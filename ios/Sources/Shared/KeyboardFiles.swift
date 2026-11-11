import UIKit
import UniformTypeIdentifiers
import TeleHexCore

/// Nhập/xuất tệp qua Files. Bàn phím: sao lưu/khôi phục đầy đủ (gồm từ đã học, chỉ bàn phím giữ). App: nạp gói ngôn ngữ
/// vào App Group, sao lưu/khôi phục cài đặt (không có từ đã học).
final class KeyboardFiles: NSObject, UIDocumentPickerDelegate {
    weak var presenter: UIViewController?
    let store: KeyboardDataStore
    let preferences: KeyboardPreferences
    /// Nơi đặt gói ngôn ngữ: App Group nếu có (app ghi, bàn phím đọc), không thì kho riêng.
    var group: () -> KeyboardDataStore? = { SharedSettings.packStore }
    var packStore: KeyboardDataStore { group() ?? store }
    /// Gói đang dùng, để sao lưu đúng gói bàn phím thật sự dùng.
    private var activePacks: [String: Data] {
        var result: [String: Data] = [:]
        for name in ["vi.ngram", "en.ngram"] { result[name] = VocabularyLoader.pack(name, local: store, group: group()) }
        return result
    }
    var onReload: ((Bool) -> Void)?
    var onMessage: ((String) -> Void)?
    private var operation = ""
    private var processing = false
    private var exported: URL?
    init(store: KeyboardDataStore, preferences: KeyboardPreferences) { self.store = store; self.preferences = preferences }
    func run(_ command: String) {
        guard !processing, let presenter, presenter.presentedViewController == nil else { return }
        operation = command
        if command == "export-backup" || command == "export-settings" {
            do {
                // Bàn phím ít RAM: bản sao lưu đầy đủ (từ ⚙ bàn phím) không kèm gói; gói nằm trong app và bản sao lưu cài đặt.
                let keyboard = command == "export-backup"
                let url = try store.backup(configuration: preferences.configuration, packs: keyboard ? [:] : activePacks, personal: keyboard,
                                           limit: keyboard ? KeyboardDataStore.keyboardBackupLimit : KeyboardDataStore.appBackupLimit); exported = url
                let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true); picker.delegate = self
                presenter.present(picker, animated: true)
            } catch { onMessage?("Không xuất được bản sao lưu") }
        } else {
            let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.data,.json], asCopy: true)
            picker.delegate = self; picker.allowsMultipleSelection = false
            presenter.present(picker, animated: true)
        }
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) { cleanup() }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        if operation.hasPrefix("export-") { cleanup(); onMessage?("Đã xuất bản sao lưu"); return }
        guard let url = urls.first else { return }
        let command = operation
        processing = true
        onMessage?("Đang kiểm tra tệp…")
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self else { return }
            defer { DispatchQueue.main.async { self.processing = false } }
            let access = url.startAccessingSecurityScopedResource()
            defer { if access { url.stopAccessingSecurityScopedResource() } }
            do {
                let restoring = command == "import-backup" || command == "import-settings"
                let limit = command == "import-settings" ? KeyboardDataStore.appBackupLimit
                    : restoring ? KeyboardDataStore.keyboardBackupLimit : KeyboardDataStore.packLimit
                let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? limit + 1
                guard size <= limit else { throw CocoaError(.fileReadTooLarge) }
                // Gói chỉ cần ánh xạ; bản sao lưu JSON thì phải đọc hết để giải mã.
                let data = try Data(contentsOf: url, options: restoring ? [] : .alwaysMapped)
                if restoring {
                    var backup = try KeyboardDataStore.decodeBackup(data, limit: limit)
                    // App không giữ từ đã học: bỏ phần đó khi khôi phục cài đặt.
                    if command == "import-settings" { backup.files.removeValue(forKey: "personal.txt") }
                    for (name, english) in [("vi.ngram",false),("en.ngram",true)] {
                        if let bytes = backup.files[name] {
                            let pack = VocabularyLoader.languagePack(bytes, english: english)
                            guard pack.valid else { throw CocoaError(.fileReadCorruptFile) }
                        }
                    }
                    if let personal = backup.files["personal.txt"] {
                        guard let text = String(data: personal, encoding: .utf8), text.isEmpty || text.components(separatedBy: "\n").first == "TELEHEX-PERSONAL-1" else { throw CocoaError(.fileReadCorruptFile) }
                    }
                    // Confirm before replacing existing profiles and learned data.
                    DispatchQueue.main.async { self.confirmRestore(backup, personal: command == "import-backup") }
                } else {
                    let english = command == "import-EN"
                    let pack = VocabularyLoader.languagePack(data, english: english)
                    guard pack.valid else { DispatchQueue.main.async { self.onMessage?(pack.error) }; return }
                    try self.packStore.save(data, name: english ? "en.ngram" : "vi.ngram")
                    DispatchQueue.main.async { self.onReload?(false); self.onMessage?("Đã nạp gói \(english ? "EN" : "VI")") }
                }
            } catch { DispatchQueue.main.async { self.onMessage?("Tệp không hợp lệ, quá lớn hoặc không đọc được; dữ liệu cũ được giữ lại") } }
        }
    }
    private func confirmRestore(_ backup: KeyboardBackup, personal: Bool) {
        guard let presenter else { return }
        // Bản sao lưu bàn phím không kèm gói: chỉ nhắc tới gói khi bản này có gói.
        let packs = backup.files.keys.contains { $0.hasSuffix(".ngram") }
        let message = personal ? "Thay cấu hình, profile\(packs ? ", gói ngôn ngữ" : "") và từ đã học hiện tại bằng bản sao lưu."
            : "Thay cấu hình, profile, gõ tắt\(packs ? " và gói ngôn ngữ" : "") hiện tại bằng bản sao lưu. Từ đã học nằm trong bàn phím, không đổi."
        let alert = UIAlertController(title: "Khôi phục bản sao lưu?", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Huỷ", style: .cancel))
        alert.addAction(UIAlertAction(title: "Khôi phục", style: .destructive) { [weak self] _ in
            guard let self else { return }
            do {
                let local = try self.restore(backup, personal: personal)
                self.onMessage?(local ? "Đã khôi phục; gói ngôn ngữ chỉ dùng trên bàn phím này tới khi nạp gói trong app" : "Đã khôi phục")
            }
            catch { self.onMessage?("Không khôi phục được; hãy giữ bản sao lưu và thử lại") }
        })
        if let presented = presenter.presentedViewController { presented.dismiss(animated: true) { presenter.present(alert,animated: true) } }
        else { presenter.present(alert, animated: true) }
    }
    /// The picker validates file contents before offering the restore confirmation. Từ đã học thay cả kho riêng (nguyên tử);
    /// gói ngôn ngữ ghi vào App Group nếu ghi được (app, hoặc bàn phím có Truy cập đầy đủ), không thì vào kho riêng.
    /// Trả true khi có gói phải lưu vào kho riêng vì không ghi được App Group (bàn phím chưa có Truy cập đầy đủ); gói đó được
    /// đánh dấu bằng mốc nạp gói hiện tại để bàn phím dùng nó cho tới khi app nạp/xoá gói lần sau.
    @discardableResult func restore(_ backup: KeyboardBackup, personal: Bool) throws -> Bool {
        if personal {
            var kept = backup.files.filter { $0.key == "personal.txt" }
            // Bản sao lưu bàn phím không kèm gói: giữ gói đang nằm trong kho riêng (và mốc của nó) qua lần thay kho.
            for name in ["vi.ngram", "en.ngram"] where backup.files[name] == nil {
                for file in [name, name + ".local"] { if let data = store.map(file) { kept[file] = data } }
            }
            try store.restore(KeyboardBackup(configuration: backup.configuration, files: kept))
        }
        var local = false
        for name in ["vi.ngram", "en.ngram"] {
            guard let data = backup.files[name] else { continue }
            if let group = group(), (try? group.save(data, name: name)) != nil {
                store.remove(name); store.remove(name + ".local")
            } else {
                try store.save(data, name: name)
                if group() != nil { try store.save(Data(String(VocabularyLoader.groupPackStamp).utf8), name: name + ".local"); local = true }
            }
        }
        preferences.configuration = backup.configuration
        onReload?(true)
        return local
    }
    private func cleanup() {
        if let exported { try? FileManager.default.removeItem(at: exported.deletingLastPathComponent()); self.exported = nil }
    }
    deinit { cleanup() }
}
