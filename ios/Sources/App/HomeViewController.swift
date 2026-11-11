import UIKit

final class HomeViewController: UIViewController {
    private let textView = UITextView()
    private let placeholder = UILabel()
    private var addStep: SetupStepView?
    private var layoutOptions: [LayoutOptionButton] = []
    private let layoutNote = UILabel()
    /// Bundle ID của keyboard extension, để nhận ra TeleHex trong danh sách bàn phím đã thêm.
    private var keyboardID: String { (Bundle.main.bundleIdentifier ?? "com.telehex.keyboard") + ".extension" }
    private lazy var document = TextViewDocument(textView)
    private let settings = AppSettings.shared
    private lazy var preview = KeyboardDriver(preferences: settings.preferences, document: { [unowned self] in self.document }, traits: { (.default, .none) })
    private let reachNote = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--reset-test-data") {
            if let bundle = Bundle.main.bundleIdentifier { UserDefaults.standard.removePersistentDomain(forName: bundle) }
            try? FileManager.default.removeItem(at: KeyboardDataStore().root)
        }
        #endif
        title = "TeleHex"
        view.backgroundColor = .systemGroupedBackground
        // Màn chào như Android 0.8.0: ô gõ thử ở đầu trang để không bị bàn phím che, các bước bật bàn phím (✓ khi xong) và chọn
        // kiểu bàn phím có hình xem trước. Lựa chọn gửi sang bàn phím qua App Group.
        let heading = UILabel()
        heading.text = "Bàn phím Telex tổ ong"
        heading.font = UIFontMetrics(forTextStyle: .title2).scaledFont(for: .systemFont(ofSize: 22, weight: .bold))
        heading.adjustsFontForContentSizeCategory = true
        heading.numberOfLines = 0
        textView.font = .preferredFont(forTextStyle: .title3)
        textView.backgroundColor = .secondarySystemGroupedBackground
        textView.layer.cornerRadius = 14
        textView.textContainerInset = UIEdgeInsets(top: 12, left: 10, bottom: 12, right: 10)
        textView.accessibilityIdentifier = "typing-field"
        textView.autocorrectionType = .no
        textView.autocapitalizationType = .none
        placeholder.text = "Chạm để gõ thử…"
        placeholder.font = .preferredFont(forTextStyle: .title3); placeholder.textColor = .placeholderText
        placeholder.isAccessibilityElement = false
        placeholder.translatesAutoresizingMaskIntoConstraints = false
        textView.addSubview(placeholder)
        NotificationCenter.default.addObserver(forName: UITextView.textDidChangeNotification, object: textView, queue: .main) { [weak self] _ in
            guard let self else { return }; self.placeholder.isHidden = !self.textView.text.isEmpty
        }
        let openSettings = UIButton(type: .system)
        openSettings.setTitle("Mở Cài đặt", for: .normal)
        openSettings.titleLabel?.font = .preferredFont(forTextStyle: .body)
        openSettings.contentHorizontalAlignment = .leading
        openSettings.accessibilityIdentifier = "open-settings"
        openSettings.addAction(UIAction { _ in
            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
        }, for: .touchUpInside)
        let add = SetupStepView(number: 1, text: "Thêm TeleHex: Cài đặt chung › Bàn phím › Bàn phím › Thêm bàn phím mới › TeleHex.", action: openSettings)
        add.accessibilityIdentifier = "setup-step-add"
        addStep = add
        let choose = SetupStepView(number: 2, text: "Chạm ô gõ thử, giữ 🌐 rồi chọn TeleHex. ⚙ trên bàn phím để tuỳ chỉnh.")
        let steps = card(title: "Bật TeleHex", lines: [], body: [add, choose])
        layoutOptions = [
            LayoutOptionButton(profileID: "builtin.default", name: "Tổ ong", detail: "Phím lục giác to hơn, vẫn thứ tự QWERTY"),
            LayoutOptionButton(profileID: "builtin.qwerty", name: "Truyền thống", detail: "QWERTY 10 cột quen thuộc, có ⇧ và ⌫"),
        ]
        for option in layoutOptions {
            option.addAction(UIAction { [weak self] _ in self?.chooseLayout(option.profileID) }, for: .touchUpInside)
        }
        let options = UIStackView(arrangedSubviews: layoutOptions); options.distribution = .fillEqually; options.spacing = 12; options.alignment = .top
        layoutNote.font = .preferredFont(forTextStyle: .footnote); layoutNote.textColor = .secondaryLabel; layoutNote.numberOfLines = 0
        layoutNote.text = "Bàn phím đổi theo lựa chọn khi mở lại. Profile riêng và chỉnh phím ở Cài đặt › Bàn phím."
        let layouts = card(title: "Kiểu bàn phím", lines: [], body: [options, layoutNote])
        // Cài đặt như Android: cùng nhóm và thứ tự; mỗi mục mở một trang danh sách.
        let links = SettingsScreens.entries.map { entry in
            let row = SettingsLinkRow(title: entry.title, symbol: entry.symbol, color: entry.color)
            row.accessibilityIdentifier = entry.id
            row.addAction(UIAction { [weak self] _ in self?.navigationController?.pushViewController(entry.make(), animated: true) }, for: .touchUpInside)
            return row
        }
        let list = UIStackView(arrangedSubviews: links); list.axis = .vertical
        list.backgroundColor = .secondarySystemGroupedBackground; list.layer.cornerRadius = 14; list.clipsToBounds = true
        let settingsHeading = UILabel(); settingsHeading.text = "Cài đặt"; settingsHeading.font = .systemFont(ofSize: 17, weight: .semibold)
        reachNote.font = .preferredFont(forTextStyle: .footnote); reachNote.textColor = .secondaryLabel; reachNote.numberOfLines = 0
        reachNote.text = "Cài đặt tới bàn phím ở lần mở bàn phím tiếp theo."
        if !settings.reachesKeyboard { reachNote.text = "Bản build chưa ký không có App Group nên cài đặt ở đây chưa tới được bàn phím." }
        let settingsGroup = UIStackView(arrangedSubviews: [settingsHeading, list, reachNote]).vertical(spacing: 8)
        let privacy = UILabel()
        privacy.text = "Xử lý trên máy, không gửi nội dung gõ. Không cần Truy cập đầy đủ — chỉ bật nếu muốn rung hoặc âm phím."
        privacy.font = .preferredFont(forTextStyle: .footnote); privacy.textColor = .secondaryLabel; privacy.numberOfLines = 0
        let notices = UIButton(type: .system)
        notices.setTitle("Nguồn dữ liệu và giấy phép", for: .normal)
        notices.titleLabel?.font = .preferredFont(forTextStyle: .footnote)
        notices.contentHorizontalAlignment = .leading
        notices.accessibilityIdentifier = "data-notices"
        notices.addAction(UIAction { [weak self] _ in self?.showNotices() }, for: .touchUpInside)
        let stack = UIStackView(arrangedSubviews: [heading, textView, steps, layouts, settingsGroup, privacy, notices])
        stack.axis = .vertical; stack.spacing = 14
        stack.setCustomSpacing(20, after: textView); stack.setCustomSpacing(4, after: privacy)
        stack.translatesAutoresizingMaskIntoConstraints = false
        let scroll = UIScrollView(); scroll.translatesAutoresizingMaskIntoConstraints = false; scroll.alwaysBounceVertical = true
        // Kéo trang là ẩn bàn phím, để xem màn chào và cài đặt bên dưới ô gõ thử.
        scroll.keyboardDismissMode = .onDrag
        view.addSubview(scroll); scroll.addSubview(stack)
        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.topAnchor), scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor), scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 8),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -24),
            stack.leadingAnchor.constraint(equalTo: scroll.frameLayoutGuide.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: scroll.frameLayoutGuide.trailingAnchor, constant: -20),
            textView.heightAnchor.constraint(equalToConstant: 104),
            placeholder.leadingAnchor.constraint(equalTo: textView.leadingAnchor, constant: 15),
            placeholder.topAnchor.constraint(equalTo: textView.topAnchor, constant: 12)
        ])
        refreshSetup()
        NotificationCenter.default.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            self?.settings.adoptKeyboardProfiles(); self?.refreshSetup()
        }
        // Bàn phím dùng ngay trong app (ô gõ thử) cũng có thể đổi profile: đọc lại khi bàn phím ẩn.
        NotificationCenter.default.addObserver(forName: UIResponder.keyboardDidHideNotification, object: nil, queue: .main) { [weak self] _ in
            self?.settings.adoptKeyboardProfiles(); self?.refreshSetup()
        }
        #if DEBUG
        // Chụp màn hình: mở bàn phím ngay khi màn hình hiện.
        if ProcessInfo.processInfo.arguments.contains("--focus-field") { DispatchQueue.main.async { [weak self] in self?.textView.becomeFirstResponder() } }
        #endif
        #if DEBUG
        // UI tests can exercise the same driver without requiring system keyboard setup.
        if ProcessInfo.processInfo.arguments.contains("--embedded-preview") {
            preview.files.presenter = self
            let keyboard = preview.view
            keyboard.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: 300)
            textView.inputView = keyboard
            // Cao như bàn phím thật (cùng công thức với KeyboardViewController) để ảnh chụp/video giống thực tế.
            keyboard.onHeightChange = { [weak self] in self?.sizePreview() }
            DispatchQueue.main.async { [weak self] in self?.sizePreview() }
            preview.onNextKeyboard = { [weak self] in self?.textView.resignFirstResponder() }
            // Chụp màn hình: `--profile=<id>` chọn bố cục, `--show-panel=<tên>` mở sẵn một bảng.
            for argument in ProcessInfo.processInfo.arguments {
                if argument.hasPrefix("--profile=") { preview.preferences.configuration.activeProfile = String(argument.dropFirst(10)) }
            }
            preview.update()
            if let panel = ProcessInfo.processInfo.arguments.first(where: { $0.hasPrefix("--show-panel=") }) {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) { keyboard.show(String(panel.dropFirst(13))) }
            }
        }
        #endif
    }

    #if DEBUG
    private func sizePreview() {
        let keyboard = preview.view, width = view.bounds.width
        let screenHeight = view.window?.windowScene?.screen.bounds.height ?? view.bounds.height
        let height = HexKeyboardView.height(for: width, screenHeight: screenHeight, spec: preview.preferences.profile.spec) * preview.preferences.configuration.heightScale
            + keyboard.extraHeight + (view.window?.safeAreaInsets.bottom ?? 0)
        guard abs(keyboard.frame.height - height) > 0.5 || keyboard.frame.width != width else { return }
        keyboard.frame = CGRect(x: 0, y: 0, width: width, height: height)
        textView.reloadInputViews()
    }
    #endif

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        refreshSetup()
        #if DEBUG
        // Bàn phím xem trước dùng chung cài đặt với app: áp lại cài đặt vừa chỉnh (gõ tắt, Telex…) khi quay về.
        if ProcessInfo.processInfo.arguments.contains("--embedded-preview") { preview.view.onPreferencesChanged?() }
        #endif
    }
    /// Bước 1 xong khi TeleHex có trong danh sách bàn phím đã thêm; ô bố cục đang chọn theo profile trong cài đặt.
    private func refreshSetup() {
        let enabled = (UserDefaults.standard.array(forKey: "AppleKeyboards") as? [String])?.contains(keyboardID) == true
        addStep?.done = enabled
        for option in layoutOptions { option.isSelected = option.profileID == settings.preferences.configuration.activeProfile }
    }
    /// Chọn kiểu ở màn chào: đổi profile trong cài đặt, gửi sang bàn phím.
    private func chooseLayout(_ id: String) {
        settings.preferences.configuration.activeProfile = id
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--embedded-preview") { preview.update() }
        #endif
        refreshSetup()
    }

    /// Thẻ bo góc kiểu Cài đặt: tiêu đề đậm, các dòng hướng dẫn, nội dung tuỳ chọn và một nút ở cuối.
    private func card(title: String, lines: [String], body: [UIView] = [], footer: UIView? = nil) -> UIView {
        let heading = UILabel(); heading.text = title; heading.font = .systemFont(ofSize: 17, weight: .semibold); heading.numberOfLines = 0
        let stack = UIStackView(arrangedSubviews: [heading] + lines.map { line in
            let label = UILabel(); label.text = line; label.numberOfLines = 0
            label.font = .preferredFont(forTextStyle: .subheadline); label.textColor = .secondaryLabel
            return label
        })
        body.forEach { stack.addArrangedSubview($0) }
        if let footer { stack.addArrangedSubview(footer) }
        stack.axis = .vertical; stack.spacing = 12
        stack.isLayoutMarginsRelativeArrangement = true; stack.layoutMargins = UIEdgeInsets(top: 14, left: 16, bottom: 14, right: 16)
        stack.backgroundColor = .secondarySystemGroupedBackground; stack.layer.cornerRadius = 14
        return stack
    }

    private func showNotices() {
        let controller = UIViewController(); controller.title = "Nguồn dữ liệu"
        let text = UITextView(); text.isEditable = false
        text.font = .preferredFont(forTextStyle: .body)
        text.text = ["en-NOTICE", "vi-NOTICE"].compactMap { name in
            Bundle.main.url(forResource: name, withExtension: "txt").flatMap { try? String(contentsOf: $0, encoding: .utf8) }
        }.joined(separator: "\n\n")
        controller.view = text
        navigationController?.pushViewController(controller, animated: true)
    }
}
