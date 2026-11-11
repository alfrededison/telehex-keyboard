import UIKit

/// Cài đặt chính của TeleHex nằm trong app: mỗi lần đổi, phần vừa đổi được gửi sang bàn phím qua App Group (bàn phím áp
/// khi mở ra). Gói ngôn ngữ nạp vào App Group; dữ liệu học chỉ bàn phím giữ nên app chỉ gửi lệnh xoá.
final class AppSettings {
    static let shared = AppSettings()
    let preferences: KeyboardPreferences
    let store: KeyboardDataStore
    let files: KeyboardFiles
    private let shared: () -> UserDefaults?
    /// Có App Group (bản đã ký): cài đặt tới được bàn phím.
    var reachesKeyboard: Bool { shared() != nil }

    init(preferences: KeyboardPreferences = KeyboardPreferences(), store: KeyboardDataStore = KeyboardDataStore(),
         shared: @escaping () -> UserDefaults? = { SharedSettings.store }) {
        self.preferences = preferences; self.store = store; self.shared = shared
        files = KeyboardFiles(store: store, preferences: preferences)
        preferences.onChange = { old, new in
            SharedSettings.publish(new, parts: SharedSettings.parts(changedFrom: old, to: new), in: shared())
        }
        files.onReload = { [weak self] _ in self?.send(.reloadPacks) }
    }
    func send(_ command: SharedSettings.Command) { SharedSettings.send(command, in: shared()) }
    /// Bàn phím (có Truy cập đầy đủ) đổi profile sau lần app sửa, gồm cả profile tạo khi kéo đổi chỗ phím: app theo.
    func adoptKeyboardProfiles() {
        guard let reported = SharedSettings.newerKeyboardProfiles(in: shared()) else { return }
        var next = preferences.configuration
        next.profiles = reported.profiles; next.activeProfile = reported.activeProfile
        next.normalize()
        if next.valid() && next != preferences.configuration { preferences.configuration = next }
    }
    func packLoaded(english: Bool) -> Bool { files.packStore.map(english ? "en.ngram" : "vi.ngram") != nil }
    func removePack(english: Bool) { files.packStore.remove(english ? "en.ngram" : "vi.ngram"); send(.reloadPacks) }
}
