import UIKit
import AudioToolbox

final class KeyboardDriver {
    let preferences: KeyboardPreferences
    let store: KeyboardDataStore
    lazy var view = HexKeyboardView(preferences: preferences)
    let session = TypingSession()
    lazy var files: KeyboardFiles = {
        let service = KeyboardFiles(store: store,preferences: preferences)
        service.onReload = { [weak self] configurationChanged in
            guard let self else { return }
            if configurationChanged { self.applyConfiguration() }
            self.reloadVocabulary()
        }
        service.onMessage = { [weak self] in self?.view.message($0) }
        return service
    }()
    private let document: () -> TextDocument
    private let traits: () -> (UIKeyboardType, UITextAutocapitalizationType)
    private let loadVocabulary: VocabularyLoader.Load
    private var preferredEnglish = false
    private var currentDocument: UUID?
    private var wordDeleteAnchor: DocumentAnchor?
    private var generation = 0
    private var capsLock = false, suppressAutoCapital = false, automaticCapital = false
    /// ⇧ vừa chạm: chỉ chữ kế tiếp viết hoa. `lastShift`: lần chạm ⇧ trước, để nhận chạm đúp khoá chữ hoa.
    private var shiftOnce = false, lastShift = Date.distantPast
    /// Chữ sắp gõ là hoa: khoá hoa, ⇧ một lần, hoặc tự viết hoa đầu câu chưa bị tắt.
    private var upperNext: Bool { capsLock || shiftOnce || (automaticCapital && !suppressAutoCapital) }
    var onNextKeyboard: (() -> Void)?
    var fullAccess: () -> Bool = { false }
    /// Clipboard cho cắt/chép/dán trên phím cách; test dùng clipboard riêng.
    var pasteboard = UIPasteboard.general
    lazy var clipboard = ClipboardHistory(store: store)
    /// Lịch sử clipboard: người dùng bật trong app và bàn phím có Truy cập đầy đủ (iOS mới cho đọc clipboard).
    var clipboardEnabled: Bool { fullAccess() && preferences.configuration.clipboardHistory }

