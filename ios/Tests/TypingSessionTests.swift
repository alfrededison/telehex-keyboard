import XCTest
import TeleHexCore
@testable import TeleHex

private final class Document: TextDocument {
    var text = ""
    var cursor = 0
    var selection: String?
    var documentID: UUID? = UUID()
    var readable = true
    var trailingContextAvailable = true
    /// UIKit trả nil (không phải "") khi trước con trỏ chưa có gì.
    var nilWhenEmpty = false
    var beforeInput: String? { readable && !(nilWhenEmpty && cursor == 0) ? String(text.prefix(cursor)) : nil }
    var afterInput: String? { readable && trailingContextAvailable ? String(text.dropFirst(cursor + (selection?.count ?? 0))) : nil }
    func insert(_ value: String) {
        let start = text.index(text.startIndex, offsetBy: cursor)
        if let selection, !selection.isEmpty {
            let end = text.index(start, offsetBy: selection.count)
            text.replaceSubrange(start..<end, with: value)
            self.selection = nil
        } else { text.insert(contentsOf: value, at: start) }
        cursor += value.count
    }
    func moveCursor(by offset: Int) -> Bool {
        guard cursor + offset >= 0 && cursor + offset <= text.count else { return false }; cursor += offset; return true
    }
    func deleteBackward() {
        if let selection, !selection.isEmpty { insert(""); return }
        guard cursor > 0 else { return }
        cursor -= 1
        text.remove(at: text.index(text.startIndex, offsetBy: cursor))
    }
}

final class TypingSessionTests: XCTestCase {
    private func configured(_ incognito: Bool = false) -> (TypingSession,Document) {
        let session = TypingSession(), document = Document()
        let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        prefs.configuration.incognito = incognito
        prefs.configuration.snippets = ["dc":"địa chỉ"]
        session.vocabulary = KeyboardVocabulary(englishText: "hello\nhelp",vietnameseText: "xin\nchào\nngười\nbạn",phrasesText: "")
        session.configure(prefs)
        return (session,document)
    }
    func testSnippetLearningAndIncognitoIsolation() {
        let (session,document) = configured()
        var snapshots: [String] = []; session.onPersonalChanged = { snapshots.append($0) }
        for key in "dc " { session.press(String(key),in: document) }
        XCTAssertEqual(document.text,"địa chỉ ")
        for key in "chaof " { session.press(String(key),in: document) }
        XCTAssertTrue(snapshots.last?.contains("chào") == true)
        let (privateSession,privateDocument) = configured(true)
        var writes = 0; privateSession.onPersonalChanged = { _ in writes += 1 }
        for key in "chaof " { privateSession.press(String(key),in: privateDocument) }
        XCTAssertEqual(writes,0)
        session.englishMode = true; let count = snapshots.count
        for key in "hello " { session.press(String(key),in: document) }
        XCTAssertEqual(snapshots.count,count)
    }
    func testSnippetExpandsAtWordStartOrRightAfterAnOpeningBracket() {
        let (session,document) = configured()
        for key in "(dc x.dc " { session.press(String(key),in: document) }
        XCTAssertEqual(document.text,"(địa chỉ x.dc ")
    }
    func testUndoRedoAndStaleHostEdits() {
        let (session,document) = configured()
        session.insert("👩🏽‍💻 tiếng",in: document)
        session.undo(in: document); XCTAssertEqual(document.text,"")
        session.redo(in: document); XCTAssertEqual(document.text,"👩🏽‍💻 tiếng")
        document.insert(" external")
        session.undo(in: document); XCTAssertEqual(document.text,"👩🏽‍💻 tiếng external")
    }
    func testWordDeletionAndUnicodeCursor() {
        let (session,document) = configured()
        session.insert("👩🏽‍💻 xin chào ",in: document)
        let anchor = DocumentAnchor(document)
        XCTAssertEqual(session.wordsToDelete(1,in: document),"chào ")
        session.deleteWords(1,anchor: anchor,in: document)
        XCTAssertEqual(document.text,"👩🏽‍💻 xin ")
        session.moveCursor(-5,in: document); XCTAssertEqual(document.cursor,1)
        session.press("⌫",in: document); XCTAssertEqual(document.text," xin ")
        session.deleteWords(1,anchor: anchor,in: document); XCTAssertEqual(document.text," xin ")
    }
    func testCandidateReplacementAndKeyboardOwnedAutomaticSpace() {
        let (session,document) = configured()
        for key in "moiw" { session.press(String(key),in: document) }
        session.choose("mời",replacement: "MỜI",in: document)
        XCTAssertEqual(document.text,"MỜI ")
        session.press("!",in: document); XCTAssertEqual(document.text,"MỜI!")
    }
    func testTextViewCursorMovesAcrossWholeEmoji() {
        let view = UITextView(); view.text = "a👩🏽‍💻b"
        view.selectedRange = NSRange(location: view.text.utf16.count,length: 0)
        let document = TextViewDocument(view)
        XCTAssertTrue(document.moveCursor(by: -2))
        XCTAssertEqual(document.beforeInput,"a")
        XCTAssertTrue(document.moveCursor(by: 1))
        XCTAssertEqual(document.beforeInput,"a👩🏽‍💻")
        XCTAssertFalse(document.moveCursor(by: -3))
    }

