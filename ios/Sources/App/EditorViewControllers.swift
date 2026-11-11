import UIKit

/// Thêm/sửa một gõ tắt: viết tắt (chữ thường, tối đa 32 ký tự) và nội dung (tối đa 2.000 ký tự). Để trống nội dung là xoá.
final class SnippetEditorViewController: UIViewController, UITextFieldDelegate {
    private let key: String?
    private let settings: AppSettings
    private let abbreviation = UITextField(), content = UITextView()
    init(key: String?, settings: AppSettings = .shared) {
        self.key = key; self.settings = settings
        super.init(nibName: nil, bundle: nil)
        title = key == nil ? "Thêm gõ tắt" : "Sửa gõ tắt"
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemGroupedBackground
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "Lưu", primaryAction: UIAction { [weak self] _ in self?.save() })
        navigationItem.rightBarButtonItem?.style = .done
        navigationItem.rightBarButtonItem?.accessibilityIdentifier = "save-snippet"
        abbreviation.placeholder = "Viết tắt, vd. dc"; abbreviation.text = key
        abbreviation.autocapitalizationType = .none; abbreviation.autocorrectionType = .no; abbreviation.keyboardType = .asciiCapable
        abbreviation.font = .preferredFont(forTextStyle: .body); abbreviation.accessibilityIdentifier = "snippet-key"
        abbreviation.returnKeyType = .next; abbreviation.delegate = self
        content.text = key.flatMap { settings.preferences.configuration.snippets[$0] } ?? ""
        content.font = .preferredFont(forTextStyle: .body); content.backgroundColor = .clear; content.accessibilityIdentifier = "snippet-value"
        // Nội dung thường chèn giữa câu; gõ viết tắt có hoa đầu thì bản mở rộng tự viết hoa.
        content.autocapitalizationType = .none
        content.textContainerInset = .zero; content.textContainer.lineFragmentPadding = 0
        func card(_ title: String, _ field: UIView, height: CGFloat) -> UIView {
            let label = UILabel(); label.text = title.uppercased(); label.font = .preferredFont(forTextStyle: .footnote); label.textColor = .secondaryLabel
            let box = UIView(); box.backgroundColor = .secondarySystemGroupedBackground; box.layer.cornerRadius = 10
            field.translatesAutoresizingMaskIntoConstraints = false; box.addSubview(field)
            NSLayoutConstraint.activate([
                field.leadingAnchor.constraint(equalTo: box.leadingAnchor, constant: 16), field.trailingAnchor.constraint(equalTo: box.trailingAnchor, constant: -16),
                field.topAnchor.constraint(equalTo: box.topAnchor, constant: 12), field.bottomAnchor.constraint(equalTo: box.bottomAnchor, constant: -12),
                box.heightAnchor.constraint(equalToConstant: height)
            ])
            // Chạm bất kỳ đâu trong khung là vào ô nhập.
            box.addGestureRecognizer(UITapGestureRecognizer(target: field, action: #selector(UIResponder.becomeFirstResponder)))
            return UIStackView(arrangedSubviews: [label, box]).vertical(spacing: 6)
        }
        let note = UILabel(); note.text = "Gõ chữ viết tắt rồi chạm cách để thay bằng nội dung. Để trống nội dung để xoá."
        note.font = .preferredFont(forTextStyle: .footnote); note.textColor = .secondaryLabel; note.numberOfLines = 0
        let stack = UIStackView(arrangedSubviews: [card("Viết tắt", abbreviation, height: 48), card("Nội dung", content, height: 140), note]).vertical(spacing: 20)
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor), stack.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
            stack.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20)
        ])
    }
    override func viewDidAppear(_ animated: Bool) { super.viewDidAppear(animated); (key == nil ? abbreviation as UIView : content).becomeFirstResponder() }
    func textFieldShouldReturn(_ textField: UITextField) -> Bool { content.becomeFirstResponder(); return false }
    private func save() {
        let name = (abbreviation.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let value = content.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, name.count <= 32, value.count <= 2000 else { warn("Viết tắt 1–32 ký tự, nội dung tối đa 2.000 ký tự"); return }
        var snippets = settings.preferences.configuration.snippets
        if let key { snippets.removeValue(forKey: key) }
        if !value.isEmpty { snippets[name] = value }
        guard snippets.count <= 1000 else { warn("Tối đa 1.000 gõ tắt"); return }
        settings.preferences.configuration.snippets = snippets
        navigationController?.popViewController(animated: true)
    }
    private func warn(_ text: String) {
        let alert = UIAlertController(title: nil, message: text, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .cancel)); present(alert, animated: true)
    }
}

/// Sửa các lớp phím (chữ, vuốt xuống, hai trang 123) trên chính bàn phím TeleHex dựng trong app, rộng như bàn phím thật.
/// Sửa profile có sẵn sẽ tạo bản riêng "Của tôi". Lưu ngay và gửi sang bàn phím.
final class LayoutEditorViewController: UIViewController {
    private let settings: AppSettings
    private lazy var keyboard = HexKeyboardView(preferences: settings.preferences)
    private let hint = UILabel()
    init(settings: AppSettings = .shared) { self.settings = settings; super.init(nibName: nil, bundle: nil); title = "Chỉnh sửa phím" }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemGroupedBackground
        hint.text = "Chữ, 123: chạm hai phím hoặc giữ rồi kéo để đổi chỗ.\nVuốt ↓: chạm một phím để sửa ký tự vuốt xuống.\n↺ đưa lớp đang sửa về mặc định; Xong để lưu và quay lại."
        hint.font = .preferredFont(forTextStyle: .subheadline); hint.textColor = .secondaryLabel; hint.numberOfLines = 0
        view.addSubview(hint); view.addSubview(keyboard)
        keyboard.accessibilityIdentifier = "layout-editor-keyboard"
        keyboard.onPreferencesChanged = { [weak self] in self?.refresh() }
        keyboard.onHeightChange = { [weak self] in self?.view.setNeedsLayout() }
        keyboard.onLayoutEditorDone = { [weak self] in self?.navigationController?.popViewController(animated: true) }
        refresh()
        keyboard.startLayoutEditor(0)
    }
    private func refresh() { keyboard.update(candidates: [], english: false, uppercase: false) }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let width = view.bounds.width
        let height = HexKeyboardView.height(for: width, screenHeight: view.window?.windowScene?.screen.bounds.height ?? view.bounds.height,
                                            spec: settings.preferences.profile.spec) * settings.preferences.configuration.heightScale
            + keyboard.extraHeight + view.safeAreaInsets.bottom
        keyboard.frame = CGRect(x: 0, y: view.bounds.height - height, width: width, height: height)
        let margins = view.layoutMargins
        hint.frame = CGRect(x: margins.left, y: view.safeAreaInsets.top + 20, width: width - margins.left - margins.right,
                            height: hint.sizeThatFits(CGSize(width: width - margins.left - margins.right, height: .greatestFiniteMagnitude)).height)
    }
}
