import UIKit

/// Một bước bật bàn phím: số thứ tự (thành ✓ xanh khi xong), lời hướng dẫn và nút tuỳ chọn bên dưới.
final class SetupStepView: UIStackView {
    private let badge = UIImageView(), label = UILabel()
    let number: Int
    var done = false { didSet { refresh() } }
    init(number: Int, text: String, action: UIView? = nil) {
        self.number = number
        super.init(frame: .zero)
        axis = .horizontal; alignment = .top; spacing = 12
        badge.contentMode = .center
        badge.widthAnchor.constraint(equalToConstant: 28).isActive = true; badge.heightAnchor.constraint(equalToConstant: 28).isActive = true
        label.text = text; label.numberOfLines = 0; label.font = .preferredFont(forTextStyle: .subheadline); label.adjustsFontForContentSizeCategory = true
        let body = UIStackView(arrangedSubviews: [label] + (action.map { [$0] } ?? []))
        body.axis = .vertical; body.alignment = .leading; body.spacing = 4
        addArrangedSubview(badge); addArrangedSubview(body)
        isAccessibilityElement = false
        refresh()
    }
    required init(coder: NSCoder) { fatalError("init(coder:) is unsupported") }
    private func refresh() {
        let configuration = UIImage.SymbolConfiguration(pointSize: 22, weight: .regular)
        badge.image = UIImage(systemName: done ? "checkmark.circle.fill" : "\(number).circle", withConfiguration: configuration)
        badge.tintColor = done ? .systemGreen : .secondaryLabel
        badge.accessibilityLabel = done ? "Bước \(number): đã xong" : "Bước \(number)"
        badge.isAccessibilityElement = true
        label.textColor = done ? .secondaryLabel : .label
    }
}

/// Ô chọn bố cục ở màn chào: hình xem trước là bàn phím thật thu nhỏ (không nhận chạm), tên và mô tả; ô đang chọn có viền
/// màu nhấn và ✓.
final class LayoutOptionButton: UIControl {
    let profileID: String
    private let preview: HexKeyboardView
    private let frameView: PreviewFrame, check = UIImageView(), title = UILabel(), detail = UILabel()
    /// Bề rộng bàn phím dựng hình xem trước, rồi thu nhỏ vừa ô.
    private static let renderWidth: CGFloat = 390
    override var isSelected: Bool { didSet { refresh() } }
    override var isHighlighted: Bool { didSet { alpha = isHighlighted ? 0.6 : 1 } }

    init(profileID: String, name: String, detail text: String) {
        self.profileID = profileID
        let defaults = UserDefaults(suiteName: "telehex.welcome.preview.\(profileID)") ?? .standard
        let preferences = KeyboardPreferences(defaults: defaults)
        preferences.configuration.activeProfile = profileID
        preview = HexKeyboardView(preferences: preferences)
        frameView = PreviewFrame(preview: preview, width: Self.renderWidth,
                                 height: HexKeyboardView.height(for: Self.renderWidth, screenHeight: 900, spec: preferences.profile.spec))
        super.init(frame: .zero)
        preview.update(candidates: [], english: false, uppercase: false)
        preview.isUserInteractionEnabled = false; preview.isAccessibilityElement = false; preview.accessibilityElementsHidden = true
        frameView.clipsToBounds = true; frameView.layer.cornerRadius = 10; frameView.isUserInteractionEnabled = false
        check.image = UIImage(systemName: "checkmark.circle.fill", withConfiguration: UIImage.SymbolConfiguration(pointSize: 20, weight: .semibold))
        check.tintColor = tintColor; check.isUserInteractionEnabled = false
        title.text = name; title.font = .preferredFont(forTextStyle: .headline); title.adjustsFontForContentSizeCategory = true
        detail.text = text; detail.font = .preferredFont(forTextStyle: .caption1); detail.textColor = .secondaryLabel; detail.numberOfLines = 0
        detail.adjustsFontForContentSizeCategory = true
        let labels = UIStackView(arrangedSubviews: [title, detail]); labels.axis = .vertical; labels.spacing = 2; labels.isUserInteractionEnabled = false
        let stack = UIStackView(arrangedSubviews: [frameView, labels]); stack.axis = .vertical; stack.spacing = 8; stack.isUserInteractionEnabled = false
        stack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(stack); addSubview(check)
        check.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: topAnchor), stack.bottomAnchor.constraint(equalTo: bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: leadingAnchor), stack.trailingAnchor.constraint(equalTo: trailingAnchor),
            frameView.heightAnchor.constraint(equalTo: frameView.widthAnchor, multiplier: 315 / Self.renderWidth),
            check.topAnchor.constraint(equalTo: frameView.topAnchor, constant: 6), check.trailingAnchor.constraint(equalTo: frameView.trailingAnchor, constant: -6)
        ])
        isAccessibilityElement = true; accessibilityTraits = .button
        accessibilityLabel = name; accessibilityHint = text; accessibilityIdentifier = "layout-option-\(profileID)"
        refresh()
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }
    override func tintColorDidChange() { super.tintColorDidChange(); refresh() }
    private func refresh() {
        frameView.layer.borderWidth = isSelected ? 3 : 1
        frameView.layer.borderColor = (isSelected ? tintColor : UIColor.separator).cgColor
        check.tintColor = tintColor; check.isHidden = !isSelected
        if isSelected { accessibilityTraits.insert(.selected) } else { accessibilityTraits.remove(.selected) }
    }
}

