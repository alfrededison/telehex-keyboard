import Foundation

struct KeyboardBackup: Codable {
    var format = "telehex-ios-1"
    var configuration: KeyboardConfiguration
    var files: [String: Data]
}

final class KeyboardDataStore {
    let root: URL
    private let io = DispatchQueue(label: "com.telehex.keyboard.storage")
    private var generation = 0
    private var pendingPersonal: String?
    /// Khớp `KeyboardLanguagePack.MAX_BYTES`. Bàn phím ánh xạ gói từ tệp (`map`) nên gói lớn không chiếm RAM như dữ liệu thường.
    static let packLimit = 32 * 1024 * 1024
    /// Bản sao lưu bàn phím đọc/ghi: không kèm gói ngôn ngữ, giữ nhỏ cho giới hạn bộ nhớ của bàn phím.
    static let keyboardBackupLimit = 24 * 1024 * 1024
    /// Bản sao lưu cài đặt của app: chứa được hai gói lớn nhất (base64 làm dữ liệu to thêm 1/3).
    static let appBackupLimit = 96 * 1024 * 1024
    init(root: URL? = nil) {
        self.root = root ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("TeleHex", isDirectory: true)
    }
    private func write(_ data: Data, name: String, folder: URL? = nil) throws {
        let directory = folder ?? root
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: [.protectionKey: FileProtectionType.completeUntilFirstUserAuthentication])
        try data.write(to: directory.appendingPathComponent(name), options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
    func read(_ name: String) -> Data? { io.sync { try? Data(contentsOf: root.appendingPathComponent(name)) } }
    /// Ánh xạ tệp vào bộ nhớ thay vì đọc hết. Ghi tệp luôn thay nguyên tử (tệp mới), nên vùng đã ánh xạ không bị đổi giữa chừng.
    func map(_ name: String) -> Data? { io.sync { try? Data(contentsOf: root.appendingPathComponent(name), options: .alwaysMapped) } }
    func save(_ data: Data, name: String) throws { try io.sync { try write(data, name: name) } }
    func remove(_ name: String) { io.sync { try? FileManager.default.removeItem(at: root.appendingPathComponent(name)) } }
    func schedulePersonal(_ text: String) {
        let ticket = io.sync { generation += 1; pendingPersonal = text; return generation }
        io.asyncAfter(deadline: .now() + 0.4) { [weak self] in
            guard let self, ticket == self.generation else { return }
            try? self.write(Data(text.utf8), name: "personal.txt")
            self.pendingPersonal = nil
        }
    }
    func clearPersonal() { io.sync { generation += 1; pendingPersonal = nil; try? FileManager.default.removeItem(at: root.appendingPathComponent("personal.txt")) } }
    /// Bản sao lưu JSON: cấu hình, gói ngôn ngữ (`packs`: tên tệp → dữ liệu; nil thì lấy trong kho này, rỗng thì không kèm)
    /// và, khi `personal`, từ đã học.
    func backup(configuration: KeyboardConfiguration, packs: [String: Data]? = nil, personal: Bool = true, limit: Int = keyboardBackupLimit) throws -> URL {
        var files: [String: Data] = io.sync {
            var result: [String: Data] = [:]
            if personal {
                if let data = try? Data(contentsOf: root.appendingPathComponent("personal.txt")) { result["personal.txt"] = data }
                if let pendingPersonal { result["personal.txt"] = Data(pendingPersonal.utf8) }
            }
            return result
        }
        for name in ["vi.ngram", "en.ngram"] { if let data = packs.map({ $0[name] }) ?? read(name) { files[name] = data } }
        let value = KeyboardBackup(configuration: configuration, files: files)
        let data = try JSONEncoder().encode(value)
        guard data.count <= limit else { throw CocoaError(.fileWriteOutOfSpace) }
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let target = directory.appendingPathComponent("TeleHex-backup.json")
        try data.write(to: target, options: .atomic)
        return target
    }
    static func decodeBackup(_ data: Data, limit: Int = keyboardBackupLimit) throws -> KeyboardBackup {
        guard data.count <= limit else { throw CocoaError(.fileReadTooLarge) }
        var backup = try JSONDecoder().decode(KeyboardBackup.self, from: data)
        // Bản sao lưu cũ có lịch sử clipboard: chức năng đã bỏ nên không khôi phục mục đó.
        backup.files.removeValue(forKey: "clipboard.json")
        backup.configuration.normalize()
        let limits = ["personal.txt": 2_000_000, "vi.ngram": packLimit, "en.ngram": packLimit]
        guard backup.format == "telehex-ios-1", backup.configuration.valid(), backup.files.allSatisfy({ name, data in
            limits[name].map { data.count <= $0 } == true
        }) else { throw CocoaError(.fileReadCorruptFile) }
        return backup
    }
    /// Caller validates language packs before committing. File replacement is all-or-nothing.
    func restore(_ backup: KeyboardBackup) throws {
        try io.sync {
            let manager = FileManager.default
            let stage = root.deletingLastPathComponent().appendingPathComponent("restore-" + UUID().uuidString)
            let old = root.deletingLastPathComponent().appendingPathComponent("old-" + UUID().uuidString)
            try manager.createDirectory(at: stage, withIntermediateDirectories: true)
            var committed = false
            defer { try? manager.removeItem(at: stage); if committed { try? manager.removeItem(at: old) } }
            for (name, data) in backup.files { try write(data, name: name, folder: stage) }
            let existed = manager.fileExists(atPath: root.path)
            if existed { try manager.moveItem(at: root, to: old) }
            do { try manager.moveItem(at: stage, to: root) }
            catch { if existed { try? manager.moveItem(at: old, to: root) }; throw error }
            generation += 1; pendingPersonal = nil; committed = true
        }
    }
}
