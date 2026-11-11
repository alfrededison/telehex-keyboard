import XCTest
import TeleHexCore
@testable import TeleHex

final class KeyboardFeaturesTests: XCTestCase {
    private func preferences() -> KeyboardPreferences {
        KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
    }
    private func store() -> KeyboardDataStore {
        KeyboardDataStore(root: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString))
    }
    func testPresetValidationAndEditingNeverMutatesBuiltIn() {
        let prefs = preferences()
        XCTAssertTrue(prefs.configuration.valid())
        XCTAssertTrue(KeyboardProfile.presets.allSatisfy { $0.valid() })
        let original = prefs.profile
        var edited = original
        edited.letters.swapAt(0,1); edited.reconcile()
        XCTAssertTrue(prefs.saveProfile(edited))
        XCTAssertFalse(prefs.profile.builtIn)
        XCTAssertEqual(KeyboardProfile.presets[0],original)
        let restored = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        restored.configuration = prefs.configuration
        XCTAssertTrue(restored.configuration.valid())
        var corrupt = prefs.configuration
        corrupt.activeProfile = "missing"
        XCTAssertFalse(corrupt.valid())
        corrupt = prefs.configuration
        corrupt.profiles[0].name = original.name
        XCTAssertFalse(corrupt.valid())
    }
    func testBackupRoundtripIncludesPendingLearningAndReplacesAtomically() throws {
        let source = store(), target = store()
        defer { try? FileManager.default.removeItem(at: source.root); try? FileManager.default.removeItem(at: target.root) }
        var config = KeyboardConfiguration()
        config.snippets = ["dc":"địa chỉ"]
        source.schedulePersonal("TELEHEX-PERSONAL-1\nu\txin\t2\n")
        let file = try source.backup(configuration: config)
        defer { try? FileManager.default.removeItem(at: file.deletingLastPathComponent()) }
        let backup = try KeyboardDataStore.decodeBackup(Data(contentsOf: file))
        XCTAssertNotNil(backup.files["personal.txt"])
        try target.save(Data("old".utf8),name: "vi.ngram")
        try target.restore(backup)
        XCTAssertNil(target.read("vi.ngram"))
        XCTAssertEqual(backup.configuration.snippets["dc"],"địa chỉ")
        var invalid = backup
        invalid.files["../escape"] = Data()
        XCTAssertThrowsError(try KeyboardDataStore.decodeBackup(JSONEncoder().encode(invalid)))
        // Bản sao lưu cũ còn lịch sử clipboard vẫn khôi phục được, nhưng không mang lịch sử đó vào.
        var legacy = backup
        legacy.files["clipboard.json"] = Data("[]".utf8)
        legacy.configuration.activeProfile = "builtin.ergonomic"
        let decoded = try KeyboardDataStore.decodeBackup(JSONEncoder().encode(legacy))
        XCTAssertNil(decoded.files["clipboard.json"])
        XCTAssertEqual(decoded.configuration.activeProfile,"builtin.default")
    }
    func testBuiltInLayoutsMatchAndroid080() {
        XCTAssertEqual(KeyboardProfile.presets.map(\.name),["Mặc định","Truyền thống"])
        XCTAssertTrue(KeyboardProfile.presets.allSatisfy { $0.valid() })
        let hex = KeyboardProfile.presets[0], qwerty = KeyboardProfile.presets[1]
        XCTAssertEqual(hex.spec,.hex7); XCTAssertEqual(qwerty.spec,.qwerty)
        // Hai hàng trên đọc zigzag ⚙ Q W E R T G Y H U I O P 123.
        let zigzag = (0..<7).flatMap { [hex.letters[7 + $0], hex.letters[$0]] }
        XCTAssertEqual(zigzag.joined(separator: " "),"#settings q w e r t g y h u i o p #symbols")
        XCTAssertEqual(Array(hex.letters[14..<21]),["a","s","d","f","j","k","l"])
        XCTAssertEqual(Array(qwerty.letters[19..<28]),["#shift","z","x","c","v","b","n","m","#backspace"])
        // Vuốt xuống Q…P ra 1…0 trên cả hai bố cục.
        for profile in [hex,qwerty] {
            XCTAssertEqual("qwertyuiop".map { profile.swipe[profile.letters.firstIndex(of: String($0))!] }.joined(),"1234567890")
        }
        // Phím 1/2 nằm ở ô ⚙ (tổ ong) và ô ⇧ (truyền thống); trang 2 có < > ở ô , .
        XCTAssertEqual(hex.symbols[0][hex.letters.firstIndex(of: "#settings")!],"#page")
        XCTAssertEqual(qwerty.symbols[0][qwerty.letters.firstIndex(of: "#shift")!],"#page")
        for profile in [hex,qwerty] {
            XCTAssertEqual(profile.symbols[1][profile.letters.firstIndex(of: ",")!],"<")
            XCTAssertEqual(profile.symbols[1][profile.letters.firstIndex(of: ".")!],">")
        }
        XCTAssertEqual(GridSpec.qwerty.count,38); XCTAssertEqual(GridSpec.qwerty.left(20),1.5)
    }
    func testReconcileMovesSymbolsOffFunctionKeysToLetterCellsFirst() {
        var profile = KeyboardProfile.presets[0]
        profile.id = "mine"; profile.name = "Của tôi"
        let settings = profile.letters.firstIndex(of: "#settings")!, emoji = profile.letters.firstIndex(of: "#emoji")!
        profile.letters.swapAt(settings,emoji); profile.reconcile()
        XCTAssertTrue(profile.valid())
        // ☺ không giữ được giá trị lớp 123. Ô chữ đều đã có ký hiệu nên phím 1/2 sang ô trống còn dùng được: ô ⚙ mới.
        XCTAssertEqual(profile.symbols[0][settings],"")
        XCTAssertEqual(profile.symbols[0][emoji],"#page")
        XCTAssertEqual(profile.symbols[0].filter { $0 == "#page" }.count,1)
    }
    func testRemovedPresetFallsBackToDefaultInsteadOfResettingSettings() throws {
        let defaults = try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))
        var stored = KeyboardConfiguration()
        stored.snippets = ["dc":"địa chỉ"]; stored.activeProfile = "builtin.balanced"
        defaults.set(try JSONEncoder().encode(stored),forKey: "telehex.configuration.v1")
        let prefs = KeyboardPreferences(defaults: defaults)
        XCTAssertEqual(prefs.configuration.activeProfile,"builtin.default")
        XCTAssertEqual(prefs.configuration.snippets["dc"],"địa chỉ")
    }
    func testClearingLearningCancelsPendingWriteAndPreservesPacks() throws {
        let storage = store()
        defer { try? FileManager.default.removeItem(at: storage.root) }
        try storage.save(Data([1,2,3]),name: "vi.ngram")
        storage.schedulePersonal("TELEHEX-PERSONAL-1\nu\txin\t1")
        storage.clearPersonal()
        let done = expectation(description: "debounce drained")
        DispatchQueue.main.asyncAfter(deadline: .now()+0.6) { done.fulfill() }
        wait(for: [done],timeout: 2)
        XCTAssertNil(storage.read("personal.txt"))
        XCTAssertEqual(storage.read("vi.ngram"),Data([1,2,3]))
    }
    /// Gói THNG nhỏ nhất hợp lệ (một từ, không bigram/trigram), thêm `padding` byte 0 ở cuối để thành gói lớn.
    private func minimalPack(padding: Int = 0) -> Data {
        // "THNG", phiên bản 1, N = 1, từ "a" (dài 1), q unigram, 0 bigram, 0 trigram.
        let header: [UInt8] = [0x54, 0x48, 0x4E, 0x47, 1, 1, 0, 0, 0, 1, 0x61, 8, 0, 0, 0, 0, 0, 0, 0, 0]
        var data = Data(header)
        data.append(Data(count: padding))
        return data
    }
    func testLanguagePacksAreMappedFromDiskUpToTheLimit() throws {
        let storage = store()
        defer { try? FileManager.default.removeItem(at: storage.root) }
        // Lớn hơn trần 4 MiB cũ: ánh xạ từ tệp và nạp được.
        try storage.save(minimalPack(padding: 20 * 1024 * 1024), name: "vi.ngram")
        let mapped = try XCTUnwrap(storage.map("vi.ngram"))
        // Cầu nối sang NSData (kiểu Kotlin nhận) dùng chung vùng ánh xạ, không chép gói ra bộ nhớ.
        XCTAssertEqual((mapped as NSData).bytes, mapped.withUnsafeBytes { $0.baseAddress })
        XCTAssertTrue(VocabularyLoader.languagePack(mapped, english: false).valid)
        XCTAssertTrue(VocabularyLoader.languagePack(minimalPack(), english: true).valid)
        // Quá trần, hoặc từ vựng trỏ ra ngoài tệp, thì từ chối mà không đọc lố vùng ánh xạ.
        XCTAssertFalse(VocabularyLoader.languagePack(minimalPack(padding: KeyboardDataStore.packLimit), english: false).valid)
        var overrun = minimalPack()
        overrun[9] = 200
        XCTAssertFalse(VocabularyLoader.languagePack(overrun, english: false).valid)
    }
    func testEmojiVietnameseSearchGenderAndTone() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "emoji",withExtension: "tsv"))
        let catalog = EmojiCatalog(text: try String(contentsOf: url,encoding: .utf8))
        XCTAssertGreaterThan(catalog.entries.count,1000)
        XCTAssertFalse(catalog.search("cuoi").isEmpty)
        XCTAssertEqual(EmojiCatalog.normalize("ĐƯỜNG"),"duong")
        XCTAssertEqual(catalog.variant("🧑‍⚕️",gender: 1,tone: 3),"👩🏽‍⚕️")
        XCTAssertEqual(catalog.variant("👨‍⚕️",gender: 1,tone: 0),"👨‍⚕️")
        XCTAssertEqual(catalog.variant("😀",gender: 1,tone: 3),"😀")
    }

}
