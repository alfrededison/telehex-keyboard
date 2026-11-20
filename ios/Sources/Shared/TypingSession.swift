import UIKit
import TeleHexCore

protocol TextDocument: AnyObject {
    var beforeInput: String? { get }
    var afterInput: String? { get }
    var selection: String? { get }
    var documentID: UUID? { get }
    func insert(_ text: String)
    func deleteBackward()
    func moveCursor(by offset: Int) -> Bool
}
extension TextDocument { func moveCursor(by offset: Int) -> Bool { false } }

struct DocumentAnchor: Equatable {
    let id: UUID?
    let before: String?
    let after: String?
    let selected: String
    init(_ document: TextDocument) {
        id = document.documentID; before = document.beforeInput; after = document.afterInput
        selected = document.selection ?? ""
    }
    func matches(_ document: TextDocument) -> Bool { self == DocumentAnchor(document) }
}

final class TypingSession {
    private let engine = KeyboardEngine()
    private var expectedContext: String?
    private var documentID: UUID?
    private var automaticSpace: DocumentAnchor?
    private var candidateAnchor: DocumentAnchor?
    private enum Target { case composition, next, cursor(String, String), glide }
    private var target = Target.composition
    private var configuration = KeyboardConfiguration()
    var vocabulary: KeyboardVocabulary?
    var emoji: EmojiCatalog?
    var onPersonalChanged: ((String) -> Void)?
    var suggestionsEnabled = true
    var learningAllowed = true
    var suppressCapitalizationLearning = false
    /// Hoa/thường của gợi ý (bàn phím đặt trước mỗi lần làm mới): khoá hoa, chữ sắp gõ đang hoa (đầu câu, ⇧).
    var capsLock = false, capitalizeNext = false
    /// Emoji gợi ý cho từ vừa gõ (khi chưa soạn gì): chọn thì chèn không kèm khoảng trắng, như Android.
    private var nextEmoji: Set<String> = []
    /// Ô nhập có tự viết hoa chữ đứng sau đoạn văn bản này không; dùng khi nạp lại từ đã gõ.
    var capitalizesWord: ((String) -> Bool)?
    /// Từ đã có trong ô nhập vừa được nạp lại để sửa: chốt y nguyên thì không học lại lần nữa.
    private var resumedWord: String?
    var englishMode = false { didSet { if oldValue != englishMode { reset() } } }
    private(set) var composing = ""
    private var latestSuggestions: [String] = []
    /// Gõ vuốt vẫn hiện từ khi tắt gợi ý (như Android), các gợi ý khác thì không.
    var candidates: [String] {
        if case .glide = target { return latestSuggestions }
        return suggestionsEnabled ? latestSuggestions : []
    }
    private struct Edit { let old: String; var new: String; var anchor: DocumentAnchor }
    private var undoStack: [Edit] = [], redoStack: [Edit] = []
    var canUndo: Bool { !undoStack.isEmpty }
    var canRedo: Bool { !redoStack.isEmpty }

