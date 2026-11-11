import UIKit

enum HexShape {
    static let sqrt3 = CGFloat(3).squareRoot()
    /// Khe hở giữa hai phím (pt), như Android.
    static let gap: CGFloat = 3

    /// Lục giác pointy-top của một ô, thu vào `gap` để tạo khe hở nhưng giữ nguyên tâm. `cells` > 1 (phím cách): đa giác
    /// ghép, cạnh trên răng cưa, hai cạnh bên thẳng, hai góc dưới vát — cùng hình với `HexGeometry.keyPath` của Android.
    static func path(in bounds: CGRect, cells: Int = 1) -> UIBezierPath {
        let path = UIBezierPath()
        let h = bounds.height
        if cells <= 1 {
            let cx = bounds.midX, cy = bounds.midY
            let iw = bounds.width - gap, ih = iw * 2 / sqrt3
            path.move(to: CGPoint(x: cx, y: cy - ih / 2))
            path.addLine(to: CGPoint(x: cx + iw / 2, y: cy - ih / 4))
            path.addLine(to: CGPoint(x: cx + iw / 2, y: cy + ih / 4))
            path.addLine(to: CGPoint(x: cx, y: cy + ih / 2))
            path.addLine(to: CGPoint(x: cx - iw / 2, y: cy + ih / 4))
            path.addLine(to: CGPoint(x: cx - iw / 2, y: cy - ih / 4))
            path.close()
            return path
        }
        let n = CGFloat(cells), w = bounds.width / n, g = gap / 2, ox = bounds.minX, oy = bounds.minY
        var points = [CGPoint(x: g, y: h / 4 + g)]
        for i in 0..<cells {
            points.append(CGPoint(x: (CGFloat(i) + 0.5) * w, y: g))
            points.append(CGPoint(x: i == cells - 1 ? n * w - g : CGFloat(i + 1) * w, y: h / 4 + g))
        }
        points += [CGPoint(x: n * w - g, y: h * 3 / 4), CGPoint(x: n * w - w / 2, y: h - g), CGPoint(x: w / 2, y: h - g), CGPoint(x: g, y: h * 3 / 4)]
        for (i, point) in points.enumerated() {
            let p = CGPoint(x: ox + point.x, y: oy + point.y)
            if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        path.close()
        return path
    }
}

/// Một phím vẽ bằng Core Graphics: nền theo loại phím, nhãn đậm, ký tự vuốt xuống nhỏ bên dưới (hoặc bên trên ở dialpad).
/// Tiêu đề của UIButton vẫn được đặt (màu trong suốt) để VoiceOver và test đọc được.
final class HexKeyButton: UIButton {
    /// Lục giác (một hay nhiều ô) hoặc chữ nhật bo góc, vẽ thu vào `insetX`/`insetY` để tạo khe; vùng chạm của phím chữ nhật
    /// là cả ô, không có khe chết (như `RectGeometry` của Android).
    enum Shape: Equatable { case hex(cells: Int), rect(insetX: CGFloat, insetY: CGFloat, radius: CGFloat) }
    var shape = Shape.hex(cells: 1) { didSet { setNeedsDisplay() } }
    var kind = KeyKind.consonant { didSet { setNeedsDisplay() } }
    var theme = KeyboardTheme.light { didSet { setNeedsDisplay() } }
    /// Ký tự vuốt xuống; rỗng = không có.
    var hint = "" { didSet { setNeedsDisplay() } }
    /// SF Symbol vẽ thay nhãn chính / thay ký tự vuốt xuống.
    var symbolName: String? { didSet { setNeedsDisplay() } }
    var hintSymbolName: String? { didSet { setNeedsDisplay() } }
    /// Dialpad: ký tự phụ nằm phía trên số.
    var hintOnTop = false
    /// Tô "đang nhấn" từ ngoài (cử chỉ, phím đích khi kéo).
    var pressed = false { didSet { if pressed != oldValue { setNeedsDisplay() } } }
    override var isHighlighted: Bool { didSet { setNeedsDisplay() } }

    /// Chữ nét thường cỡ lớn như phím iOS; theo Dynamic Type nhưng có trần để không tràn phím.
    static let labelFont = UIFontMetrics(forTextStyle: .body).scaledFont(for: .systemFont(ofSize: 23, weight: .regular), maximumPointSize: 28)
    static let smallFont = UIFont.systemFont(ofSize: 16, weight: .regular)
    static let hintFont = UIFont.systemFont(ofSize: 10, weight: .regular)
    static let spaceFont = UIFont.systemFont(ofSize: 15, weight: .regular)
    /// Emoji vuốt xuống vẽ nhỏ hơn chữ thường: glyph emoji cao hơn chữ cùng cỡ và sẽ lấn vào nhãn.
    static let emojiHintFont = UIFont.systemFont(ofSize: 8)

    var shapePath: UIBezierPath {
        switch shape {
        case let .hex(cells): return HexShape.path(in: bounds, cells: cells)
        case let .rect(insetX, insetY, radius): return UIBezierPath(roundedRect: bounds.insetBy(dx: insetX, dy: insetY), cornerRadius: radius)
        }
    }
    /// Khung phím thực sự được vẽ, để đặt bong bóng phóng to ngay trên mặt phím.
    var keyFrame: CGRect { shapePath.bounds }
    // The drawn polygon and its hit region must agree where neighbouring hex rows overlap.
    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        if case .hex = shape { return shapePath.contains(point) }
        return bounds.contains(point)
    }

    override func draw(_ rect: CGRect) {
        let path = shapePath
        // Bóng 1pt dưới phím: cùng hình, dịch xuống, vẽ trước mặt phím.
        if let shadow = path.copy() as? UIBezierPath {
            shadow.apply(CGAffineTransform(translationX: 0, y: 1)); theme.keyShadow.setFill(); shadow.fill()
        }
        (pressed || isHighlighted ? theme.pressedBackground(for: kind) : theme.background(for: kind)).setFill(); path.fill()
        let fg = theme.foreground(for: kind)
        let face = path.bounds
        let center = CGPoint(x: face.midX, y: face.midY)
        let title = self.title(for: .normal) ?? ""
        if kind == .space {
            draw(text: title, font: Self.spaceFont, color: theme.hintFg, at: center); return
        }
        let corner: Bool
        if case .rect = shape, !hintOnTop { corner = true } else { corner = false }
        let hasHint = !hint.isEmpty || hintSymbolName != nil
        let emojiHint = !hint.allSatisfy(\.isASCII) && hint.unicodeScalars.contains { $0.properties.isEmoji }
        // Phím chữ nhật: ký tự phụ nhỏ ở góc dưới bên phải, nhãn nhích lên. Lục giác: ký tự phụ dưới nhãn.
        // Dialpad: ký tự phụ nằm trên số.
        let labelCenter = CGPoint(x: center.x, y: center.y + (hasHint ? (corner ? -3 : hintOnTop ? 6 : -5) : 0))
        if let symbolName {
            draw(symbol: symbolName, size: 19, color: fg, at: labelCenter)
        } else {
            let font = title.count > 1 && kind != .consonant && kind != .symbol && kind != .vowel && kind != .tone ? Self.smallFont : Self.labelFont
            draw(text: title, font: font, color: fg, at: labelCenter)
        }
        let hintPoint: CGPoint
        if corner { hintPoint = CGPoint(x: face.maxX - 6, y: face.maxY - 7) }
        else if hintOnTop { hintPoint = CGPoint(x: center.x, y: max(face.minY + 9, labelCenter.y - 19)) }
        // Emoji dưới nhãn lục giác đặt thấp hơn để không chạm phần chân chữ (g, j, p, q, y).
        else { hintPoint = CGPoint(x: center.x, y: labelCenter.y + (emojiHint ? 19 : 17)) }
        if let hintSymbolName {
            draw(symbol: hintSymbolName, size: 10, color: theme.hintFg, at: hintPoint)
        } else if !hint.isEmpty {
            draw(text: hint, font: emojiHint ? Self.emojiHintFont : Self.hintFont, color: theme.hintFg, at: hintPoint)
        }
    }
    private func draw(text: String, font: UIFont, color: UIColor, at point: CGPoint) {
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let size = (text as NSString).size(withAttributes: attributes)
        (text as NSString).draw(at: CGPoint(x: point.x - size.width / 2, y: point.y - size.height / 2), withAttributes: attributes)
    }
    private func draw(symbol: String, size: CGFloat, color: UIColor, at point: CGPoint) {
        let configuration = UIImage.SymbolConfiguration(pointSize: size, weight: .regular)
        guard let image = UIImage(systemName: symbol, withConfiguration: configuration)?.withTintColor(color, renderingMode: .alwaysOriginal) else { return }
        image.draw(in: CGRect(x: point.x - image.size.width / 2, y: point.y - image.size.height / 2, width: image.size.width, height: image.size.height))
    }
}