    func testCursorWordCorrectionVerifiesWholeToken() {
        let (session,document) = configured()
        document.text = "toi"; document.cursor = 1
        session.refreshCandidates(in: document)
        XCTAssertTrue(session.candidates.contains("tôi"))
        session.choose("tôi",in: document)
        XCTAssertEqual(document.text,"tôi ")
        session.undo(in: document); XCTAssertEqual(document.text,"toi")
    }
    func testCursorWordCorrectionPreservesExistingSeparatorsAndUndo() {
        for trailing in [" bạn","\nBạn",", bạn","!", ""] {
            let (session,document) = configured()
            document.text = "toi" + trailing; document.cursor = 1
            session.refreshCandidates(in: document)
            XCTAssertTrue(session.candidates.contains("tôi"))
            session.choose("tôi",in: document)
            let separator = trailing.isEmpty ? " " : ""
            XCTAssertEqual(document.text,"tôi" + separator + trailing)
            session.undo(in: document); XCTAssertEqual(document.text,"toi" + trailing)
            session.redo(in: document); XCTAssertEqual(document.text,"tôi" + separator + trailing)
        }
    }
    func testExistingSpaceIsNotOwnedByTheKeyboardWhenTypingPunctuation() {
        let (session,document) = configured()
        document.text = "toi bạn"; document.cursor = 1
        session.refreshCandidates(in: document); session.choose("tôi",in: document)
        session.press(",",in: document)
        XCTAssertEqual(document.text,"tôi, bạn")
    }

    func testManualLanguageChoiceSurvivesFieldChangeAndURLSuppressesLearning() {
        let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        let document = Document(); var trait = UIKeyboardType.default
        let driver = KeyboardDriver(preferences: prefs,document: { document },traits: { (trait,.none) })
        driver.update(); driver.view.onToggleLanguage?()
        XCTAssertTrue(driver.session.englishMode)
        document.documentID = UUID(); driver.update()
        XCTAssertTrue(driver.session.englishMode)
        trait = .URL; document.documentID = UUID(); driver.update()
        XCTAssertFalse(driver.session.learningAllowed)
        XCTAssertTrue(driver.session.candidates.isEmpty)
    }
    func testGlideWaitsForSelectionAndInvalidatesOnExternalEdit() {
        let (session,document) = configured()
        let centers = "x:0,0\ni:1,0\nn:2,0\nc:0,1\nh:1,1\na:2,1\no:3,1"
        session.glide(path: "0,0;1,0;2,0",centers: centers,in: document)
        XCTAssertTrue(session.candidates.contains("xin")); XCTAssertEqual(document.text,"")
        session.refreshCandidates(in: document)
        XCTAssertTrue(session.candidates.contains("xin"))
        document.insert("external")
        session.choose("xin",in: document)
        XCTAssertEqual(document.text,"external")
    }