    func configure(_ preferences: KeyboardPreferences) {
        reset()
        configuration = preferences.configuration
        engine.configure(newToneStyle: preferences.newToneStyle, lAsGrave: preferences.lAsGrave, englishFallback: preferences.configuration.englishFallback)
        configureVocabulary(preferences)
        suggestionsEnabled = preferences.suggestions
    }
    func configureVocabulary(_ preferences: KeyboardPreferences) {
        // Ô kề theo lưới thật của profile (cả lưới truyền thống, có hàng số, 8 cột), như Android
        let neighbors = LayoutBridge.shared.neighbors(letters: preferences.profile.letters)
        vocabulary?.configure(newToneStyle: preferences.newToneStyle, lAsGrave: preferences.lAsGrave, neighbors: neighbors)
    }
    private func learn(_ word: String, before: String) {
        guard learningAllowed, !englishMode, !configuration.incognito, let vocabulary, !word.isEmpty, word != resumedWord else { return }
        vocabulary.learn(before: before, word: suppressCapitalizationLearning ? word.lowercased() : word)
        onPersonalChanged?(vocabulary.personalData())
    }
    func refreshCandidates(in document: TextDocument) {
        synchronize(with: document)
        if case .glide = target, candidateAnchor?.matches(document) == true { return }
        candidateAnchor = DocumentAnchor(document)
        guard suggestionsEnabled, document.documentID != nil, document.selection?.isEmpty != false else { latestSuggestions = []; return }
        let before = document.beforeInput ?? "", after = document.afterInput ?? ""
        if !composing.isEmpty {
            target = .composition
            let boundary = before.dropLast(composing.count).last
            let ownsToken = before.hasSuffix(composing) && boundary?.isLetter != true && boundary?.isNumber != true && after.first?.isLetter != true && after.first?.isNumber != true
            // Nội dung, thứ tự và hoa/thường theo quy tắc chung với Android (module typing)
            let words = (ownsToken ? vocabulary?.suggestions(prefix: composing, before: before, englishMode: englishMode) ?? [] : []).map(casedCompletion)
            let corrections = !englishMode && ownsToken ? vocabulary?.corrections(raw: engine.rawKeys(), before: String(before.dropLast(composing.count))) ?? [] : []
            if englishMode { latestSuggestions = unique(words) }
            else if engine.invalidSyllable() { latestSuggestions = Suggestions.shared.forInvalidSyllable(corrections: corrections) }
            else {
                latestSuggestions = Suggestions.shared.forSyllable(tones: engine.candidates(), completions: words, corrections: corrections,
                                                                    emoji: emoji?.suggestions(composing) ?? [])
            }
        } else {
            let head = String(before.reversed().prefix { $0.isLetter || (englishMode && "'’".contains($0)) }.reversed())
            let tail = String(after.prefix { $0.isLetter || (englishMode && "'’".contains($0)) })
            if !head.isEmpty && head.count + tail.count <= 32 {
                target = .cursor(head, tail)
                latestSuggestions = englishMode ? [] : vocabulary?.wordCorrections(word: head + tail, before: String(before.dropLast(head.count))) ?? []
            } else if Suggestions.shared.offersNextWords(textBeforeCursor: before) {
                // Như Android: sau khoảng trắng là gợi ý từ kế tiếp (dù sau con trỏ có chữ), kèm emoji cho từ vừa gõ
                target = .next
                let next = (vocabulary?.nextWords(before: before, englishMode: englishMode) ?? []).map(casedPrediction)
                let previous = String(before.trimmingCharacters(in: .whitespacesAndNewlines).reversed().prefix { $0.isLetter }.reversed())
                let emojis = englishMode || previous.isEmpty ? [] : emoji?.suggestions(previous) ?? []
                nextEmoji = Set(emojis)
                latestSuggestions = unique(next + emojis)
            } else { latestSuggestions = [] }
        }
    }
    private func unique(_ words: [String]) -> [String] { var seen = Set<String>(); return words.filter { seen.insert($0).inserted } }
    private func usuallyCapitalized(_ word: String) -> Bool { !englishMode && vocabulary?.usuallyCapitalized(word: word) == true }
    private func casedCompletion(_ word: String) -> String {
        Suggestions.shared.casedCompletion(word: word, typed: composing, capsLock: capsLock, usuallyCapitalized: usuallyCapitalized(word))
    }
    private func casedPrediction(_ word: String) -> String {
        Suggestions.shared.casedPrediction(word: word, capsLock: capsLock, capitalizeNext: capitalizeNext, usuallyCapitalized: usuallyCapitalized(word))
    }
    func reset() {
        engine.reset(); composing = ""; expectedContext = nil; documentID = nil
        automaticSpace = nil; candidateAnchor = nil; latestSuggestions = []; target = .composition; resumedWord = nil; nextEmoji = []
    }
    func synchronize(with document: TextDocument) {
        if !composing.isEmpty && (document.documentID != documentID || document.beforeInput != expectedContext || document.selection?.isEmpty == false) { reset() }
        if case .glide = target, candidateAnchor?.matches(document) != true { reset() }
    }
    /// Chốt âm tiết đang soạn như khi chạm cách mà không chèn gì (đổi VI/EN): khôi phục phím cho từ sai chính tả hoặc học
    /// từ, như Android.
    func finishComposition(in document: TextDocument) {
        synchronize(with: document)
        if !composing.isEmpty {
            let context = String((document.beforeInput ?? "").dropLast(composing.count))
            if !englishMode, let restored = engine.restoredKeys() { replace(with: restored, in: document) }
            else { learn(composing, before: context) }
        }
        reset()
    }
    func press(_ key: String, in document: TextDocument) {
        synchronize(with: document)
        if case .glide = target { target = .composition; latestSuggestions = [] }
        defer { refreshCandidates(in: document) }
        let removeSpace = Spacing.shared.removesAutoSpace(typed: key) && automaticSpace?.matches(document) == true
        automaticSpace = nil
        if key == "⌫" {
            if composing.isEmpty {
                let old = document.selection.flatMap { $0.isEmpty ? nil : $0 } ?? document.beforeInput?.last.map(String.init) ?? ""
                guard !old.isEmpty else { return }
                change(old: old, new: "", in: document)
            } else { replace(with: englishMode ? String(composing.dropLast()) : engine.backspace(), in: document) }
        } else if key.count == 1, let scalar = key.unicodeScalars.first,
                  (65...90).contains(scalar.value) || (97...122).contains(scalar.value) || (englishMode && key == "'") {
            if document.documentID == nil { reset(); document.insert(key); return }
            if composing.isEmpty && !englishMode { resumeWord(in: document) }
            // Ô trống: UIKit trả context trước con trỏ là nil chứ không phải "", vẫn bắt đầu âm tiết ("dd" → "đ" ngay chữ đầu).
            // Chèn xong mà vẫn không đọc được context thì không thể kiểm chứng để sửa: quay về gõ thô từng phím.
            replace(with: englishMode ? composing + key : engine.press(letter: key), in: document)
            if document.beforeInput == nil { reset() }
        } else {
            let before = document.beforeInput ?? ""
            let context = String(before.dropLast(composing.count))
            // Gõ tắt theo quy tắc chung với Android: khớp cả phần đã chốt trước âm tiết ("k2", "@@g"), tách sau dấu mở
            // ("(dc" → "(được", "x.dc" thì không), chữ đang hiện trước rồi phím đã gõ, giữ chữ hoa đầu
            // Có vùng chọn thì chạm cách chỉ thay vùng chọn, không bung gõ tắt từ chữ trước vùng chọn
            let shortcut = key == " " && document.selection?.isEmpty != false ? Shortcuts.shared.match(snippets: configuration.snippets, textBefore: context, typed: composing,
                                                                keys: englishMode || composing.isEmpty ? composing : engine.rawKeys()) : nil
            if let shortcut {
                change(old: shortcut.prefix + composing, new: shortcut.expansion, in: document)
            } else if !composing.isEmpty {
                if !englishMode, let restored = engine.restoredKeys() {
                    // Như UniKey: từ có dấu mà sai chính tả thì trả lại phím đã gõ ("ủe" → user), không học
                    replace(with: restored, in: document)
                } else { learn(composing, before: context) }
            }
            reset()
            change(old: removeSpace ? " " : "", new: key, in: document, typing: true)
        }
    }
    /// Con trỏ ngay sau một từ đã chốt (vừa xoá dấu cách, hoặc chạm vào cuối từ): nạp lại từ đó làm âm tiết
    /// đang soạn để phím kế tiếp gõ tiếp vào nó như Android: "hiêu␣" ⌫ s → hiếu, chạm sau "ban" rồi gõ s → bán.
    private func resumeWord(in document: TextDocument) {
        guard document.selection?.isEmpty != false, let before = document.beforeInput else { return }
        if let next = document.afterInput?.first, next.isLetter || next.isNumber { return }
        let word = String(before.reversed().prefix { $0.isLetter || $0.isNumber }.reversed())
        guard !word.isEmpty, word.count <= 32, engine.resume(word: word) else { return }
        composing = word; expectedContext = before; documentID = document.documentID; resumedWord = engine.text()
        // Chữ hoa đầu từ do ô nhập tự viết hoa ở vị trí đó thì học như chữ thường, đừng coi là tên riêng
        suppressCapitalizationLearning = word.first?.isUppercase == true && capitalizesWord?(String(before.dropLast(word.count))) == true
    }
    func choose(_ candidate: String, replacement: String? = nil, in document: TextDocument) {
        synchronize(with: document)
        guard candidates.contains(candidate), candidateAnchor?.matches(document) == true else { refreshCandidates(in: document); return }
        let before = document.beforeInput ?? ""
        let value = replacement ?? candidate
        var addedSpace = false
        // Quy tắc chung với Android: thêm khoảng trắng trừ khi sau đó đã có khoảng trắng hay dấu câu đứng sát
        func insertion(before trailing: String?) -> String {
            addedSpace = Spacing.shared.pickAddsSpace(textAfterCursor: trailing)
            return value + (addedSpace ? " " : "")
        }
        switch target {
        case .glide where englishMode:
            // EN không soạn âm tiết và không học: chèn thẳng sau chữ đang có, cách khỏi chữ hay dấu câu ngay trước
            // ("hello,|" → "hello, world"), không cách sau khoảng trắng hay dấu mở ngoặc. Như Android.
            let lead = Spacing.shared.glideNeedsLeadingSpace(textBeforeCursor: before) ? " " : ""
            change(old: "", new: lead + insertion(before: document.afterInput), in: document)
        case .composition, .glide:
            guard composing.isEmpty || before.hasSuffix(composing) else { reset(); return }
            let context = String(before.dropLast(composing.count))
            change(old: composing, new: insertion(before: document.afterInput), in: document)
            learn(value, before: context)
        case .next where nextEmoji.contains(candidate):
            change(old: "", new: value, in: document)
        case .next:
            change(old: "", new: insertion(before: document.afterInput), in: document); learn(value, before: before)
        case let .cursor(head, tail):
            guard tail.isEmpty || document.moveCursor(by: tail.count), document.beforeInput?.hasSuffix(head + tail) == true else { reset(); return }
            change(old: head + tail, new: insertion(before: document.afterInput), in: document)
            learn(value, before: String(before.dropLast(head.count)))
        }
        reset(); automaticSpace = addedSpace ? DocumentAnchor(document) : nil; refreshCandidates(in: document)
    }
    /// Ô cho phép gõ vuốt: không phải email, URL, ô số; đã nạp từ điển. Như Android, gõ vuốt tiếng Việt chạy cả khi tắt gợi ý
    /// (từ gõ vuốt vẫn hiện để chọn); EN thì cần gợi ý.
    var glideAvailable: Bool { learningAllowed && configuration.glide && vocabulary != nil && (suggestionsEnabled || !englishMode) }
    /// Trả về false khi không gõ vuốt được ở ô/trạng thái này (khác với vuốt mà không nhận ra từ).
    @discardableResult func glide(path: String, centers: String, in document: TextDocument) -> Bool {
        synchronize(with: document)
        guard glideAvailable, let vocabulary, document.documentID != nil, document.selection?.isEmpty != false else { return false }
        candidateAnchor = DocumentAnchor(document); target = .glide
        let before = document.beforeInput ?? ""
        // VI thay âm tiết đang soạn; EN chèn sau chữ đang có nên lấy cả đoạn trước con trỏ làm ngữ cảnh.
        let context = englishMode ? before : String(before.dropLast(composing.count))
        latestSuggestions = vocabulary.glide(path: path, centers: centers, before: context, englishMode: englishMode).map { word in
            Suggestions.shared.casedGlide(word: word, english: englishMode, typed: englishMode ? "" : composing, capsLock: capsLock,
                                          capitalizeNext: capitalizeNext, usuallyCapitalized: usuallyCapitalized(word))
        }
        return true
    }
    /// Vùng chạm động: phím chữ này có giữ âm tiết đang gõ hợp lệ không (EN thì luôn có).
    func keepsSyllableValid(_ letter: String) -> Bool { englishMode || engine.keepsSyllableValid(letter: letter) }
    /// Chèn nguyên văn (dán từ lịch sử clipboard): chốt âm tiết đang gõ trước, có trong hoàn tác.
    func insertText(_ text: String, in document: TextDocument) {
        synchronize(with: document)
        if !composing.isEmpty { learn(composing, before: String((document.beforeInput ?? "").dropLast(composing.count))) }
        reset()
        change(old: "", new: text, in: document)
        refreshCandidates(in: document)
    }
    func forget(_ word: String, in document: TextDocument) {
        guard !englishMode, !configuration.incognito else { return }
        vocabulary?.forget(word: word)
        if let vocabulary { onPersonalChanged?(vocabulary.personalData()) }
        refreshCandidates(in: document)
    }
    func clearLearned(in document: TextDocument) { vocabulary?.clearPersonal(); refreshCandidates(in: document) }
    private func replace(with text: String, in document: TextDocument) {
        change(old: composing, new: text, in: document, typing: true)
        composing = text; expectedContext = document.beforeInput; documentID = document.documentID
    }
    /// All edits are confined to verified suffixes or the host's actual selection.
    /// `typing`: lần sửa do gõ phím (không phải dán, chọn gợi ý…), được gộp với lần trước trong cùng một từ.
    private func change(old: String, new: String, in document: TextDocument, history: Bool = true, typing: Bool = false) {
        let selected = document.selection ?? ""
        // Gộp vào lần sửa trước chỉ khi văn bản chưa đổi từ bên ngoài kể từ lần đó
        let canMerge = typing && undoStack.last?.anchor.matches(document) == true
        if selected.isEmpty {
            guard old.isEmpty || document.beforeInput?.hasSuffix(old) == true else { reset(); return }
            for _ in old { document.deleteBackward() }
            document.insert(new)
        // Có vùng chọn: chèn thay vùng chọn; xoá thì phải gọi deleteBackward, vì insertText("") của iOS không làm gì.
        } else if new.isEmpty { document.deleteBackward() } else { document.insert(new) }
        if history, document.documentID != nil {
            // Hoàn tác theo từ như Android: các lần sửa liên tiếp trong cùng một từ (gõ từng phím, đổi dấu) gộp làm một,
            // đến khoảng trắng thì khép lại: "việt nam" hai ngón ← xoá "nam", không phải một phím.
            if canMerge, selected.isEmpty, !new.isEmpty, var last = undoStack.last, !last.new.isEmpty,
               last.new.last?.isWhitespace != true, last.new.hasSuffix(old) {
                last.new = String(last.new.dropLast(old.count)) + new; last.anchor = DocumentAnchor(document)
                undoStack[undoStack.count - 1] = last
            } else {
                undoStack.append(Edit(old: selected.isEmpty ? old : selected, new: new, anchor: DocumentAnchor(document)))
                if undoStack.count > 50 { undoStack.removeFirst() }
            }
            redoStack.removeAll()
        }
    }
    /// Trả false khi không có gì để hoàn tác (hoặc văn bản đã đổi ngoài bàn phím), để báo cho người dùng.
    @discardableResult func undo(in document: TextDocument) -> Bool {
        guard let last = undoStack.last, last.anchor.matches(document), document.selection?.isEmpty != false else { return false }
        reset(); undoStack.removeLast()
        change(old: last.new, new: last.old, in: document, history: false)
        redoStack.append(Edit(old: last.old, new: last.new, anchor: DocumentAnchor(document)))
        refreshCandidates(in: document)
        return true
    }
    @discardableResult func redo(in document: TextDocument) -> Bool {
        guard let last = redoStack.last, last.anchor.matches(document), document.selection?.isEmpty != false else { return false }
        reset(); redoStack.removeLast()
        change(old: last.old, new: last.new, in: document, history: false)
        undoStack.append(Edit(old: last.old, new: last.new, anchor: DocumentAnchor(document)))
        refreshCandidates(in: document)
        return true
    }
    func insert(_ text: String, in document: TextDocument) {
        synchronize(with: document); reset(); change(old: "", new: text, in: document); refreshCandidates(in: document)
    }
    func moveCursor(_ offset: Int, in document: TextDocument) { reset(); _ = document.moveCursor(by: offset); refreshCandidates(in: document) }
    func moveLine(_ direction: Int, in document: TextDocument) {
        let before = document.beforeInput ?? "", after = document.afterInput ?? ""
        let col = before.split(separator: "\n", omittingEmptySubsequences: false).last?.count ?? 0
        let offset: Int
        if direction < 0 {
            let lines = before.split(separator: "\n", omittingEmptySubsequences: false)
            guard lines.count > 1 else { return }
            let previous = lines[lines.count - 2].count
            offset = -(col + 1 + max(0, previous - col))
        } else {
            let lines = after.split(separator: "\n", omittingEmptySubsequences: false)
            guard lines.count > 1 else { return }
            offset = lines[0].count + 1 + min(col, lines[1].count)
        }
        moveCursor(offset, in: document)
    }
    /// Đoạn sẽ xoá khi kéo phím cách sang trái, theo quy tắc chung với Android (`WordStep`): mỗi dấu xuống dòng là một bước,
    /// "abc\n  " một bước xoá cả khoảng trắng lẫn dấu xuống dòng; quá mép trái thì tới đầu dòng.
    func wordsToDelete(_ count: Int, toLineStart: Bool = false, in document: TextDocument) -> String {
        WordStep.shared.deletion(textBefore: document.beforeInput ?? "", words: Int32(max(0, min(20, count))), toLineStart: toLineStart)
    }
    func deleteWords(_ count: Int, toLineStart: Bool = false, anchor: DocumentAnchor, in document: TextDocument) {
        guard anchor.matches(document) else { return }
        let deleted = wordsToDelete(count, toLineStart: toLineStart, in: document)
        reset(); change(old: deleted, new: "", in: document); refreshCandidates(in: document)
    }
}
final class ProxyDocument: TextDocument {
    private let proxy: UITextDocumentProxy
    init(_ proxy: UITextDocumentProxy) { self.proxy = proxy }
    var beforeInput: String? { proxy.documentContextBeforeInput }
    var afterInput: String? { proxy.documentContextAfterInput }
    var selection: String? { proxy.selectedText }
    var documentID: UUID? {
        // UIKit can return nil before a host connects despite the Swift getter's nonnull type.
        // Read the documented property through Objective-C to avoid UUID's forced nil bridge.
        (proxy as? NSObject)?.value(forKey: #keyPath(UITextDocumentProxy.documentIdentifier)) as? UUID
    }
    func insert(_ text: String) { proxy.insertText(text) }
    func deleteBackward() { proxy.deleteBackward() }
    func moveCursor(by offset: Int) -> Bool {
        let before = proxy.documentContextBeforeInput ?? "", after = proxy.documentContextAfterInput ?? ""
        guard offset >= -before.count && offset <= after.count else { return false }
        let utf16 = offset < 0 ? -before.suffix(-offset).utf16.count : after.prefix(offset).utf16.count
        proxy.adjustTextPosition(byCharacterOffset: utf16)
        return true
    }
}

final class TextViewDocument: TextDocument {
    weak var textView: UITextView?
    let documentID: UUID? = UUID()
    init(_ textView: UITextView) { self.textView = textView }
    var beforeInput: String? {
        guard let textView, let range = textView.textRange(from: textView.beginningOfDocument,
                                                         to: textView.selectedTextRange?.start ?? textView.endOfDocument) else { return nil }
        return textView.text(in: range)
    }
    var afterInput: String? {
        guard let textView, let range = textView.textRange(from: textView.selectedTextRange?.end ?? textView.endOfDocument,
                                                         to: textView.endOfDocument) else { return nil }
        return textView.text(in: range)
    }
    var selection: String? {
        guard let textView, let range = textView.selectedTextRange else { return nil }
        return textView.text(in: range)
    }
    func insert(_ text: String) { textView?.insertText(text) }
    func deleteBackward() { textView?.deleteBackward() }
    func moveCursor(by offset: Int) -> Bool {
        guard let textView, let range = textView.selectedTextRange else { return false }
        let context = offset < 0 ? beforeInput ?? "" : afterInput ?? ""
        guard abs(offset) <= context.count else { return false }
        let units = offset < 0 ? -context.suffix(-offset).utf16.count : context.prefix(offset).utf16.count
        guard let position = textView.position(from: range.start, offset: units) else { return false }
        textView.selectedTextRange = textView.textRange(from: position, to: position)
        return true
    }
}