    init(preferences: KeyboardPreferences = KeyboardPreferences(), store: KeyboardDataStore = KeyboardDataStore(), document: @escaping () -> TextDocument, traits: @escaping () -> (UIKeyboardType, UITextAutocapitalizationType), loadVocabulary: @escaping VocabularyLoader.Load = VocabularyLoader.load) {
        self.preferences = preferences; self.store = store; self.document = document; self.traits = traits
        self.loadVocabulary = loadVocabulary
        session.configure(preferences)
        session.onPersonalChanged = { [weak self] text in self?.store.schedulePersonal(text) }
        view.onKey = { [weak self] key in
            guard let self else { return }
            self.update()
            if self.session.composing.isEmpty { self.session.suppressCapitalizationLearning = self.capsLock || self.automaticCapital }
            if self.fullAccess() {
                if self.preferences.configuration.haptics { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
                if self.preferences.configuration.sound { AudioServicesPlaySystemSound(1104) }
            }
            self.session.press(key, in: self.document())
            if key.first?.isLetter == true { self.suppressAutoCapital = false }
            // Hoa một lần chỉ cho chữ kế tiếp; dấu câu, ↵, ⌫ cũng bỏ nó. Phím khác chen giữa thì không còn là chạm đúp ⇧.
            if key != " " { self.shiftOnce = false }
            self.lastShift = .distantPast
            self.update()
        }
        view.onCandidate = { [weak self] word in
            guard let self else { return }
            let value = self.view.emoji?.variant(word,gender: self.preferences.configuration.emojiGender,tone: self.preferences.configuration.emojiTone) ?? word
            let replacement = self.capsLock ? value.uppercased() : self.upperNext ? value.prefix(1).uppercased() + value.dropFirst() : value
            self.session.choose(word,replacement: replacement,in: self.document())
            self.shiftOnce = false
            self.update()
        }
        view.onForgetCandidate = { [weak self] word in
            guard let self else { return }; self.session.forget(word,in: self.document()); self.update()
        }
        view.onToggleLanguage = { [weak self] in
            guard let self else { return }; self.session.englishMode.toggle(); self.preferredEnglish=self.session.englishMode; self.update()
        }
        view.onCapsLock = { [weak self] in
            guard let self else { return }
            if self.capsLock { self.capsLock=false; self.suppressAutoCapital=true }
            else if self.automaticCapital && !self.suppressAutoCapital { self.suppressAutoCapital=true }
            else { self.capsLock=true; self.suppressAutoCapital=false }
            self.shiftOnce = false
            self.update()
        }
        view.onShift = { [weak self] in self?.pressShift() }
        view.onNextKeyboard = { [weak self] in self?.onNextKeyboard?() }
        view.onPreferencesChanged = { [weak self] in
            self?.applyConfiguration()
        }
        view.onGlide = { [weak self] path, centers in
            guard let self else { return }
            let attempted = self.session.glide(path: path,centers: centers,in: self.document()); self.update()
            if attempted && self.session.candidates.isEmpty { self.view.message("Chưa nhận ra từ, thử lướt lại") }
        }
        view.onCursor = { [weak self] horizontal, vertical in
            guard let self else { return }
            if horizontal != 0 { self.session.moveCursor(horizontal,in: self.document()) }
            if vertical != 0 { self.session.moveLine(vertical,in: self.document()) }
            self.update()
        }
        view.onWordDelete = { [weak self] count, commit in
            guard let self else { return }
            if count == 0 && !commit { self.wordDeleteAnchor = DocumentAnchor(self.document()) }
            if commit, let anchor = self.wordDeleteAnchor {
                if !anchor.selected.isEmpty { self.command("space-left") }
                else { self.session.deleteWords(count,anchor: anchor,in: self.document()) }
                self.wordDeleteAnchor=nil; self.update()
            }
        }
        view.onCommand = { [weak self] command in self?.command(command) }
        view.onInsertText = { [weak self] text in
            guard let self else { return }; self.session.insertText(text, in: self.document()); self.update()
        }
        view.clipboard = clipboard
        reloadVocabulary()
    }
    /// Phím ⇧ của bố cục truyền thống, như Android: chữ kế tiếp đang thường thì viết hoa, đang hoa (cả tự viết hoa
    /// đầu câu) thì viết thường; chạm đúp thì khoá chữ hoa, chạm lần nữa để tắt.
    func pressShift() {
        let now = Date(), quick = now.timeIntervalSince(lastShift) < 0.3
        lastShift = now
        if capsLock { capsLock = false; shiftOnce = false; lastShift = .distantPast; suppressAutoCapital = automaticCapital }
        else if quick { capsLock = true; shiftOnce = false; suppressAutoCapital = false }
        else if upperNext { shiftOnce = false; suppressAutoCapital = true }
        else { shiftOnce = true }
        update()
    }
    /// Cài đặt app gửi qua App Group: áp khi bàn phím mở ra, chạy lệnh xoá dữ liệu học/nạp lại gói ngôn ngữ.
    /// Có Truy cập đầy đủ thì báo lại profile đang dùng để app hiện đúng.
    func syncShared(_ shared: UserDefaults? = SharedSettings.store) {
        let update = preferences.applyShared(from: shared)
        if update.clearPersonal { session.clearLearned(in: document()); store.clearPersonal() }
        if update.changed { applyConfiguration() }
        if update.clearPersonal || update.reloadPacks { reloadVocabulary() }
        if fullAccess() { SharedSettings.reportKeyboardProfiles(preferences.configuration, in: shared) }
        if clipboardEnabled { clipboard.prune() }
    }
    private func applyConfiguration() {
        if fullAccess() { SharedSettings.reportKeyboardProfiles(preferences.configuration, in: SharedSettings.store) }
        // Tắt lịch sử clipboard là xoá hết những gì đã lưu.
        if !preferences.configuration.clipboardHistory && !clipboard.items.isEmpty { clipboard.clearAll() }
        session.configure(preferences)
        update()
        view.superview?.setNeedsLayout()
    }
    func reloadVocabulary() {
        generation += 1; let request = generation
        session.reset(); session.vocabulary = nil
        loadVocabulary(store) { [weak self] vocabulary, emoji in
            guard let self, request == self.generation else { return }
            self.session.vocabulary=vocabulary; self.session.emoji=emoji; self.view.emoji=emoji
            self.session.configureVocabulary(self.preferences)
            self.update(); self.view.refreshPanel()
        }
    }
    func update() {
        let document = document(), (type,capitalization) = traits()
        if document.documentID != currentDocument {
            currentDocument = document.documentID; session.reset(); capsLock=false; suppressAutoCapital=false; shiftOnce=false
            session.englishMode = preferredEnglish || [.emailAddress,.URL,.asciiCapable].contains(type)
            view.setNumeric([.numberPad,.phonePad,.decimalPad,.asciiCapableNumberPad,.numbersAndPunctuation].contains(type))
        }
        let restricted = [.emailAddress,.URL,.numberPad,.phonePad,.decimalPad,.asciiCapable,.asciiCapableNumberPad].contains(type)
        session.learningAllowed = !restricted
        session.suggestionsEnabled = preferences.suggestions && !restricted
        let before = document.beforeInput ?? "", trimmed = before.trimmingCharacters(in: .whitespaces)
        switch capitalization {
        case .allCharacters: automaticCapital = true
        case .words: automaticCapital = before.isEmpty || before.last?.isWhitespace == true
        case .sentences: automaticCapital = trimmed.isEmpty || trimmed.last.map { ".!?\n".contains($0) } == true
        default: automaticCapital = false
        }
        if !preferences.configuration.autoCapitalization { automaticCapital = false }
        view.capsLocked = capsLock
        view.glideAllowed = session.glideAvailable
        // Kéo đổi chỗ phím chỉ khi có Truy cập đầy đủ, để profile tạo ra về được app.
        view.rearrangeAllowed = fullAccess()
        view.clipboardEnabled = clipboardEnabled
        view.validNextLetter = preferences.configuration.dynamicHitZones && !session.englishMode
            ? { [weak self] letter in self?.session.keepsSyllableValid(letter) ?? true } : nil
        session.refreshCandidates(in: document)
        view.update(candidates: session.candidates,english: session.englishMode,uppercase: upperNext)
    }
    private func command(_ name: String) {
        let doc = document()
        switch name {
        // Vuốt trái phím cách: xoá một ký tự; có vùng chọn trong ứng dụng thì cắt.
        case "space-left":
            if selection(in: doc) != nil { clipboardAction("cut", in: doc) } else { session.press("⌫",in: doc) }
        // Vuốt lên phím cách: có vùng chọn thì chép, không thì khoá/mở chữ hoa.
        case "space-up":
            if selection(in: doc) != nil { clipboardAction("copy", in: doc) } else { view.onCapsLock?(); return }
        case "paste": clipboardAction("paste", in: doc)
        // Hai ngón vuốt trái/phải: báo kết quả như Android.
        case "undo": view.message(session.undo(in: doc) ? "↶ Đã hoàn tác" : "Không còn gì để hoàn tác")
        case "redo": view.message(session.redo(in: doc) ? "↷ Đã làm lại" : "Không còn gì để làm lại")
        case "clear-personal":
            session.clearLearned(in: doc); store.clearPersonal()
            reloadVocabulary()
        case "remove-VI", "remove-EN":
            store.remove(name == "remove-VI" ? "vi.ngram" : "en.ngram"); reloadVocabulary()
        case "import-VI", "import-EN", "import-backup", "export-backup": files.run(name)
        default: break
        }
        update()
    }
    private func selection(in doc: TextDocument) -> String? { doc.selection.flatMap { $0.isEmpty ? nil : $0 } }
    /// Cắt/chép vùng chọn của ứng dụng, dán clipboard. iOS chỉ cho bàn phím đọc/ghi clipboard khi có Truy cập đầy đủ;
    /// không có quyền thì cắt vẫn xoá vùng chọn như trước (hoàn tác được) và báo cần quyền.
    private func clipboardAction(_ action: String, in doc: TextDocument) {
        guard fullAccess() else {
            if action == "cut" { session.press("⌫",in: doc) }
            view.message(action == "cut" ? "Đã xoá; cần Truy cập đầy đủ để cắt vào clipboard"
                         : "Cần cho phép Truy cập đầy đủ để \(action == "copy" ? "chép" : "dán")")
            return
        }
        if action == "paste" {
            guard let text = pasteboard.string, !text.isEmpty else { view.message("Clipboard trống"); return }
            session.insertText(text, in: doc); view.message("Đã dán")
            return
        }
        guard let text = selection(in: doc) else { return }
        pasteboard.string = text
        if action == "cut" { session.press("⌫",in: doc) }
        view.message(action == "cut" ? "✂ Đã cắt" : "Đã chép")
    }
}