    func testEnglishGlideSpacesAfterPunctuationAndNeverLearns() {
        let (session,document) = configured()
        session.englishMode = true
        let initial = session.vocabulary!.personalData()
        for key in "hello," { session.press(String(key),in: document) }
        let centers = "h:0,0\ne:1,0\nl:2,0\np:3,0\no:4,0"
        XCTAssertTrue(session.glide(path: "0,0;1,0;2,0;3,0",centers: centers,in: document))
        XCTAssertTrue(session.candidates.contains("help"))
        session.choose("help",in: document)
        XCTAssertEqual(document.text,"hello, help ")
        // Sau dấu mở ngoặc thì không thêm dấu cách.
        session.press("(",in: document)
        session.glide(path: "0,0;1,0;2,0;3,0",centers: centers,in: document)
        session.choose("help",in: document)
        XCTAssertEqual(document.text,"hello, help (help ")
        XCTAssertEqual(session.vocabulary!.personalData(),initial)
        // Ô không có gợi ý (email, URL, ô số) thì không gõ vuốt.
        session.suggestionsEnabled = false
        XCTAssertFalse(session.glideAvailable)
        XCTAssertFalse(session.glide(path: "0,0;1,0;2,0;3,0",centers: centers,in: document))
    }
    func testEnglishContractionsWithoutApostropheAndCapitalI() {
        let session = TypingSession(), document = Document()
        session.englishMode = true
        session.vocabulary = KeyboardVocabulary(englishText: "don't\ni'll\nill\ndone", vietnameseText: "", phrasesText: "")
        for key in "dont" { session.press(String(key),in: document) }
        XCTAssertTrue(session.candidates.contains("don't"))
        session.choose("don't",in: document)
        XCTAssertEqual(document.text,"don't ")
        for key in "ill" { session.press(String(key),in: document) }
        XCTAssertTrue(session.candidates.contains("I'll"))
    }
    func testFirstSyllableInAnEmptyFieldCombinesWhenUIKitReportsNilContext() {
        let (session,document) = configured()
        document.nilWhenEmpty = true
        for key in "ddi" { session.press(String(key),in: document) }
        XCTAssertEqual(document.text,"đi")
    }
    func testAutoCapitalizedFirstLetterStillCombinesWithTelexDoubles() {
        let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        let document = Document()
        let driver = KeyboardDriver(preferences: prefs,document: { document },traits: { (.default,.sentences) },loadVocabulary: { _,_ in })
        driver.update()
        for key in "ddiaj" { driver.view.activate(String(key), index: driver.view.keys.first { $0.2 == String(key) }!.1) }
        XCTAssertEqual(document.text,"Địa")
    }
    func testMarkedMisspelledSyllableRestoresKeysOnCommitLikeUniKey() {
        let (session,document) = configured()
        var learned = 0; session.onPersonalChanged = { _ in learned += 1 }
        for key in "user there usser " { session.press(String(key),in: document) }
        XCTAssertEqual(document.text,"user thể user ")
        XCTAssertEqual(learned,2) // thể, user (gõ huỷ dấu có chủ ý); không học "user" khôi phục
        let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        prefs.configuration.englishFallback = false
        let other = TypingSession(), raw = Document(); other.configure(prefs)
        other.vocabulary = session.vocabulary
        for key in "user " { other.press(String(key),in: raw) }
        XCTAssertEqual(raw.text,"ủe ")
    }