/// Nút viên thuốc trên thanh gợi ý và trong các bảng: đổi nền khi nhấn.
class PillButton: UIButton {
    var normalColor = UIColor.clear { didSet { backgroundColor = normalColor } }
    var pressedColor = UIColor.clear
    override var isHighlighted: Bool { didSet { backgroundColor = isHighlighted ? pressedColor : normalColor } }
}

/// Một ô trong danh sách nhóm kiểu app Cài đặt: chữ căn trái, phụ kiện (›, ✓) căn phải.
final class ListRowButton: PillButton {
    let accessory = UIImageView()
    let detail = UILabel()
    override init(frame: CGRect) {
        super.init(frame: frame)
        accessory.contentMode = .center; accessory.isUserInteractionEnabled = false; addSubview(accessory)
        detail.font = .systemFont(ofSize: 15); detail.textAlignment = .right; detail.isUserInteractionEnabled = false; addSubview(detail)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }
    override func layoutSubviews() {
        super.layoutSubviews()
        let size: CGFloat = accessory.image == nil ? 0 : 20
        accessory.frame = CGRect(x: bounds.width - 14 - size, y: (bounds.height - 20) / 2, width: size, height: 20)
        let detailWidth = detail.text?.isEmpty == false ? min(bounds.width * 0.45, detail.intrinsicContentSize.width) : 0
        detail.frame = CGRect(x: accessory.frame.minX - (size > 0 ? 6 : 0) - detailWidth, y: 0, width: detailWidth, height: bounds.height)
    }
}

/// Một ô của trình soạn: tên ô nhỏ phía trên, nội dung cỡ lớn bên dưới (cắt đầu khi dài để luôn thấy chữ vừa gõ).
final class EditorFieldButton: PillButton {
    let caption = UILabel(), value = UILabel()
    override init(frame: CGRect) {
        super.init(frame: frame)
        caption.font = .systemFont(ofSize: 11); value.font = .systemFont(ofSize: 17); value.lineBreakMode = .byTruncatingHead
        [caption, value].forEach { $0.isUserInteractionEnabled = false; addSubview($0) }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }
    override func layoutSubviews() {
        super.layoutSubviews()
        let inner = bounds.insetBy(dx: 10, dy: 3)
        caption.frame = CGRect(x: inner.minX, y: inner.minY, width: inner.width, height: 14)
        value.frame = CGRect(x: inner.minX, y: caption.frame.maxY, width: inner.width, height: inner.maxY - caption.frame.maxY)
    }
}

/// Bong bóng phóng to ký tự đang nhấn, nổi trên phím như bàn phím iOS. Không nhận chạm.
final class KeyPopup: UIView {
    let label = UILabel()
    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false; layer.cornerRadius = 9
        layer.shadowColor = UIColor.black.cgColor; layer.shadowOpacity = 0.3; layer.shadowRadius = 3; layer.shadowOffset = CGSize(width: 0, height: 1)
        label.font = .systemFont(ofSize: 34, weight: .regular); label.textAlignment = .center; label.adjustsFontSizeToFitWidth = true
        label.minimumScaleFactor = 0.5; addSubview(label)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }
    override func layoutSubviews() { super.layoutSubviews(); label.frame = bounds.insetBy(dx: 2, dy: 2) }
}

/// Bảng cuộn của cài đặt / emoji: kéo bắt đầu trên một hàng thì cuộn chứ không thành chạm vào hàng đó. Mặc định
/// UIScrollView không huỷ chạm đang theo dõi trên UIControl. Công tắc và thanh trượt vẫn giữ cử chỉ của chúng.
final class PanelScrollView: UIScrollView {
    override func touchesShouldCancel(in view: UIView) -> Bool { view is UIButton || super.touchesShouldCancel(in: view) }
}

/// Nhãn có lề trong, dùng cho toast.
final class PaddedLabel: UILabel {
    var insets = UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16)
    override func drawText(in rect: CGRect) { super.drawText(in: rect.inset(by: insets)) }
    override var intrinsicContentSize: CGSize { padded(super.intrinsicContentSize) }
    override func sizeThatFits(_ size: CGSize) -> CGSize {
        padded(super.sizeThatFits(CGSize(width: size.width - insets.left - insets.right, height: size.height)))
    }
    private func padded(_ size: CGSize) -> CGSize { CGSize(width: size.width + insets.left + insets.right, height: size.height + insets.top + insets.bottom) }
}

final class HexKeyboardView: UIView {
    var onKey: ((String) -> Void)?
    var onCandidate: ((String) -> Void)?
    var onForgetCandidate: ((String) -> Void)?
    var onNextKeyboard: (() -> Void)?
    var onToggleLanguage: (() -> Void)?
    var onCapsLock: (() -> Void)?
    var onPreferencesChanged: (() -> Void)?
    var onCommand: ((String) -> Void)?
    var onGlide: ((String, String) -> Void)?
    /// Host cho gõ vuốt ở ô hiện tại (VI hay EN; không ở email/URL/ô số hay khi tắt gợi ý).
    var glideAllowed = true
    /// Giữ kéo đổi chỗ phím chỉ chạy khi có Truy cập đầy đủ (host đặt).
    var rearrangeAllowed = false
    /// Lịch sử clipboard đang bật (có Truy cập đầy đủ và người dùng bật trong app).
    var clipboardEnabled = false
    weak var clipboard: ClipboardHistory?
    /// Chèn nguyên văn vào ô nhập (dán từ lịch sử clipboard).
    var onInsertText: ((String) -> Void)?
    var onCursor: ((Int, Int) -> Void)?
    var onWordDelete: ((Int, Bool) -> Void)?
    var onShift: (() -> Void)?
    /// Bàn phím cần cao thêm (`extraHeight` đổi): host cập nhật ràng buộc chiều cao.
    var onHeightChange: (() -> Void)?
    /// Chỉ hiện 🌐 khi iOS yêu cầu (`needsInputModeSwitchKey`, máy có nút Home); máy Face ID đã có nút chuyển bàn phím
    /// của hệ thống bên dưới bàn phím.
    var showsGlobe = false { didSet { globe.isHidden = !showsGlobe || visiblePanel != nil; setNeedsLayout() } }
    var emoji: EmojiCatalog?
    let preferences: KeyboardPreferences
    private(set) var theme = KeyboardTheme.light
    let candidateScroll = UIScrollView(), candidateStack = UIStackView()
    let mode = PillButton(type: .custom), backspace = PillButton(type: .custom), more = PillButton(type: .custom), globe = PillButton(type: .custom)
    /// Thanh trên của bảng: "‹ Cài đặt" quay về bảng cha, tiêu đề ở giữa, "Xong" (`close-settings`) về bàn phím.
    let panelBack = UIButton(type: .system), panelUp = UIButton(type: .system), panelTitle = UILabel()
    let panel = PanelScrollView(), panelStack = UIStackView(), banner = PaddedLabel(), editorBar = UIStackView()
    let traceLayer = CAShapeLayer()
    let popup = KeyPopup()
    let touchGesture = KeyboardGesture()
    /// Nhóm ô đang dựng trong bảng cài đặt; `note` hay `section` mới thì đóng nhóm.
    var currentSection: UIStackView?
    /// Ràng buộc bề rộng tối thiểu của từng ô trên thanh gợi ý; `layoutSubviews` chia đều thanh cho `slotCount` ô.
    var slotWidths: [NSLayoutConstraint] = [], slotCount = 3
    var keys: [(HexKeyButton, Int, String)] = []
    var currentProfile: KeyboardProfile?
    var page = -1, dialpad = false, uppercase = false, english = false
    var suggestions: [String] = []
    var capsLocked = false
    var visiblePanel: String?
    var editLayer: Int?
    var selectedCell: (Int, Int)?
    var cursorStep = CGPoint.zero, deletingWords = false
    var dragged: UILabel?
    var pressedIndex: Int?, hoverIndex: Int?
    var repeatTimer: Timer?
    var bannerTask: DispatchWorkItem?
    var query = "", category: String?
    var emojiPage = 0, snippetPage = 0
    var inlineDocuments: [EditorDocument] = []
    var inlineNames: [String] = [], inlineAscii: [Bool] = []
    var inlineIndex = 0
    /// Tiêu đề trình soạn (vd. "Gõ tắt"), hiện giữa thanh trên.
    var inlineTitle = ""
    var inlineSession = TypingSession()
    var inlineSave: (([String]) -> Void)?
    var inlineCancel: (() -> Void)?
    struct InlineEditorState {
        let title: String, documents: [EditorDocument], names: [String], ascii: [Bool], index: Int
        let session: TypingSession
        let save: (([String]) -> Void)?
        let cancel: (() -> Void)?
    }
    var emojiReturnEditor: InlineEditorState?
    /// Trình sửa lớp phím (trong app) bấm "Xong".
    var onLayoutEditorDone: (() -> Void)?

