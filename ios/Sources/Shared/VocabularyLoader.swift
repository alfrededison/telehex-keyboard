import Foundation
import TeleHexCore

enum VocabularyLoader {
    typealias Completion = (KeyboardVocabulary?, EmojiCatalog?) -> Void
    typealias Load = (KeyboardDataStore, @escaping Completion) -> Void
    /// Each keyboard owns mutable model state. Only asset bytes are shared between sessions.
    private static var assets: [String: String]?
    private static let queue = DispatchQueue(label: "com.telehex.keyboard.vocabulary", qos: .userInitiated)
    /// Kiểm tra và mở gói ngay trên `data`, không chép sang mảng Kotlin; `data` ánh xạ từ tệp thì gói không nằm hết trong RAM.
    static func languagePack(_ data: Data, english: Bool) -> KeyboardLanguagePack {
        KeyboardLanguagePack.companion.load(buffer: NSDataModelBuffer(data: data), englishMode: english)
    }
    /// Mốc lần app nạp/xoá gói gần nhất (lệnh `reloadPacks`).
    static var groupPackStamp: Double { SharedSettings.store?.double(forKey: SharedSettings.commandKey(.reloadPacks)) ?? 0 }
    /// Gói ngôn ngữ đang dùng: gói app nạp vào App Group; gói khôi phục ngay trên bàn phím (không ghi được group) dùng cho tới
    /// khi app nạp hoặc xoá gói lần sau. Bản không ký (không có group) chỉ có kho riêng.
    static func pack(_ name: String, local: KeyboardDataStore, group: KeyboardDataStore? = SharedSettings.packStore, groupStamp: Double = groupPackStamp) -> Data? {
        guard let group else { return local.map(name) }
        let marker = local.read(name + ".local").flatMap { String(data: $0, encoding: .utf8) }.flatMap(Double.init)
        if marker == groupStamp, let data = local.map(name) { return data }
        return group.map(name)
    }
    static func load(store: KeyboardDataStore = KeyboardDataStore(), completion: @escaping (KeyboardVocabulary?, EmojiCatalog?) -> Void) {
        queue.async {
            if assets == nil {
                var source: [String: String] = [:]
                for (name, ext) in [("en-frequency","txt"),("vi-frequency","txt"),("vi-phrases","tsv"),("emoji","tsv")] {
                    if let url = Bundle.main.url(forResource: name, withExtension: ext), let text = try? String(contentsOf: url, encoding: .utf8) { source[name] = text }
                }
                assets = source
            }
            let vocabulary: KeyboardVocabulary?
            if let en = assets?["en-frequency"], let vi = assets?["vi-frequency"], let phrases = assets?["vi-phrases"] {
                let model = KeyboardVocabulary(englishText: en, vietnameseText: vi, phrasesText: phrases)
                if let data = store.read("personal.txt"), let text = String(data: data, encoding: .utf8) { _ = model.restorePersonal(text: text) }
                for (name, english) in [("vi.ngram",false),("en.ngram",true)] {
                    if let data = pack(name, local: store), data.count <= KeyboardDataStore.packLimit {
                        let pack = languagePack(data, english: english)
                        if pack.valid { model.setLanguagePack(pack: pack, englishMode: english) }
                    }
                }
                vocabulary = model
            } else { vocabulary = nil }
            let emoji = assets?["emoji"].map(EmojiCatalog.init)
            DispatchQueue.main.async { completion(vocabulary, emoji) }
        }
    }
}