    func testEnglishFallbackOptionAndPasteFromClipboard() {
        let (session,document) = configured()
        for key in "windows" { session.press(String(key),in: document) }
        XCTAssertEqual(document.text,"windows")
        let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        prefs.configuration.englishFallback = false
        let other = TypingSession(), raw = Document(); other.configure(prefs)
        for key in "windows" { other.press(String(key),in: raw) }
        XCTAssertNotEqual(raw.text,"windows")
        // Dán nguyên văn: chốt âm tiết đang gõ, có trong hoàn tác.
        let (paste,doc) = configured()
        for key in "xin " { paste.press(String(key),in: doc) }
        for key in "chaof" { paste.press(String(key),in: doc) }
        paste.insertText(" bạn ơi",in: doc)
        XCTAssertEqual(doc.text,"xin chào bạn ơi")
        paste.undo(in: doc)
        XCTAssertEqual(doc.text,"xin chào")
    }
    func testNextLetterValidityForDynamicHitZones() {
        let (session,document) = configured()
        for key in "ng" { session.press(String(key),in: document) }
        XCTAssertTrue(session.keepsSyllableValid("h"))
        XCTAssertFalse(session.keepsSyllableValid("k"))
        session.englishMode = true
        XCTAssertTrue(session.keepsSyllableValid("k"))
    }
    func testCompletionsAtDocumentEndWhenUIKitReturnsNilTrailingContext() {
        let session = TypingSession(), document = Document()
        session.englishMode = true
        session.vocabulary = KeyboardVocabulary(englishText: "help\nhello", vietnameseText: "", phrasesText: "")
        document.trailingContextAvailable = false
        for key in "hel" { session.press(String(key), in: document) }
        XCTAssertTrue(session.candidates.contains("help"))
        session.choose("help", in: document)
        XCTAssertEqual(document.text, "help ")
    }

    func testMissingDocumentIdentityInsertsWithoutStartingComposition() {
        let session = TypingSession(), document = Document()
        document.documentID = nil
        session.press("a", in: document)
        session.press("a", in: document)
        XCTAssertEqual(document.text, "aa")
        XCTAssertTrue(session.composing.isEmpty)
    }

    func testCompletionDoesNotOfferReplacementForPartOfAnExistingWord() {
        let session = TypingSession(), document = Document()
        session.englishMode = true
        session.vocabulary = KeyboardVocabulary(englishText: "help\nhello", vietnameseText: "", phrasesText: "")
        for key in "hel" { session.press(String(key), in: document) }
        XCTAssertTrue(session.candidates.contains("help"))
        session.reset()
        document.text = "hello"; document.cursor = 2
        session.press("l", in: document)
        XCTAssertTrue(session.candidates.isEmpty)
        XCTAssertEqual(document.text, "helllo")
    }