    /// Kích thước chung với Android: lề 8, khe 3, thanh gợi ý 52, lề trên/dưới lưới 6.
    static let barHeight: CGFloat = 52, pad: CGFloat = 8, gridTop: CGFloat = 6, gridBottom: CGFloat = 6
    /// Hàng ô nhập của trình soạn (gõ tắt, tên profile, tìm emoji) nằm dưới thanh trên; bàn phím cao thêm đúng chừng đó
    /// để lưới phím giữ nguyên cỡ.
    static let editorRowHeight: CGFloat = 50
    var extraHeight: CGFloat { inlineDocuments.isEmpty ? 0 : Self.editorRowHeight }
    /// Những gì quyết định chiều cao bàn phím; đổi thì báo `onHeightChange`.
    private var reportedHeightKey = ""
    /// Dialpad 4×4 như Android: cột cuối là `-`, cách, ⌫, ↵.
    static let dialpadKeys = ["1","2","3","-", "4","5","6"," ", "7","8","9","⌫", "ABC","0",".","\n"]
    static let dialpadHints = ["1":"(","2":")","3":"/","4":"*","5":"#","6":"-","7":";","8":":","9":"=","0":"+",".":","]

    init(preferences: KeyboardPreferences) {
        self.preferences = preferences
        super.init(frame: .zero)
        candidateScroll.showsHorizontalScrollIndicator = false; candidateStack.spacing = 0; candidateStack.alignment = .center
        candidateScroll.addSubview(candidateStack); addSubview(candidateScroll)
        configure(mode, "VI", "language-mode") { [weak self] in
            guard let self else { return }
            if !self.inlineDocuments.isEmpty { self.inlineSession.englishMode.toggle(); self.updateEditorBar() }
            else { self.onToggleLanguage?() }
        }
        mode.titleLabel?.font = .systemFont(ofSize: 15, weight: .bold); mode.layer.cornerRadius = 17
        mode.accessibilityHint = "Chuyển giữa tiếng Việt và tiếng Anh"
        configure(globe, "", "next-keyboard") { [weak self] in self?.onNextKeyboard?() }
        globe.setImage(UIImage(systemName: "globe", withConfiguration: UIImage.SymbolConfiguration(pointSize: 20, weight: .regular)), for: .normal)
        globe.accessibilityLabel = "Bàn phím tiếp theo"; globe.layer.cornerRadius = 17; globe.isHidden = true
        configure(backspace, "", "backspace") { [weak self] in self?.type("⌫") }
        backspace.setImage(UIImage(systemName: "delete.left", withConfiguration: UIImage.SymbolConfiguration(pointSize: 20, weight: .regular)), for: .normal)
        backspace.accessibilityLabel = "Xoá"; backspace.layer.cornerRadius = 17
        configure(more, "", "more-candidates") { [weak self] in self?.showCandidates() }
        more.setImage(UIImage(systemName: "chevron.down", withConfiguration: UIImage.SymbolConfiguration(pointSize: 16, weight: .medium)), for: .normal)
        more.accessibilityLabel = "Xem thêm gợi ý"; more.layer.cornerRadius = 17
        [mode,globe,backspace,more].forEach { addSubview($0) }
        let repeatPress = UILongPressGestureRecognizer(target: self, action: #selector(repeatDelete(_:)))
        repeatPress.minimumPressDuration = 0.35; backspace.addGestureRecognizer(repeatPress)
        panel.accessibilityIdentifier = "keyboard-panel"
        configure(panelBack,"Xong","close-settings") { [weak self] in
            guard let self else { return }
            if self.visiblePanel == "emoji" { self.returnFromEmoji() }
            else { self.closePanel() }
        }
        panelBack.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold); panelBack.contentHorizontalAlignment = .trailing
        panelBack.accessibilityLabel = "Xong, về bàn phím"
        addSubview(panelBack); panelBack.isHidden = true
        configure(panelUp,"","panel-back") { [weak self] in
            guard let self, let name = self.visiblePanel, let parent = Self.panelParents[name] else { return }
            self.show(parent)
        }
        panelUp.setImage(UIImage(systemName: "chevron.left", withConfiguration: UIImage.SymbolConfiguration(pointSize: 17, weight: .semibold)), for: .normal)
        panelUp.titleLabel?.font = .systemFont(ofSize: 17); panelUp.contentHorizontalAlignment = .leading
        panelUp.titleEdgeInsets = UIEdgeInsets(top: 0, left: 4, bottom: 0, right: -4)
        addSubview(panelUp); panelUp.isHidden = true
        panelTitle.font = .systemFont(ofSize: 17, weight: .semibold); panelTitle.textAlignment = .center
        panelTitle.adjustsFontSizeToFitWidth = true; panelTitle.minimumScaleFactor = 0.8; panelTitle.accessibilityTraits = .header
        addSubview(panelTitle); panelTitle.isHidden = true
        panelStack.axis = .vertical; panelStack.spacing = 8
        panel.addSubview(panelStack); addSubview(panel); panel.isHidden = true
        banner.font = .systemFont(ofSize: 15, weight: .medium); banner.textAlignment = .center; banner.numberOfLines = 2
        banner.layer.cornerRadius = 20; banner.clipsToBounds = true
        banner.isUserInteractionEnabled = false; banner.isHidden = true; addSubview(banner)
        editorBar.spacing = 6; editorBar.distribution = .fillEqually; addSubview(editorBar); editorBar.isHidden = true
        traceLayer.fillColor = nil; traceLayer.lineWidth = 5; traceLayer.lineCap = .round; traceLayer.lineJoin = .round; layer.addSublayer(traceLayer)
        // Phím được thêm sau nên nằm trên lớp nét vuốt; nâng nét vuốt lên trên phím, bong bóng/thông báo/phím đang kéo trên cùng.
        traceLayer.zPosition = 10
        popup.isHidden = true; addSubview(popup)
        popup.layer.zPosition = 20; banner.layer.zPosition = 20
        let gesture = touchGesture
        gesture.keyAt = { [weak self] point in self?.cell(at: point) }
        gesture.trackAt = { [weak self] point in self?.cell(at: point, dynamic: false) }
        gesture.holdDelay = { [weak self] index in
            guard let self, let code = self.code(at: index) else { return 0.8 }
            if self.isBackspace(code) { return 0.4 } // giữ ⌫ để xoá liên tục, như Android
            if self.dialpad { return 0.8 }
            return code == " " ? self.preferences.configuration.cursorDelay : self.preferences.configuration.holdDelay
        }
        gesture.emit = { [weak self] event in self?.handle(event) }
        addGestureRecognizer(gesture)
        for direction: UISwipeGestureRecognizer.Direction in [.left, .right] {
            let swipe = UISwipeGestureRecognizer(target: self, action: #selector(twoFinger(_:)))
            swipe.numberOfTouchesRequired = 2; swipe.direction = direction; addGestureRecognizer(swipe)
        }
        applyTheme()
        reloadKeys()
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }
    deinit { repeatTimer?.invalidate() }
    /// Bước hàng phím chữ nhật = 1,44 bề rộng ô, trần 60pt để màn ngang không quá cao (như Android).
    static let rectRowRatio: CGFloat = 1.44, rectMaxRow: CGFloat = 60
    /// Trần bề rộng ô tổ ong: iPhone dọc lớn nhất có ô 56,5pt nên không đổi; iPad giữ ô cỡ iPhone, lưới nằm giữa thay vì
    /// lục giác to nửa màn hình mà chữ vẫn nhỏ. Bố cục chữ nhật vẫn trải hết bề ngang như bàn phím iPad.
    static let hexMaxKey: CGFloat = 60
    static func height(for width: CGFloat, screenHeight: CGFloat, spec: GridSpec = .hex7) -> CGFloat {
        let fullWidth: CGFloat = max(0,width - 2 * pad) / spec.span
        let keyWidth: CGFloat = spec.isHex ? min(fullWidth, hexMaxKey) : fullWidth
        let grid: CGFloat = spec.isHex ? keyWidth * 8 / HexShape.sqrt3 : CGFloat(spec.rowSizes.count) * min(keyWidth * rectRowRatio, rectMaxRow)
        return min(barHeight + gridTop + grid + gridBottom,max(200,screenHeight * 0.5))
    }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        if previousTraitCollection?.userInterfaceStyle != traitCollection.userInterfaceStyle { applyTheme() }
    }
    /// Tính lại bảng màu theo Cài đặt và chế độ sáng/tối của hệ thống; tô lại mọi phần đang hiển thị.
    func applyTheme() {
        let config = preferences.configuration
        overrideUserInterfaceStyle = config.theme == 1 ? .light : config.theme == 2 ? .dark : .unspecified
        let systemDark = (overrideUserInterfaceStyle == .unspecified ? traitCollection : UITraitCollection(userInterfaceStyle: overrideUserInterfaceStyle)).userInterfaceStyle == .dark
        let resolved = KeyboardTheme.resolve(theme: config.theme, accent: config.accent, systemDark: systemDark)
        let changed = resolved != theme
        theme = resolved
        backgroundColor = theme.background
        tintColor = theme.accent
        for button in [backspace, more, globe] {
            button.tintColor = theme.keyFg; button.setTitleColor(theme.keyFg, for: .normal); button.pressedColor = barPressed
        }
        mode.normalColor = .clear; mode.pressedColor = barPressed; mode.setTitleColor(theme.accent, for: .normal)
        panelBack.setTitleColor(theme.accent, for: .normal); panelUp.tintColor = theme.accent; panelUp.setTitleColor(theme.accent, for: .normal)
        panelTitle.textColor = theme.keyFg
        banner.backgroundColor = theme.toastBg; banner.textColor = theme.toastFg
        traceLayer.strokeColor = theme.accent.withAlphaComponent(0.7).cgColor
        popup.backgroundColor = theme.keyBg; popup.label.textColor = theme.keyFg
        keys.forEach { $0.0.theme = theme }
        for view in candidateStack.arrangedSubviews {
            if let chip = view as? PillButton { chip.pressedColor = barPressed; chip.setTitleColor(theme.keyFg, for: .normal); chip.tintColor = theme.keyFg }
            else { view.backgroundColor = theme.separator }
        }
        if changed, visiblePanel != nil || !inlineDocuments.isEmpty || editLayer != nil {
            if let visiblePanel { show(visiblePanel) }
            if !inlineDocuments.isEmpty { updateEditorBar() }
            if let editLayer { startLayoutEditor(editLayer) }
        }
    }
    /// Nền khi nhấn một nút nằm thẳng trên nền bàn phím (thanh gợi ý, thanh trên của bảng).
    var barPressed: UIColor { theme.keyFg.withAlphaComponent(0.12) }
    func style(chip: PillButton, selected: Bool) {
        chip.normalColor = selected ? theme.accent : theme.cellBg; chip.pressedColor = selected ? theme.accent.withAlphaComponent(0.8) : theme.cellPressedBg
        chip.setTitleColor(selected ? theme.accentFg : theme.keyFg, for: .normal)
    }
    func update(candidates: [String], english: Bool, uppercase: Bool) {
        self.english = english; self.uppercase = uppercase; suggestions = candidates
        mode.setTitle((inlineDocuments.isEmpty ? english : inlineSession.englishMode) ? "EN" : "VI", for: .normal)
        if currentProfile != preferences.profile { reloadKeys() }
        applyTheme()
        for (button, index, code) in keys { decorate(button, code: code, index: index) }
        candidateStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        slotWidths.removeAll()
        let words = Array(candidates.prefix(6))
        if words.isEmpty {
            buildToolbar()
        } else {
            // Gợi ý là chữ trơn chia đều ba ô, ngăn bằng vạch mảnh; gợi ý đầu tiên đậm hơn.
            slotCount = min(3, words.count)
            for (position, word) in words.enumerated() {
                if position > 0 { addDivider() }
                let chip = slotButton(id: "candidate-\(word)") { [weak self] in self?.onCandidate?(word) }
                chip.setTitle(word, for: .normal)
                chip.titleLabel?.font = .systemFont(ofSize: 17, weight: position == 0 ? .semibold : .regular)
                chip.titleLabel?.lineBreakMode = .byTruncatingMiddle
                chip.addGestureRecognizer(UILongPressGestureRecognizer(target: self, action: #selector(forgetCandidate(_:))))
            }
        }
        more.isHidden = candidates.isEmpty || visiblePanel != nil || !inlineDocuments.isEmpty
        setNeedsLayout()
    }
    /// Một ô trên thanh gợi ý: nền trong, bo góc khi nhấn, rộng tối thiểu một phần `slotCount` của thanh.
    @discardableResult func slotButton(id: String, action: @escaping () -> Void) -> PillButton {
        let chip = PillButton(type: .custom)
        configure(chip, "", id, action)
        chip.layer.cornerRadius = 8; chip.pressedColor = barPressed; chip.tintColor = theme.keyFg
        chip.setTitleColor(theme.keyFg, for: .normal)
        chip.contentEdgeInsets = UIEdgeInsets(top: 0, left: 8, bottom: 0, right: 8)
        chip.heightAnchor.constraint(equalToConstant: 38).isActive = true
        let width = chip.widthAnchor.constraint(greaterThanOrEqualToConstant: 44); width.isActive = true; slotWidths.append(width)
        candidateStack.addArrangedSubview(chip)
        return chip
    }
    func addDivider() {
        let line = UIView(); line.backgroundColor = theme.separator; line.isUserInteractionEnabled = false
        line.widthAnchor.constraint(equalToConstant: 1 / max(1, UIScreen.main.scale)).isActive = true
        line.heightAnchor.constraint(equalToConstant: 20).isActive = true
        candidateStack.addArrangedSubview(line)
    }
    /// Chưa có gợi ý: thanh hiện lối tắt emoji, clipboard (khi bật) và cài đặt nhanh thay vì để trống.
    func buildToolbar() {
        var items: [(String, String, String, (HexKeyboardView) -> Void)] = [("face.smiling", "Emoji", "emoji", { $0.openEmoji() })]
        if clipboardEnabled { items.append(("doc.on.clipboard", "Clipboard", "clipboard", { $0.show("clipboard") })) }
        items.append(("gearshape", "Cài đặt nhanh", "settings", { $0.show("settings") }))
        slotCount = items.count
        for (symbol, name, id, action) in items {
            let button = slotButton(id: "toolbar-\(id)") { [weak self] in if let self { action(self) } }
            button.setImage(UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 19, weight: .regular)), for: .normal)
            button.accessibilityLabel = name
        }
    }
    func refreshPanel() { if let visiblePanel { show(visiblePanel) } }
    func setNumeric(_ value: Bool) { if value { dialpad = true; page = -1 } else { dialpad = false }; reloadKeys() }
    func reloadKeys() {
        // Không để chạm đang dở của lớp/bố cục cũ chạy trên phím mới (như Android).
        cancelTouches()
        keys.forEach { $0.0.removeFromSuperview() }; keys.removeAll()
        currentProfile = preferences.profile
        let profile = preferences.profile
        if dialpad {
            for (i, value) in Self.dialpadKeys.enumerated() { addKey(value, index: i) }
        } else {
            for (index, code) in profile.letters.enumerated() where !code.isEmpty { addKey(code, index: index) }
        }
        keys.forEach { $0.0.isHidden = visiblePanel != nil && editLayer == nil }
        setNeedsLayout()
    }
    func code(at index: Int) -> String? {
        if dialpad { return Self.dialpadKeys.indices.contains(index) ? Self.dialpadKeys[index] : nil }
        return preferences.profile.letters.indices.contains(index) ? preferences.profile.letters[index] : nil
    }
    func isBackspace(_ code: String) -> Bool { code == "#backspace" || (dialpad && code == "⌫") }
    /// Giá trị lớp 123 hiện thay phím ở ô này, như `SymbolPages.render` của Android: ô chữ luôn lấy giá trị của trang;
    /// ô ⚙ ⇧ `,` `.` chỉ lấy khi trang có giá trị, trống thì giữ phím của lớp chữ.
    func symbolValue(_ code: String, index: Int) -> String? {
        guard page >= 0, editLayer == nil, !dialpad, KeyboardProfile.symbolUsable(code) else { return nil }
        let value = preferences.profile.symbols[page][index]
        return KeyboardProfile.isLetter(code) || !value.isEmpty ? value : nil
    }
    func pageLabel(_ value: String, page: Int) -> String { value == "#page" ? "\(page + 1)/2" : value }
    /// Nhãn của ô trong trình sửa lớp vuốt xuống / 123; nil = ô đó hiện phím của lớp chữ.
    func editValue(_ code: String, index: Int) -> String? {
        guard let editLayer else { return nil }
        if editLayer == 1, KeyboardProfile.swipeUsable(code) { let value = preferences.profile.swipe[index]; return value.isEmpty ? "·" : value }
        if editLayer >= 2, KeyboardProfile.symbolUsable(code) {
            let value = preferences.profile.symbols[editLayer - 2][index]
            return value.isEmpty ? "·" : pageLabel(value, page: editLayer - 2)
        }
        return nil
    }
    /// Phím dấu Telex: s f r x j z, thêm l khi "L cũng là dấu huyền".
    var toneKeys: String { "sfrxjz" + (preferences.configuration.lAsGrave ? "l" : "") }
    func kind(for code: String, index: Int) -> KeyKind {
        if dialpad {
            switch code {
            case " ": return .space
            case "\n": return .enter
            case "-", "⌫", "ABC": return .function
            default: return .consonant
            }
        }
        if let editLayer, editLayer >= 2, KeyboardProfile.symbolUsable(code) { return preferences.profile.symbols[editLayer - 2][index] == "#page" ? .function : .symbol }
        if let value = symbolValue(code, index: index) { return value == "#page" ? .function : .symbol }
        switch code {
        case " ": return .space
        case "\n": return .enter
        // ⇧ như iOS: đang bật (hoa một lần hoặc khoá hoa) thì phím sáng lên, icon đặc.
        case "#shift": return capsLocked || (uppercase && inlineDocuments.isEmpty) ? .consonant : .function
        default:
            guard KeyboardProfile.isLetter(code) else { return .function }
            if toneKeys.contains(code) { return .tone }
            return "aeiouyw".contains(code) ? .vowel : .consonant
        }
    }
    func addKey(_ code: String, index: Int) {
        let button = HexKeyButton(type: .custom)
        configure(button, label(code, index: index), "key-\(code)") { [weak self] in self?.activate(code, index: index) }
        button.setTitleColor(.clear, for: .normal); button.setTitleColor(.clear, for: .highlighted)
        button.theme = theme
        let spec = preferences.profile.spec
        if dialpad { button.shape = .rect(insetX: 0, insetY: 0, radius: 12); button.hintOnTop = true }
        else if spec.isHex { button.shape = .hex(cells: code == " " ? spec.spaceCells : 1) }
        // Khe ngang ~2,7pt, dọc ~5,4pt, bo 6pt như phím chữ nhật của Android.
        else { button.shape = .rect(insetX: HexShape.gap * 0.9, insetY: HexShape.gap * 1.8, radius: HexShape.gap * 2) }
        decorate(button, code: code, index: index)
        button.accessibilityTraits.insert(.keyboardKey)
        if code == "#shift" { button.accessibilityLabel = "Chữ hoa" }
        if code == " " && !dialpad { button.accessibilityLabel = "Dấu cách" }
        if isBackspace(code) { button.accessibilityLabel = "Xoá" }
        if KeyboardProfile.isLetter(code), !dialpad, page < 0, editLayer == nil {
            button.accessibilityCustomActions = [UIAccessibilityCustomAction(name: "Chữ hoa/thường") { [weak self] _ in
                self?.type(code.uppercased()); return true
            }]
        }
        addSubview(button); keys.append((button,index,code))
    }
    /// Nhãn, loại phím, ký tự vuốt xuống và icon của một phím theo lớp và trạng thái hiện tại.
    func decorate(_ button: HexKeyButton, code: String, index: Int) {
        button.setTitle(label(code, index: index), for: .normal)
        button.kind = kind(for: code, index: index)
        button.hint = ""; button.symbolName = nil; button.hintSymbolName = nil
        if dialpad {
            button.hint = Self.dialpadHints[code] ?? ""
            button.symbolName = code == "⌫" ? "delete.left" : code == "\n" ? "return" : nil
            return
        }
        if editValue(code, index: index) != nil || symbolValue(code, index: index) != nil { return }
        if editLayer == nil && page < 0 && KeyboardProfile.swipeUsable(code) { button.hint = preferences.profile.swipe[index] }
        switch code {
        case "#settings":
            button.symbolName = "gearshape.fill"
            // Như Android: icon clipboard ở chỗ ký tự vuốt xuống, báo vuốt xuống ⚙ mở lịch sử clipboard.
            if clipboardEnabled && editLayer == nil { button.hintSymbolName = "doc.on.clipboard" }
        case "#emoji": button.symbolName = page < 0 ? "face.smiling" : nil
        case "#globe": button.symbolName = "globe"
        case "#shift": button.symbolName = capsLocked ? "capslock.fill" : uppercase && inlineDocuments.isEmpty ? "shift.fill" : "shift"
        case "#backspace": button.symbolName = "delete.left"
        case "\n": button.symbolName = "return"
        default: break
        }
    }
    func label(_ code: String, index: Int) -> String {
        if dialpad { return code == "\n" ? "↵" : code == " " ? "cách" : code }
        if let value = editValue(code, index: index) { return value }
        if let value = symbolValue(code, index: index) { return pageLabel(value, page: page) }
        switch code {
        case "#settings": return "⚙"
        case "#symbols": return page >= 0 ? "ABC" : "123"
        case "#emoji": return page >= 0 ? "1234" : "☺"
        case "#globe": return "🌐"
        case "#shift": return "⇧"
        case "#backspace": return "⌫"
        case " ": return capsLocked ? "⇪ HOA" : english ? "English" : "Tiếng Việt"
        case "\n": return "↵"
        default: return uppercase && inlineDocuments.isEmpty ? code.uppercased() : code
        }
    }
    func activate(_ code: String, index: Int) {
        if let layer = editLayer { editCell(index, layer: layer); return }
        if dialpad {
            if code == "ABC" { dialpad = false; reloadKeys() } else { type(code) }; return
        }
        if let value = symbolValue(code, index: index) {
            if value == "#page" { page = 1 - page; reloadKeys() } else if !value.isEmpty { type(value) }; return
        }
        switch code {
        case "#settings": show("settings")
        case "#symbols": page = page < 0 ? 0 : -1; reloadKeys()
        case "#emoji": if page >= 0 { dialpad = true; reloadKeys() } else { openEmoji() }
        case "#globe": onNextKeyboard?()
        case "#shift": if inlineDocuments.isEmpty { onShift?() }
        case "#backspace": type("⌫")
        default: type(uppercase && inlineDocuments.isEmpty ? code.uppercased() : code)
        }
    }
    func type(_ value: String) {
        if inlineDocuments.isEmpty && emojiReturnEditor != nil {
            returnFromEmoji(inserting: value); return
        }
        if !inlineDocuments.isEmpty {
            if value == "\n" {
                if inlineIndex + 1 < inlineDocuments.count { inlineIndex += 1; inlineSession.reset(); inlineSession.englishMode = inlineAscii[inlineIndex]; updateEditorBar() }
                else if let save = inlineSave { let values = inlineDocuments.map(\.text); closePanel(); save(values) }; return
            }
            guard value == "⌫" || inlineDocuments[inlineIndex].text.count < 2000 else { message("Nội dung quá dài"); return }
            inlineSession.press(value, in: inlineDocuments[inlineIndex]); updateEditorBar()
        } else { onKey?(value) }
    }
    func configure(_ button: UIButton, _ title: String, _ id: String, _ action: @escaping () -> Void) {
        button.setTitle(title, for: .normal); button.setTitleColor(theme.keyFg, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 16); button.accessibilityIdentifier = id
        button.addAction(UIAction { _ in action() }, for: .touchUpInside)
    }
    /// Kéo phím cách sang trái quá 1,2 bước thì xoá theo từ, mỗi bước thêm một từ (như Android).
    var wordThreshold: CGFloat { CGFloat(preferences.configuration.stepSize) * 1.2 }
    func wordCount(_ dx: CGFloat) -> Int {
        -dx < wordThreshold ? 0 : Int((-dx - wordThreshold) / CGFloat(preferences.configuration.stepSize)) + 1
    }
    /// Ô đang hiển thị dưới điểm chạm, không bù độ lệch học được (dùng khi kéo đổi chỗ và khi nhấc tay).
    func displayedCell(at point: CGPoint) -> Int? {
        keys.first(where: { !$0.0.isHidden && $0.0.point(inside: $0.0.convert(point, from: self), with: nil) })?.1
    }
    /// `dynamic`: áp vùng chạm động (chỉ cho phím lúc chạm; các điểm trên đường vuốt dùng cách chọn phím thường, như Android).
    func cell(at point: CGPoint, dynamic: Bool = true) -> Int? {
        guard visiblePanel == nil || editLayer != nil else { return nil }
        let hit = keys.first(where: { !$0.0.isHidden && $0.0.point(inside: $0.0.convert(point, from: self), with: nil) })
        // Độ lệch chạm học được chỉ dời vùng giữa các phím chữ: phím chức năng, dấu câu, cách luôn theo hình vẽ, và chạm rõ
        // giữa phím chữ (trong 1/4 bề rộng quanh tâm) gõ đúng nhãn. Như Android.
        if preferences.configuration.touchAdaptation && !dialpad && editLayer == nil && hit.map({ KeyboardProfile.isLetter($0.2) }) != false {
            let central = hit.map { hypot(point.x - $0.0.center.x, point.y - $0.0.center.y) <= $0.0.bounds.width * 0.25 } ?? false
            if !central, let match = keys.first(where: { key in
                guard !key.0.isHidden, KeyboardProfile.isLetter(key.2) else { return false }
                let offset = preferences.configuration.offsets[preferences.profile.id + ":" + key.2] ?? [0,0]
                let local = key.0.convert(point,from: self)
                return key.0.point(inside: CGPoint(x: local.x - offset[0] * key.0.bounds.width,y: local.y - offset[1] * key.0.bounds.height),with: nil)
            }) { return dynamic ? dynamicZone(match.1, at: point) : match.1 }
        }
        if let hit { return dynamic ? dynamicZone(hit.1, at: point) : hit.1 }
        // Lưới chữ nhật: khoảng trống cạnh ⇧ / ⌫ nhận phím gần nhất (khoảng cách Chebyshev theo nửa ô), như Android.
        guard !dialpad, !preferences.profile.spec.isHex else { return nil }
        func distance(_ frame: CGRect) -> CGFloat { max(abs(point.x - frame.midX) / max(1, frame.width / 2), abs(point.y - frame.midY) / max(1, frame.height / 2)) }
        let nearest = keys.filter { !$0.0.isHidden }.min { distance($0.0.frame) < distance($1.0.frame) }
        return nearest.flatMap { distance($0.0.frame) <= 2 ? $0.1 : nil }
    }
    /// Vùng chạm động (host đặt khi bật): phím chữ đó có giữ âm tiết đang gõ hợp lệ không.
    var validNextLetter: ((String) -> Bool)?
    /// Như Android: chạm không rõ giữa phím, hai phím chữ gần nhất cách gần bằng nhau (chênh dưới 1,8 lần), mà phím gần nhất
    /// làm âm tiết không hợp lệ còn phím kia thì không, thì chọn phím kia. Chạm giữa phím (trong 1/4 bề rộng) giữ nguyên.
    func dynamicZone(_ index: Int, at point: CGPoint) -> Int {
        // Không áp trong trình soạn (tìm emoji, ký tự vuốt xuống): âm tiết đang kiểm là của ô nhập chính.
        guard let check = validNextLetter, !dialpad, editLayer == nil, page < 0, inlineDocuments.isEmpty,
              let chosen = keys.first(where: { $0.1 == index }), KeyboardProfile.isLetter(chosen.2),
              hypot(point.x - chosen.0.center.x, point.y - chosen.0.center.y) > chosen.0.bounds.width * 0.25 else { return index }
        let near = keys.filter { !$0.0.isHidden && KeyboardProfile.isLetter($0.2) }
            .map { ($0, hypot(point.x - $0.0.center.x, point.y - $0.0.center.y)) }.sorted { $0.1 < $1.1 }
        guard near.count >= 2, near[1].1 < near[0].1 * 1.8, !check(near[0].0.2), check(near[1].0.2) else { return index }
        return near[1].0.1
    }
    func adaptTouch(_ point: CGPoint, index: Int) {
        guard preferences.configuration.touchAdaptation, inlineDocuments.isEmpty, editLayer == nil, !dialpad,
              let key = keys.first(where: { $0.1 == index }), KeyboardProfile.isLetter(key.2) else { return }
        let name = preferences.profile.id + ":" + key.2
        let old = preferences.configuration.offsets[name] ?? [0,0]
        let dx = Double((point.x - key.0.center.x) / max(1,key.0.bounds.width))
        let dy = Double((point.y - key.0.center.y) / max(1,key.0.bounds.height))
        if preferences.configuration.offsets.count >= 200 && preferences.configuration.offsets[name] == nil { preferences.configuration.offsets.removeValue(forKey: preferences.configuration.offsets.keys.sorted().first!) }
        preferences.configuration.offsets[name] = [min(0.3,max(-0.3,old[0] * 0.95 + dx * 0.05)),min(0.3,max(-0.3,old[1] * 0.95 + dy * 0.05))]
    }
    func setPressed(_ index: Int?) {
        if let pressedIndex, pressedIndex != index { keys.first { $0.1 == pressedIndex }?.0.pressed = false }
        pressedIndex = index
        if let index { keys.first { $0.1 == index }?.0.pressed = true }
    }
    func setHover(_ index: Int?) {
        if let hoverIndex, hoverIndex != index { keys.first { $0.1 == hoverIndex }?.0.pressed = false }
        hoverIndex = index
        if let index { keys.first { $0.1 == index }?.0.pressed = true }
    }