/// Khung chứa bàn phím xem trước: dựng ở kích thước thật rồi thu nhỏ vừa khung mỗi khi khung đổi cỡ.
final class PreviewFrame: UIView {
    private let preview: UIView, size: CGSize
    init(preview: UIView, width: CGFloat, height: CGFloat) {
        self.preview = preview; size = CGSize(width: width, height: height)
        super.init(frame: .zero)
        addSubview(preview)
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }
    override func layoutSubviews() {
        super.layoutSubviews()
        guard bounds.width > 0 else { return }
        let scale = bounds.width / size.width
        preview.transform = .identity
        preview.frame = CGRect(origin: .zero, size: size)
        preview.layoutIfNeeded()
        preview.transform = CGAffineTransform(scaleX: scale, y: scale)
        // Bàn phím thấp hơn khung thì canh giữa theo chiều dọc.
        preview.center = CGPoint(x: bounds.midX, y: max(size.height * scale / 2, bounds.midY))
    }
}

/// Một mục trong thẻ "Cài đặt" của trang chính: ô icon màu, tên, ›, vạch ngăn mảnh phía dưới như app Cài đặt.
final class SettingsLinkRow: UIControl {
    init(title: String, symbol: String, color: UInt32) {
        super.init(frame: .zero)
        let tile = UIImageView(image: UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: 15, weight: .medium)))
        tile.tintColor = .white; tile.backgroundColor = UIColor(hex: color); tile.contentMode = .center; tile.layer.cornerRadius = 7; tile.clipsToBounds = true
        let label = UILabel(); label.text = title; label.font = .preferredFont(forTextStyle: .body); label.adjustsFontForContentSizeCategory = true
        let chevron = UIImageView(image: UIImage(systemName: "chevron.right", withConfiguration: UIImage.SymbolConfiguration(pointSize: 13, weight: .semibold)))
        chevron.tintColor = .tertiaryLabel
        let separator = UIView(); separator.backgroundColor = .separator
        for view in [tile, label, chevron, separator] { view.translatesAutoresizingMaskIntoConstraints = false; view.isUserInteractionEnabled = false; addSubview(view) }
        NSLayoutConstraint.activate([
            heightAnchor.constraint(greaterThanOrEqualToConstant: 46),
            tile.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16), tile.centerYAnchor.constraint(equalTo: centerYAnchor),
            tile.widthAnchor.constraint(equalToConstant: 29), tile.heightAnchor.constraint(equalToConstant: 29),
            label.leadingAnchor.constraint(equalTo: tile.trailingAnchor, constant: 12), label.centerYAnchor.constraint(equalTo: centerYAnchor),
            label.topAnchor.constraint(greaterThanOrEqualTo: topAnchor, constant: 11),
            chevron.leadingAnchor.constraint(greaterThanOrEqualTo: label.trailingAnchor, constant: 8),
            chevron.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16), chevron.centerYAnchor.constraint(equalTo: centerYAnchor),
            separator.leadingAnchor.constraint(equalTo: label.leadingAnchor), separator.trailingAnchor.constraint(equalTo: trailingAnchor),
            separator.bottomAnchor.constraint(equalTo: bottomAnchor), separator.heightAnchor.constraint(equalToConstant: 1 / UIScreen.main.scale)
        ])
        isAccessibilityElement = true; accessibilityLabel = title; accessibilityTraits = .button
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }
    override var isHighlighted: Bool { didSet { backgroundColor = isHighlighted ? .systemFill : .clear } }
}
