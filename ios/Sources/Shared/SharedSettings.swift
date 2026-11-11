import Foundation

/// Đồng bộ một chiều app → bàn phím qua App Group, không cần Truy cập đầy đủ: app là nơi chỉnh cài đặt và ghi cấu hình,
/// gói ngôn ngữ, lệnh xoá vào kho chung; bàn phím đọc khi mở ra. Cấu hình chia ba phần, mỗi phần có mốc thời gian riêng, để
/// thao tác nhanh trên bàn phím (đổi profile, ẩn danh, kéo đổi chỗ phím) không bị lần chỉnh khác trong app đè mất. Dữ liệu
/// học và độ lệch chạm chỉ bàn phím giữ. Khi có Truy cập đầy đủ, bàn phím báo lại profile để app hiện đúng.
/// Bản không ký (Simulator, CI) không có App Group: `store` là nil và mọi thao tác bỏ qua.
enum SharedSettings {
    static let group = "group.com.telehex.keyboard.shared"
    static var containerURL: URL? { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group) }
    static var store: UserDefaults? { containerURL == nil ? nil : UserDefaults(suiteName: group) }
    /// Gói ngôn ngữ dùng chung: app nạp, bàn phím đọc.
    static var packStore: KeyboardDataStore? { containerURL.map { KeyboardDataStore(root: $0.appendingPathComponent("TeleHex", isDirectory: true)) } }

    /// Phần của cấu hình có mốc sửa riêng.
    enum Part: String, CaseIterable { case general, profiles, incognito }
    /// Lệnh app gửi cho bàn phím: làm một lần, ở lần mở bàn phím tiếp theo.
    enum Command: String, CaseIterable { case clearPersonal, resetOffsets, reloadPacks }

    static let configKey = "telehex.shared.config.v1"
    static func stampKey(_ part: Part) -> String { "telehex.shared.stamp.\(part.rawValue)" }
    static func commandKey(_ command: Command) -> String { "telehex.shared.command.\(command.rawValue)" }

    /// Phần "chung": mọi thứ trừ profile, ẩn danh và độ lệch chạm (bàn phím tự học).
    static func general(_ configuration: KeyboardConfiguration) -> KeyboardConfiguration {
        var copy = configuration
        copy.profiles = []; copy.activeProfile = ""; copy.incognito = false; copy.offsets = [:]
        return copy
    }
    static func parts(changedFrom old: KeyboardConfiguration, to new: KeyboardConfiguration) -> Set<Part> {
        var result = Set<Part>()
        if old.profiles != new.profiles || old.activeProfile != new.activeProfile { result.insert(.profiles) }
        if old.incognito != new.incognito { result.insert(.incognito) }
        if general(old) != general(new) { result.insert(.general) }
        return result
    }

    /// App: ghi cấu hình (không kèm độ lệch chạm) và đánh dấu các phần vừa đổi.
    static func publish(_ configuration: KeyboardConfiguration, parts: Set<Part>, in store: UserDefaults?, now: Date = Date()) {
        guard let store, !parts.isEmpty else { return }
        var copy = configuration; copy.offsets = [:]
        guard let data = try? JSONEncoder().encode(copy) else { return }
        store.set(data, forKey: configKey)
        for part in parts { store.set(now.timeIntervalSince1970, forKey: stampKey(part)) }
    }
    static func send(_ command: Command, in store: UserDefaults?, now: Date = Date()) {
        store?.set(now.timeIntervalSince1970, forKey: commandKey(command))
    }
    /// Cấu hình app đã gửi, đã kiểm tra hợp lệ.
    static func configuration(in store: UserDefaults?) -> KeyboardConfiguration? {
        guard let data = store?.data(forKey: configKey), var value = try? JSONDecoder().decode(KeyboardConfiguration.self, from: data) else { return nil }
        value.normalize()
        return value.valid() ? value : nil
    }

    struct KeyboardProfiles: Codable, Equatable { var profiles: [KeyboardProfile]; var activeProfile: String }
    static let keyboardProfilesKey = "telehex.shared.keyboardProfiles", keyboardProfilesStampKey = "telehex.shared.keyboardProfilesStamp"

    /// Bàn phím (có Truy cập đầy đủ, nên ghi được group): báo danh sách profile và profile đang dùng, gồm cả profile tạo khi
    /// kéo đổi chỗ phím, để app hiện đúng. Chỉ ghi khi đổi.
    static func reportKeyboardProfiles(_ configuration: KeyboardConfiguration, in store: UserDefaults?, now: Date = Date()) {
        guard let store, let data = try? JSONEncoder().encode(KeyboardProfiles(profiles: configuration.profiles, activeProfile: configuration.activeProfile)),
              store.data(forKey: keyboardProfilesKey) != data else { return }
        store.set(data, forKey: keyboardProfilesKey); store.set(now.timeIntervalSince1970, forKey: keyboardProfilesStampKey)
    }
    /// App: profile bàn phím báo lại, nếu bàn phím đổi sau lần app sửa profile.
    static func newerKeyboardProfiles(in store: UserDefaults?) -> KeyboardProfiles? {
        guard let store, store.double(forKey: keyboardProfilesStampKey) > store.double(forKey: stampKey(.profiles)),
              let data = store.data(forKey: keyboardProfilesKey) else { return nil }
        return try? JSONDecoder().decode(KeyboardProfiles.self, from: data)
    }
}