    func handle(_ event: KeyboardGesture.Event) {
        switch event {
        case let .begin(index, _):
            cursorStep = .zero; deletingWords = false; setPressed(index); onWordDelete?(0, false); showPopup(index)
        case let .move(index, start, point, path, visited, held):
            guard let key = keys.first(where: { $0.1 == index }) else { return }
            // Bong bóng chỉ cho chạm: giữ, vuốt hay lướt thì ẩn.
            if held || hypot(point.x - start.x, point.y - start.y) >= preferences.configuration.swipeThreshold { hidePopup() }
            if isBackspace(key.2) && editLayer == nil {
                if held && repeatTimer == nil {
                    type("⌫")
                    repeatTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in self?.type("⌫") }
                }
                return
            }
            if dialpad { return }
            let code = preferences.profile.letters[index], dx = point.x - start.x, dy = point.y - start.y
            if held && code == " " {
                // Như Android: ngang mỗi nửa bước một ký tự, dọc mỗi bước một dòng.
                let size = CGFloat(preferences.configuration.stepSize)
                let step = CGPoint(x: (dx / (size / 2)).rounded(.toNearestOrAwayFromZero), y: (dy / size).rounded(.toNearestOrAwayFromZero))
                if inlineDocuments.isEmpty { onCursor?(Int(step.x - cursorStep.x), Int(step.y - cursorStep.y)) }; cursorStep = step
                keys.forEach { $0.0.alpha = 0.45 }
            } else if held && (KeyboardProfile.isLetter(code) || editLayer != nil) && (editLayer != nil || (preferences.configuration.rearrange && rearrangeAllowed)) && inlineDocuments.isEmpty {
                if dragged == nil {
                    // Phím đang kéo nổi phía trên ngón tay để ngón không che nhãn, như Android.
                    let ghost = UILabel(frame: CGRect(x: 0, y: 0, width: 52, height: 52)); ghost.text = key.0.title(for: .normal); ghost.textAlignment = .center
                    ghost.font = .systemFont(ofSize: 28, weight: .bold)
                    ghost.backgroundColor = theme.background(for: key.0.kind); ghost.textColor = theme.foreground(for: key.0.kind)
                    ghost.layer.cornerRadius = 26; ghost.clipsToBounds = true
                    ghost.layer.shadowColor = UIColor.black.cgColor; ghost.layer.shadowOpacity = 0.25; ghost.layer.shadowRadius = 6; ghost.layer.shadowOffset = CGSize(width: 0, height: 2); ghost.layer.masksToBounds = false
                    ghost.isUserInteractionEnabled = false; ghost.layer.zPosition = 20; addSubview(ghost); dragged = ghost
                    setPressed(nil)
                }
                dragged?.center = CGPoint(x: point.x, y: point.y - 40)
                let over = displayedCell(at: point)
                setHover(over != index ? over : nil)
            } else if code == " " && -dx >= wordThreshold && abs(dx) > abs(dy) {
                deletingWords = true; let count = wordCount(dx)
                showBanner("Xoá \(count) từ"); onWordDelete?(count, false)
            } else if KeyboardProfile.isLetter(code) && visited.filter({ KeyboardProfile.isLetter(preferences.profile.letters[$0]) }).count >= 3 && glideAllowed && preferences.configuration.glide && page < 0 && editLayer == nil && inlineDocuments.isEmpty {
                let line = UIBezierPath(); if let first = path.first { line.move(to: first) }; path.dropFirst().forEach { line.addLine(to: $0) }; traceLayer.path = line.cgPath
                setPressed(nil)
            }
        case let .end(index, start, point, path, visited, held):
            defer { clearGesture() }
            guard let key = keys.first(where: { $0.1 == index }) else { return }
            if isBackspace(key.2) && editLayer == nil {
                if repeatTimer == nil { activate(key.2, index: index) } // giữ thì đã xoá trong lúc giữ
                return
            }
            if dialpad {
                let dx = point.x - start.x, dy = point.y - start.y
                if let hint = Self.dialpadHints[key.2], !hint.isEmpty, dy >= preferences.configuration.swipeThreshold, abs(dy) > abs(dx) { type(hint); return }
                activate(key.2,index: index); return
            }
            let code = preferences.profile.letters[index], dx = point.x - start.x, dy = point.y - start.y
            if held && code == " " { return }
            if editLayer != nil && !held { editCell(index,layer: editLayer!); return }
            // Ngón cái chạm mép ⚙/123/☺/⇧ rồi lăn sang chữ trước khi nhấc: lần chạm ngắn kết thúc trên chữ gõ chữ đó,
            // không bật nút chức năng đã chạm lúc đầu (như Android).
            if editLayer == nil && !held && code.hasPrefix("#") && max(abs(dx), abs(dy)) < preferences.configuration.swipeThreshold,
               let target = displayedCell(at: point), target != index, KeyboardProfile.isLetter(preferences.profile.letters[target]) {
                adaptTouch(point, index: target); activate(preferences.profile.letters[target], index: target); return
            }
            if held && code == "#symbols" { dialpad = true; reloadKeys(); return }
            if held && dragged != nil {
                if let other = displayedCell(at: point), other != index { swapCells(index, other, layer: editLayer ?? (page >= 0 ? page + 2 : 0)) }
                else if editLayer == nil { activate(code, index: index) }
                return
            }
            // Lớp 123: vuốt trên một ô ký hiệu cũng chỉ gõ ký hiệu đó, không thành chữ hoa hay ký tự vuốt xuống.
            if symbolValue(code, index: index) != nil { activate(code, index: index); return }
            if held && code == "#settings" { show("settings"); return }
            // Vuốt xuống phím ⚙ mở lịch sử clipboard (khi bật), như Android.
            if code == "#settings" && clipboardEnabled && dy >= preferences.configuration.swipeThreshold && abs(dy) > abs(dx) { show("clipboard"); return }
            let distinct = visited.filter { KeyboardProfile.isLetter(preferences.profile.letters[$0]) }
            if KeyboardProfile.isLetter(code) && distinct.count >= 3 && glideAllowed && preferences.configuration.glide && editLayer == nil && inlineDocuments.isEmpty && page < 0 {
                let width = max(1,key.0.bounds.width)
                let trace = path.map { "\($0.x / width),\($0.y / width)" }.joined(separator: ";")
                let centers = keys.filter { KeyboardProfile.isLetter($0.2) }.map { "\($0.2):\($0.0.center.x / width),\($0.0.center.y / width)" }.joined(separator: "\n")
                onGlide?(trace, centers); return
            }
            guard max(abs(dx),abs(dy)) >= preferences.configuration.swipeThreshold else { adaptTouch(point,index: index); activate(code,index: index); return }
            if code == " " {
                if abs(dx) > abs(dy) {
                    if dx < 0 {
                        if !inlineDocuments.isEmpty { type("⌫") } else if deletingWords { onWordDelete?(wordCount(dx),true) } else { onCommand?("space-left") }
                    }
                } else if dy > 0 { if inlineDocuments.isEmpty { onToggleLanguage?() } else { inlineSession.englishMode.toggle(); updateEditorBar() } } else if inlineDocuments.isEmpty { onCapsLock?() }
            } else if KeyboardProfile.isLetter(code) && abs(dy) > abs(dx) && dy < 0 { type(uppercase ? code.lowercased() : code.uppercased()) }
            else if (KeyboardProfile.isLetter(code) || [",","."].contains(code)) && abs(dy) > abs(dx) && dy > 0 { let value = preferences.profile.swipe[index]; if !value.isEmpty { type(value) } }
        case .cancel: clearGesture()
        }
    }
    /// Bỏ chạm/cử chỉ đang dở: đổi lớp hay bố cục, bàn phím bị ẩn.
    func cancelTouches() { touchGesture.cancel() }
    func clearGesture() {
        dragged?.removeFromSuperview(); dragged = nil; traceLayer.path = nil; keys.forEach { $0.0.alpha = 1 }
        hidePopup()
        setPressed(nil); setHover(nil)
        if deletingWords { hideBanner() }
        repeatTimer?.invalidate(); repeatTimer = nil
    }
    /// Bong bóng phóng to phím chữ/ký hiệu đang chạm, đặt ngay trên mặt phím và giữ trong khung bàn phím.
    func showPopup(_ index: Int) {
        guard visiblePanel == nil, editLayer == nil, !dialpad, let key = keys.first(where: { $0.1 == index })?.0,
              [.consonant, .vowel, .tone, .symbol].contains(key.kind), key.symbolName == nil,
              let title = key.title(for: .normal), !title.isEmpty, title.count <= 2 else { hidePopup(); return }
        let face = key.convert(key.keyFrame, to: self)
        let width = max(44, face.width * 1.4), height = min(58, max(46, face.height))
        let x = min(max(2, face.midX - width / 2), bounds.width - width - 2)
        popup.frame = CGRect(x: x, y: max(2, face.minY - height + 4), width: width, height: height)
        popup.label.text = title; popup.isHidden = false; bringSubviewToFront(popup)
    }
    func hidePopup() { popup.isHidden = true }
    @objc private func repeatDelete(_ recognizer: UILongPressGestureRecognizer) {
        if recognizer.state == .began {
            type("⌫"); repeatTimer = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) { [weak self] _ in self?.type("⌫") }
        } else if recognizer.state != .changed { repeatTimer?.invalidate(); repeatTimer = nil }
    }
    @objc private func twoFinger(_ recognizer: UISwipeGestureRecognizer) { guard inlineDocuments.isEmpty else { return }; onCommand?(recognizer.direction == .left ? "undo" : "redo") }
    @objc func forgetCandidate(_ recognizer: UILongPressGestureRecognizer) {
        if recognizer.state == .began, let button = recognizer.view as? UIButton, let word = button.title(for: .normal) { onForgetCandidate?(word) }
    }
    /// Viên thông báo nổi trên thanh gợi ý; `message()` tự ẩn sau một lúc, `showBanner` giữ tới khi `hideBanner`.
    func showBanner(_ text: String) {
        bannerTask?.cancel(); bannerTask = nil
        banner.text = text
        if banner.isHidden { banner.alpha = 0; banner.isHidden = false; UIView.animate(withDuration: 0.09) { self.banner.alpha = 1 } }
        setNeedsLayout()
    }
    func hideBanner() {
        bannerTask?.cancel(); bannerTask = nil
        UIView.animate(withDuration: 0.22, animations: { self.banner.alpha = 0 }) { _ in if self.banner.alpha == 0 { self.banner.isHidden = true } }
    }
    override func layoutSubviews() {
        super.layoutSubviews()
        let bar = Self.barHeight, pad = Self.pad
        globe.frame = CGRect(x: 4, y: (bar - 34) / 2, width: 40, height: 34)
        mode.frame = CGRect(x: globe.isHidden ? 4 : 46, y: (bar - 34) / 2, width: 44, height: 34)
        backspace.frame = CGRect(x: bounds.width - 54, y: (bar - 34) / 2, width: 50, height: 34)
        more.frame = CGRect(x: backspace.frame.minX - 40, y: (bar - 34) / 2, width: 40, height: 34)
        // Thanh trên của bảng: ‹ bảng cha | tiêu đề | Xong (trước ⌫ nếu ⌫ đang hiện).
        let doneRight = backspace.isHidden ? bounds.width - pad - 4 : backspace.frame.minX - 4
        panelBack.frame = CGRect(x: doneRight - 64, y: 0, width: 64, height: bar)
        panelUp.frame = CGRect(x: pad - 2, y: 0, width: 120, height: bar)
        let titleInset = max(bounds.width - panelBack.frame.minX, panelUp.frame.maxX) + 4
        panelTitle.frame = CGRect(x: titleInset, y: 0, width: max(0, bounds.width - 2 * titleInset), height: bar)
        let candidatesLeft: CGFloat = mode.isHidden ? pad : mode.frame.maxX + 2
        let candidatesRight = more.isHidden ? backspace.frame.minX - 2 : more.frame.minX
        candidateScroll.frame = CGRect(x: candidatesLeft, y: 0, width: max(0,candidatesRight - candidatesLeft), height: bar)
        let dividers = CGFloat(max(0, slotCount - 1)) / max(1, UIScreen.main.scale)
        let slot = floor((candidateScroll.bounds.width - dividers) / CGFloat(max(1, slotCount)))
        slotWidths.forEach { $0.constant = max(44, slot) }
        let size = candidateStack.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize)
        candidateStack.frame = CGRect(x: 0, y: (bar - 38) / 2, width: size.width, height: 38); candidateScroll.contentSize = CGSize(width: size.width, height: bar)
        // Sửa lớp phím: sáu nút chọn lớp nằm ngay chỗ thanh gợi ý. Trình soạn chữ: hàng ô nhập rộng dưới thanh trên, bàn phím
        // cao thêm `extraHeight` nên lưới phím không bị thu nhỏ.
        if extraHeight > 0 {
            editorBar.frame = CGRect(x: pad, y: bar + 2, width: bounds.width - 2 * pad, height: Self.editorRowHeight - 6)
        } else {
            let editorLeft: CGFloat = !mode.isHidden ? mode.frame.maxX + 4 : !globe.isHidden ? globe.frame.maxX + 4 : pad
            let editorRight: CGFloat = backspace.isHidden ? bounds.width - pad : backspace.frame.minX - 4
            editorBar.frame = CGRect(x: editorLeft, y: (bar - 40) / 2, width: max(0, editorRight - editorLeft), height: 40)
        }
        let top: CGFloat = bar + extraHeight + Self.gridTop
        let heightKey = "\(extraHeight)|\(preferences.profile.spec.isHex)|\(preferences.profile.spec.span)|\(preferences.profile.spec.rowSizes.count)|\(preferences.configuration.heightScale)"
        if reportedHeightKey != heightKey {
            reportedHeightKey = heightKey
            DispatchQueue.main.async { [weak self] in self?.onHeightChange?() }
        }
        let scale: CGFloat = preferences.configuration.oneHand == 0 ? 1 : 0.8
        let span = bounds.width * scale
        let bottom = Self.gridBottom + safeAreaInsets.bottom
        let spec = preferences.profile.spec, available = max(1,bounds.height - top - bottom)
        // Tổ ong: lục giác đều, bước hàng 0,75 chiều cao. Truyền thống: bước hàng 1,44 bề rộng ô, không vượt chiều cao còn lại.
        let width = spec.isHex ? max(1,min((span - 2 * pad) / spec.span, available * HexShape.sqrt3 / 8, Self.hexMaxKey)) : max(1,(span - 2 * pad) / spec.span)
        let height = spec.isHex ? width * 2 / HexShape.sqrt3 : min(width * Self.rectRowRatio, Self.rectMaxRow, available / CGFloat(spec.rowSizes.count))
        let pitch = spec.isHex ? height * 0.75 : height
        let left = (span - width * spec.span) / 2 + (preferences.configuration.oneHand == 2 ? bounds.width - span : 0)
        for (button,index,code) in keys {
            if dialpad {
                let gap: CGFloat = 6, weights: [CGFloat] = [90, 90, 90, 86]
                let totalWidth = bounds.width - 2 * pad - 3 * gap, rowHeight = max(1,(bounds.height - top - bottom - 2 - 3 * gap) / 4)
                let widths = weights.map { totalWidth * $0 / weights.reduce(0, +) }
                let column = index % 4, row = index / 4
                let x = pad + widths.prefix(column).reduce(0, +) + CGFloat(column) * gap
                button.frame = CGRect(x: x,y: top + CGFloat(row) * (rowHeight + gap),width: widths[column],height: rowHeight)
            } else {
                let row = spec.position(index).row
                button.frame = CGRect(x: left + spec.left(index) * width, y: top + CGFloat(row) * pitch,
                                      width: width * CGFloat(code == " " ? spec.spaceCells : 1), height: height)
            }
        }
        panel.frame = CGRect(x: pad,y: bar,width: bounds.width - 2 * pad,height: max(1,bounds.height - bar - safeAreaInsets.bottom - 4))
        panelStack.frame = CGRect(x: 0,y: 4,width: panel.bounds.width,height: panelStack.systemLayoutSizeFitting(CGSize(width: panel.bounds.width,height: UIView.layoutFittingCompressedSize.height),withHorizontalFittingPriority: .required,verticalFittingPriority: .fittingSizeLevel).height)
        panel.contentSize = CGSize(width: panelStack.bounds.width, height: panelStack.bounds.height + 12)
        let bannerSize = banner.sizeThatFits(CGSize(width: bounds.width - 76, height: bar))
        let bannerWidth = min(bounds.width - 76, bannerSize.width), bannerHeight = min(bar - 6, max(40, bannerSize.height))
        // Khi các nút chọn lớp chiếm thanh trên, thông báo nổi ngay bên dưới để không che chúng.
        let bannerY = editLayer != nil && !editorBar.isHidden ? bar + 4 : (bar - bannerHeight) / 2
        banner.frame = CGRect(x: (bounds.width - 64 - bannerWidth) / 2 + 6,y: bannerY,width: bannerWidth,height: bannerHeight)
        if !banner.isHidden { bringSubviewToFront(banner) }
    }
}
