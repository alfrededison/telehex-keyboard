import XCTest
import TeleHexCore
@testable import TeleHex

final class KeyboardRegressionTests: XCTestCase {
    private func preferences() -> KeyboardPreferences {
        KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
    }
    private func vocabulary() -> KeyboardVocabulary {
        KeyboardVocabulary(englishText: "hello\nhelp",vietnameseText: "xin\nchào\ncũ\nmới",phrasesText: "")
    }
    private func store() -> KeyboardDataStore {
        KeyboardDataStore(root: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString))
    }
    func testSwitchingLayerFamiliesDropsSelectionAndInvalidSourceCannotChangeProfile() {
        let prefs = preferences(), view = HexKeyboardView(preferences: preferences())
        view.startLayoutEditor(0); view.editCell(0,layer: 0)
        view.startLayoutEditor(2)
        XCTAssertNil(view.selectedCell)
        view.editCell(1,layer: 2)
        XCTAssertEqual(view.selectedCell?.0,2)
        view.startLayoutEditor(0)
        XCTAssertNil(view.selectedCell)
        let original = view.preferences.profile
        view.swapCells(0,1,layer: 2,sourceLayer: 0)
        view.swapCells(0,1,layer: 9,sourceLayer: 2)
        XCTAssertEqual(view.preferences.profile,original)
        view.startPanel("profiles")
        XCTAssertNil(view.selectedCell)
        XCTAssertTrue(prefs.configuration.valid())
    }
    func testTwoSymbolPagesKeepTheirSelectionAndSwapAcrossPages() {
        let view = HexKeyboardView(preferences: preferences())
        let original = view.preferences.profile
        view.startLayoutEditor(2); view.editCell(0,layer: 2)
        view.startLayoutEditor(3)
        XCTAssertEqual(view.selectedCell?.0,2)
        view.editCell(1,layer: 3)
        XCTAssertNil(view.selectedCell)
        XCTAssertEqual(view.preferences.profile.symbols[0][0],original.symbols[1][1])
        XCTAssertEqual(view.preferences.profile.symbols[1][1],original.symbols[0][0])
        XCTAssertFalse(view.preferences.profile.builtIn)
    }
    func testRestoreAppliesPrivacySnippetsAndTelexBeforeTheLoadCompletes() throws {
        let prefs = preferences(), storage = store(), document = EditorDocument("")
        defer { try? FileManager.default.removeItem(at: storage.root) }
        var loads: [VocabularyLoader.Completion] = []
        let driver = KeyboardDriver(preferences: prefs,store: storage,document: { document },traits: { (.default,.none) },loadVocabulary: { _,completion in loads.append(completion) })
        driver.update()
        loads[0](vocabulary(),nil)
        var restored = prefs.configuration
        restored.incognito = true; restored.lAsGrave = true; restored.newToneStyle = true
        restored.activeProfile = "builtin.qwerty"
        restored.snippets = ["dc":"địa chỉ"]
        try driver.files.restore(KeyboardBackup(configuration: restored,files: [:]),personal: true)
        XCTAssertEqual(loads.count,2)
        XCTAssertEqual(driver.view.currentProfile?.id,"builtin.qwerty")
        // Configuration must apply even while dictionary loading is still pending.
        for key in "hoal dc " { driver.view.onKey?(String(key)) }
        XCTAssertEqual(document.text,"hoà địa chỉ ")
        let model = vocabulary()
        loads[1](model,nil)
        for key in "chaof " { driver.view.onKey?(String(key)) }
        XCTAssertFalse(model.personalData().contains("chào"))
        XCTAssertNil(storage.read("personal.txt"))
        restored.incognito = false; restored.lAsGrave = false; restored.newToneStyle = false
        restored.snippets = ["dc":"nhà"]
        try driver.files.restore(KeyboardBackup(configuration: restored,files: [:]),personal: true)
        for key in "hoal dc " { driver.view.onKey?(String(key)) }
        XCTAssertTrue(document.text.hasSuffix("hoal nhà "))
    }
    func testClearingRejectsPreviouslyLoadedModelRegardlessOfCompletionOrder() throws {
        let storage = store(), document = EditorDocument("")
        defer { try? FileManager.default.removeItem(at: storage.root) }
        let stale = vocabulary(); stale.learn(before: "xin ",word: "cũ")
        try storage.save(Data(stale.personalData().utf8),name: "personal.txt")
        var loads: [VocabularyLoader.Completion] = []
        let driver = KeyboardDriver(preferences: preferences(),store: storage,document: { document },traits: { (.default,.none) },loadVocabulary: { _,completion in loads.append(completion) })
        driver.update()
        driver.view.onCommand?("clear-personal")
        XCTAssertEqual(loads.count,2)
        XCTAssertNil(storage.read("personal.txt"))
        loads[0](stale,nil)
        XCTAssertNil(driver.session.vocabulary)
        let clean = vocabulary()
        loads[1](clean,nil)
        loads[0](stale,nil)
        XCTAssertTrue(driver.session.vocabulary === clean)
        for key in "mois " { driver.view.onKey?(String(key)) }
        XCTAssertFalse(driver.session.vocabulary?.personalData().contains("cũ") == true)
        let backup = try storage.backup(configuration: driver.preferences.configuration)
        defer { try? FileManager.default.removeItem(at: backup.deletingLastPathComponent()) }
        let snapshot = try KeyboardDataStore.decodeBackup(Data(contentsOf: backup))
        let personal = snapshot.files["personal.txt"].flatMap { String(data: $0,encoding: .utf8) } ?? ""
        XCTAssertFalse(personal.contains("cũ"))
    }
    func testNormalDictionaryCompletionPreservesAnActiveComposition() {
        let document = EditorDocument("")
        var completion: VocabularyLoader.Completion?
        let driver = KeyboardDriver(preferences: preferences(),document: { document },traits: { (.default,.none) },loadVocabulary: { _,callback in completion = callback })
        driver.update()
        for key in "tie" { driver.view.onKey?(String(key)) }
        completion?(vocabulary(),nil)
        for key in "engs" { driver.view.onKey?(String(key)) }
        XCTAssertEqual(document.text,"tiếng")
    }
    func testEmojiSearchAndBackKeepTheOriginalEditorAndComposition() {
        let view = HexKeyboardView(preferences: preferences())
        var host = "", saved: [String] = []
        view.onKey = { host += $0 }
        view.beginEditor(title: "Gõ tắt",fields: ["Nội dung"],values: [""],ascii: [false]) { saved = $0 }
        for key in "tie" { view.type(String(key)) }
        view.openEmoji(); view.searchEmoji()
        view.type("a"); view.inlineCancel?()
        view.returnFromEmoji()
        for key in "engs" { view.type(String(key)) }
        XCTAssertEqual(view.inlineDocuments.first?.text,"tiếng")
        view.openEmoji(); view.searchEmoji(); view.type("vui")
        view.inlineSave?(["vui"])
        view.returnFromEmoji(inserting: "😀")
        XCTAssertEqual(view.inlineDocuments.first?.text,"tiếng😀")
        XCTAssertEqual(host,"")
        view.inlineSave?(view.inlineDocuments.map(\.text))
        XCTAssertEqual(saved,["tiếng😀"])
    }
    func testFunctionKeyGesturesAndAccessibilityCannotTypeInternalTokensOrGlide() throws {
        let view = HexKeyboardView(preferences: preferences())
        view.frame = CGRect(x: 0,y: 0,width: 402,height: 300); view.layoutIfNeeded()
        var host = "", glides = 0
        view.onKey = { host += $0 }; view.onGlide = { _,_ in glides += 1 }
        for code in ["#settings","#symbols","#emoji"] {
            let key = try XCTUnwrap(view.keys.first { $0.2 == code })
            XCTAssertTrue(key.0.accessibilityCustomActions?.isEmpty ?? true)
            let start = key.0.center, end = CGPoint(x: start.x,y: start.y - 60)
            view.handle(.end(key.1,start,end,[start,end],[key.1],false))
            view.handle(.end(key.1,start,end,[start,end],[0,1,2,7,8,9],false))
        }
        XCTAssertEqual(host,""); XCTAssertEqual(glides,0)
        XCTAssertEqual(view.keys.first { $0.2 == "a" }?.0.accessibilityCustomActions?.count,1)
    }
}
