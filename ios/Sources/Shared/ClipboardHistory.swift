import UIKit

/// Lịch sử clipboard của bàn phím, như Android: mục thường tự xoá sau 1 giờ, mục ghim giữ lâu, tối đa 30 mục thường. Khác
/// Android, chữ chép chỉ được ghi khi mở bảng clipboard (iOS hỏi quyền dán mỗi lần đọc).
/// iOS chỉ cho bàn phím đọc clipboard khi có Truy cập đầy đủ, nên chỉ chạy khi có quyền và người dùng bật trong app.
/// Lưu trong sandbox bàn phím, không vào bản sao lưu; tắt tuỳ chọn là xoá hết.
final class ClipboardHistory {
    struct Item: Codable, Equatable { var text: String; var date: Date; var pinned = false }
    static let lifetime: TimeInterval = 3600, limit = 30, maxLength = 2000
    private(set) var items: [Item] = []
    private let store: KeyboardDataStore
    private let now: () -> Date
    private var lastChange: Int?

    init(store: KeyboardDataStore, now: @escaping () -> Date = Date.init) {
        self.store = store; self.now = now
        if let data = store.read("clipboard.json"), let saved = try? JSONDecoder().decode([Item].self, from: data) { items = saved }
        prune()
    }
    /// Ghi chữ đang chép khi clipboard đổi so với lần đọc trước. Chỉ đọc nội dung khi clipboard có chữ. Mỗi lần đọc chữ chép
    /// từ app khác, iOS hỏi "TeleHex muốn dán…" (trừ khi người dùng đặt Dán từ ứng dụng khác = Cho phép), nên chỉ gọi khi
    /// người dùng mở bảng clipboard, không tự đọc mỗi lần bàn phím hiện lên.
    func capture(from pasteboard: UIPasteboard = .general) {
        guard pasteboard.changeCount != lastChange else { return }
        lastChange = pasteboard.changeCount
        guard pasteboard.hasStrings, let text = pasteboard.string else { return }
        add(text)
    }
    func add(_ text: String) {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, text.count <= Self.maxLength else { return }
        let pinned = items.first { $0.text == text }?.pinned ?? false
        items.removeAll { $0.text == text }
        items.insert(Item(text: text, date: now(), pinned: pinned), at: 0)
        var regular = 0
        items = items.filter { $0.pinned || { regular += 1; return regular <= Self.limit }() }
        save()
    }
    func togglePin(_ text: String) {
        guard let index = items.firstIndex(where: { $0.text == text }) else { return }
        items[index].pinned.toggle(); items[index].date = now(); save()
    }
    func clearUnpinned() { items.removeAll { !$0.pinned }; save() }
    func clearAll() { items = []; store.remove("clipboard.json") }
    /// Bỏ mục thường quá 1 giờ; gọi khi bàn phím hiện lên, không đợi lần chép kế tiếp.
    func prune() {
        let before = items.count
        items.removeAll { !$0.pinned && now().timeIntervalSince($0.date) > Self.lifetime }
        if items.count != before { save() }
    }
    private func save() {
        if let data = try? JSONEncoder().encode(items) { try? store.save(data, name: "clipboard.json") }
    }
}
