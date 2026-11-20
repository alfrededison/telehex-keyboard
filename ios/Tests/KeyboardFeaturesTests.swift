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
        XCTAssertEqual(KeyboardProfile.presets.map(\.name),["Mặc định","Truyền thống","Có hàng số","Tổ ong có hàng số","Tổ ong 8 cột","Tổ ong 8 cột có hàng số"])
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
    func testTraditionalBottomRowHasThreeKeysEachSideOfSpace() {
        let qwerty = KeyboardProfile.presets[1]
        XCTAssertEqual(Array(qwerty.letters[28...]),["#symbols","#emoji","#settings"," ","","","",",",".","\n"])
        XCTAssertEqual(qwerty.swipe[qwerty.letters.firstIndex(of: ",")!],"?")
        // Profile riêng lưu đến 0.8.3 (123 ☺ ⚙ , bên trái phím cách) vẫn hợp lệ, không làm hỏng cả cấu hình.
        var old = qwerty; old.id = "mine"; old.name = "Của tôi"
        old.letters[31...35] = [","," ","","",""]
        XCTAssertTrue(old.valid())
        var split = qwerty; split.id = "mine"; split.name = "Của tôi"
        split.letters.swapAt(32,36) // phím cách bị tách đôi
        XCTAssertFalse(split.valid())
    }
    func testNumberRowLayout() {
        let num = KeyboardProfile.presets[2], qwerty = KeyboardProfile.presets[1]
        XCTAssertEqual(num.id,"builtin.qwertyNum"); XCTAssertEqual(num.spec,.qwertyNum); XCTAssertEqual(GridSpec.qwertyNum.count,48)
        XCTAssertTrue(num.valid())
        XCTAssertEqual(Array(num.letters[0..<10]).joined(),"1234567890")
        XCTAssertEqual(Array(num.letters[10...]),qwerty.letters)
        XCTAssertEqual(GridSpec.qwertyNum.left(30),1.5)
        XCTAssertEqual(Array(num.swipe[0..<10]).joined(),"!@#$%^&*()")
        XCTAssertEqual(Array(num.swipe[10..<20]).joined(),"+=~|[]<>{}")
        XCTAssertEqual(num.symbols.map(\.count),[48,48])
        XCTAssertEqual(Array(num.symbols[0][0..<10]).joined(),"1234567890")
        XCTAssertEqual(num.symbols[0][num.letters.firstIndex(of: "#shift")!],"#page")
        XCTAssertEqual(num.symbols[0][num.letters.firstIndex(of: "#settings")!],"")
        // Mọi ô chữ/số của hai trang đều có ký tự, không trùng nhau; reconcile không đổi gì.
        for page in num.symbols { for (i,code) in num.letters.enumerated() where KeyboardProfile.isCharCell(code) { XCTAssertFalse(page[i].isEmpty, "ô \(i)") } }
        let values = num.symbols.flatMap { $0 }.filter { !$0.isEmpty && $0 != "#page" }
        XCTAssertEqual(values.count,Set(values).count)
        var reconciled = num; reconciled.reconcile(); XCTAssertEqual(reconciled,num)
    }
    func testEightColumnHoneycombLayout() {
        let hex8 = KeyboardProfile.presets[4], hex = KeyboardProfile.presets[0]
        XCTAssertEqual(hex8.id,"builtin.hex8"); XCTAssertEqual(hex8.spec,.hex8); XCTAssertEqual(GridSpec.hex8.count,40)
        XCTAssertTrue(hex8.valid())
        // Hàng Q, W, A giữ đúng ô của Mặc định; hàng Z có ⇧ ở đầu; ⌫ sau L
        for row in 0..<3 { for column in 0..<7 {
            let code = hex.letters[row * 7 + column]
            if KeyboardProfile.isLetter(code) { XCTAssertEqual(hex8.letters[row * 8 + column], code) }
        } }
        XCTAssertEqual(Array(hex8.letters[24..<32]),["#shift","z","x","c","v","b","n","m"])
        XCTAssertEqual(hex8.letters[23],"#backspace")
        // Lục giác giãn dọc: lưới cao bằng tổ ong 7 cột ở cùng bề ngang
        XCTAssertEqual(GridSpec.hex8.hexHeight / GridSpec.hex8.span,GridSpec.hex7.hexHeight / GridSpec.hex7.span,accuracy: 1e-6)
        XCTAssertEqual(hex8.swipe[hex8.letters.firstIndex(of: "'")!],"\"")
        for code in ["?","!","'"] { XCTAssertTrue(KeyboardProfile.swipeUsable(code) && KeyboardProfile.symbolUsable(code)) }
        XCTAssertEqual(hex8.symbols[0][hex8.letters.firstIndex(of: "#settings")!],"#page")
        // Trang 1 đã có `?` ở ô M: ô `?` mang `…` để không hiện `?` hai lần; trang 2 để trống, hiện `?` của lớp chữ
        XCTAssertEqual(hex8.symbols[0][hex8.letters.firstIndex(of: "?")!],"…")
        XCTAssertEqual(hex8.symbols[1][hex8.letters.firstIndex(of: "?")!],"")
        for page in hex8.symbols { for (i,code) in hex8.letters.enumerated() where KeyboardProfile.isCharCell(code) { XCTAssertFalse(page[i].isEmpty, "ô \(i)") } }
        let values = hex8.symbols.flatMap { $0 }.filter { !$0.isEmpty && $0 != "#page" }
        XCTAssertEqual(values.count,Set(values).count)
        var reconciled = hex8; reconciled.reconcile(); XCTAssertEqual(reconciled,hex8)
    }
    func testEightColumnNumberRowLayout() {
        let num = KeyboardProfile.presets[5], hex8 = KeyboardProfile.presets[4]
        XCTAssertEqual(num.id,"builtin.hex8Num"); XCTAssertEqual(num.spec,.hex8Num); XCTAssertEqual(GridSpec.hex8Num.count,50)
        XCTAssertTrue(num.valid())
        XCTAssertEqual(Array(num.letters[0..<10]).joined(),"1234567890")
        XCTAssertEqual(Array(num.letters[10...]),hex8.letters)
        XCTAssertEqual(GridSpec.hex8Num.left(9) + GridSpec.hex8Num.width(9),8.5,accuracy: 1e-6)
        // Hàng số lục giác đều, hàng chữ giãn: lưới cao bằng tổ ong 7 cột có hàng số ở cùng bề ngang
        XCTAssertEqual(GridSpec.hex8Num.stretches[0],1)
        XCTAssertEqual(GridSpec.hex8Num.hexHeight / GridSpec.hex8Num.span,GridSpec.hex7Num.hexHeight / GridSpec.hex7Num.span,accuracy: 1e-6)
        XCTAssertEqual(Array(num.swipe[0..<10]),Array(repeating: "", count: 10))
        XCTAssertEqual(num.swipe[num.letters.firstIndex(of: "q")!],"+"); XCTAssertEqual(num.swipe[num.letters.firstIndex(of: "p")!],"}")
        XCTAssertEqual(num.swipe[num.letters.firstIndex(of: "z")!],"@")
        XCTAssertEqual(num.symbols[0][num.letters.firstIndex(of: "#settings")!],"#page")
        for page in num.symbols { for (i,code) in num.letters.enumerated() where KeyboardProfile.isCharCell(code) { XCTAssertFalse(page[i].isEmpty, "ô \(i)") } }
        let values = num.symbols.flatMap { $0 }.filter { !$0.isEmpty && $0 != "#page" }
        XCTAssertEqual(values.count,Set(values).count)
        for code in ["'","!","?"] { XCTAssertFalse(num.symbols[0].contains(code)) } // ô dấu câu trống hiện dấu đó, không trùng
        var reconciled = num; reconciled.reconcile(); XCTAssertEqual(reconciled,num)
    }
    func testHexNumberRowLayout() {
        let hexNum = KeyboardProfile.presets[3], hex = KeyboardProfile.presets[0], qwertyNum = KeyboardProfile.presets[2]
        XCTAssertEqual(hexNum.id,"builtin.hexNum"); XCTAssertEqual(hexNum.spec,.hex7Num); XCTAssertEqual(GridSpec.hex7Num.count,45)
        XCTAssertTrue(hexNum.valid())
        XCTAssertEqual(Array(hexNum.letters[0..<10]).joined(),"1234567890")
        XCTAssertEqual(Array(hexNum.letters[10...]),hex.letters)
        // 10 lục giác nhỏ vừa bề ngang 7,5 ô; hàng chữ giữ toạ độ của lưới tổ ong
        XCTAssertEqual(GridSpec.hex7Num.left(9) + GridSpec.hex7Num.width(9),7.5,accuracy: 1e-6)
        XCTAssertEqual(GridSpec.hex7Num.left(10),GridSpec.hex7.left(0))
        XCTAssertEqual(GridSpec.hex7.hexHeight,8 / HexShape.sqrt3,accuracy: 1e-6)
        XCTAssertEqual(GridSpec.hex7Num.hexHeight,GridSpec.hex7.hexHeight + 1.5 / HexShape.sqrt3,accuracy: 1e-6)
        XCTAssertEqual(Array(hexNum.swipe[0..<10]),Array(repeating: "", count: 10))
        XCTAssertEqual(hexNum.swipe[hexNum.letters.firstIndex(of: "q")!],"+")
        XCTAssertEqual(hexNum.symbols.map(\.count),[45,45])
        XCTAssertEqual(Array(hexNum.symbols[0][0..<10]).joined(),"1234567890")
        XCTAssertEqual(hexNum.symbols[0][hexNum.letters.firstIndex(of: "#settings")!],"#page")
        for page in hexNum.symbols { for (i,code) in hexNum.letters.enumerated() where KeyboardProfile.isCharCell(code) { XCTAssertFalse(page[i].isEmpty, "ô \(i)") } }
        let values = hexNum.symbols.flatMap { $0 }.filter { !$0.isEmpty && $0 != "#page" }
        XCTAssertEqual(values.count,Set(values).count)
        XCTAssertEqual(Set(values),Set(qwertyNum.symbols.flatMap { $0 }.filter { !$0.isEmpty && $0 != "#page" }))
        var reconciled = hexNum; reconciled.reconcile(); XCTAssertEqual(reconciled,hexNum)
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