    func testGoldenCorpusThroughSwiftBridge() throws {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "golden", withExtension: "tsv"))
        let fixture = try String(contentsOf: url, encoding: .utf8)
        let engine = KeyboardEngine()
        var count = 0
        for line in fixture.components(separatedBy: .newlines) where !line.isEmpty && !line.hasPrefix("#") {
            let fields = line.components(separatedBy: "\t")
            let options = fields.count > 2 ? fields[2].components(separatedBy: ",") : []
            engine.configure(newToneStyle: options.contains("new"), lAsGrave: options.contains("l"), englishFallback: true)
            for key in fields[0] { _ = engine.press(letter: String(key)) }
            XCTAssertEqual(engine.text(), fields[1], "Input: \(fields[0])")
            count += 1
        }
        XCTAssertEqual(count, 431)
        print("GOLDEN \(count) original cases passed through Swift -> Kotlin/Native on iOS Simulator")
    }

    func testVietnameseAndEnglish() {
        let session = TypingSession(), document = Document()
        for key in "tieengs vieetj windows" { session.press(String(key), in: document) }
        XCTAssertEqual(document.text, "tiếng việt windows")
    }

    func testBackspaceCandidateAndEmoji() {
        let session = TypingSession(), document = Document()
        for key in "tieengs" { session.press(String(key), in: document) }
        session.press("⌫", in: document)
        XCTAssertEqual(document.text, "tiến")
        session.press(" ", in: document)
        for key in "moiw" { session.press(String(key), in: document) }
        session.choose("mời", in: document)
        XCTAssertEqual(document.text, "tiến mời ")
        session.press("👨‍👩‍👧‍👦", in: document)
        session.press("⌫", in: document)
        XCTAssertEqual(document.text, "tiến mời ")
    }

    func testCursorMoveDoesNotOverwriteExistingSyllable() {
        let session = TypingSession(), document = Document()
        for key in "tieengs" { session.press(String(key), in: document) }
        document.cursor = 0
        session.press("a", in: document)
        XCTAssertEqual(document.text, "atiếng")
    }

    func testSelectionAndFieldSwitchInvalidateComposition() {
        let session = TypingSession(), document = Document()
        for key in "tieengs" { session.press(String(key), in: document) }
        document.cursor = 0
        document.selection = document.text
        session.press("a", in: document)
        XCTAssertEqual(document.text, "a")
        document.documentID = UUID()
        document.text = "a "; document.cursor = 2
        session.press("s", in: document)
        XCTAssertEqual(document.text, "a s")
    }

    func testBackspacedWordTakesToneAgain() {
        let session = TypingSession(), document = Document()
        for key in "hieeu " { session.press(String(key), in: document) }
        session.press("⌫", in: document)
        session.press("s", in: document)
        XCTAssertEqual(document.text, "hiếu")
        session.undo(in: document)
        XCTAssertEqual(document.text, "hiêu")
    }

    func testResumedWordIsLearnedOnlyWhenChanged() {
        let (session,document) = configured()
        var snapshots: [String] = []; session.onPersonalChanged = { snapshots.append($0) }
        for key in "ban " { session.press(String(key),in: document) }
        let learned = snapshots.count
        for key in ["⌫","h","⌫"," "] { session.press(key,in: document) }
        XCTAssertEqual(document.text,"ban ")
        XCTAssertEqual(snapshots.count,learned)
        for key in ["⌫","s"," "] { session.press(key,in: document) }
        XCTAssertEqual(document.text,"bán ")
        XCTAssertEqual(snapshots.count,learned + 1)
    }

    func testResumedAutoCapitalizedWordIsLearnedLowercase() {
        let (session,document) = configured()
        var snapshots: [String] = []; session.onPersonalChanged = { snapshots.append($0) }
        session.capitalizesWord = { before in before.isEmpty || before.last?.isWhitespace == true }
        for key in "xin Ban " { session.press(String(key),in: document) }
        for key in ["⌫","s"," "] { session.press(key,in: document) }
        XCTAssertEqual(document.text,"xin Bán ")
        XCTAssertTrue(snapshots.last?.contains("bán") == true)
        XCTAssertFalse(snapshots.last?.contains("Bán") == true)
    }

    func testTappedWordEndTakesToneButMidWordDoesNot() {
        let session = TypingSession(), document = Document()
        for key in "ban chieeu " { session.press(String(key), in: document) }
        session.press("⌫", in: document)
        session.press("f", in: document)
        XCTAssertEqual(document.text, "ban chiều")
        document.cursor = 3
        session.press("s", in: document)
        XCTAssertEqual(document.text, "bán chiều")
        document.cursor = 1
        session.press("a", in: document)
        XCTAssertEqual(document.text, "baán chiều")
    }

    func testUnavailableContextFallsBackWithoutDeletingHostText() {
        let session = TypingSession(), document = Document()
        for key in "tieengs" { session.press(String(key), in: document) }
        document.readable = false
        session.press("a", in: document)
        session.press("a", in: document)
        XCTAssertEqual(document.text, "tiếngaa")
    }

    func testStaleCandidateDoesNotReplaceExternallyEditedText() {
        let session = TypingSession(), document = Document()
        for key in "moi" { session.press(String(key), in: document) }
        document.insert("!")
        session.choose("mời", in: document)
        XCTAssertEqual(document.text, "moi!")
    }
}
