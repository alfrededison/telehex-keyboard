import UIKit

final class KeyboardViewController: UIInputViewController {
    private lazy var driver = KeyboardDriver(
        document: { [unowned self] in ProxyDocument(self.textDocumentProxy) },
        traits: { [unowned self] in (self.textDocumentProxy.keyboardType ?? .default, self.textDocumentProxy.autocapitalizationType ?? .none) }
    )
    private var height: NSLayoutConstraint?

    override func viewDidLoad() {
        super.viewDidLoad()
        driver.files.presenter = self
        // Đổi profile, ẩn danh, kéo đổi chỗ phím ngay trên bàn phím: giữ khi app gửi cài đặt cũ hơn.
        driver.preferences.trackLocalEdits()
        driver.fullAccess = { [weak self] in self?.hasFullAccess ?? false }
        let keyboard = driver.view
        keyboard.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(keyboard)
        NSLayoutConstraint.activate([
            keyboard.topAnchor.constraint(equalTo: view.topAnchor),
            keyboard.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            keyboard.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            keyboard.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        let height = view.heightAnchor.constraint(equalToConstant: 300)
        height.priority = .defaultHigh
        height.isActive = true
        self.height = height
        driver.onNextKeyboard = { [weak self] in self?.advanceToNextInputMode() }
        // Trình soạn mở hàng ô nhập: bàn phím cao thêm thay vì thu nhỏ phím.
        keyboard.onHeightChange = { [weak self] in self?.view.setNeedsLayout() }
        driver.update()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        driver.syncShared()
    }
    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        // Máy Face ID đã có nút chuyển bàn phím của hệ thống; chỉ máy có nút Home mới cần 🌐 trên bàn phím.
        driver.view.showsGlobe = needsInputModeSwitchKey
    }
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let screenHeight = view.window?.windowScene?.screen.bounds.height ?? 800
        // Lưới cao theo bề rộng; vùng home indicator cộng thêm bên dưới để hàng cuối không bị che.
        height?.constant = HexKeyboardView.height(for: view.bounds.width, screenHeight: screenHeight, spec: driver.preferences.profile.spec) * driver.preferences.configuration.heightScale + driver.view.extraHeight + view.safeAreaInsets.bottom
    }
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        // Chạm đang dở không được chạy ở lần mở bàn phím sau.
        driver.view.cancelTouches()
    }
    override func textDidChange(_ textInput: UITextInput?) { if isViewLoaded { driver.update() } }
}
