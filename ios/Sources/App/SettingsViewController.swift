import UIKit

/// Màn cài đặt dạng danh sách nhóm như app Cài đặt của iOS, dựng từ mô tả hàng. Hàng đọc/ghi thẳng vào
/// `AppSettings.preferences`; mỗi lần đổi tự gửi sang bàn phím.
final class SettingsViewController: UITableViewController {
    struct Row {
        enum Kind {
            case toggle(WritableKeyPath<KeyboardConfiguration, Bool>)
            case choice([String], WritableKeyPath<KeyboardConfiguration, Int>)
            case slider(ClosedRange<Double>, step: Double, WritableKeyPath<KeyboardConfiguration, Double>, format: (Double) -> String)
            case navigate(() -> UIViewController)
            case action((SettingsViewController) -> Void)
            case destructive((SettingsViewController) -> Void)
            case checked(Bool, (SettingsViewController) -> Void)
            case accent
            case text
        }
        var title: String
        var detail: String? = nil
        var id: String
        var kind: Kind
        /// Thao tác khi vuốt trái hàng (vd. đổi tên, xoá).
        var swipe: [UIContextualAction] = []
    }
    struct Section { var header: String? = nil; var footer: String? = nil; var rows: [Row] }

    let settings: AppSettings
    private let build: (SettingsViewController) -> [Section]
    private var sections: [Section] = []
    var preferences: KeyboardPreferences { settings.preferences }

    init(title: String, settings: AppSettings = .shared, build: @escaping (SettingsViewController) -> [Section]) {
        self.settings = settings; self.build = build
        super.init(style: .insetGrouped)
        self.title = title
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is unsupported") }
    override func viewDidLoad() {
        super.viewDidLoad()
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "cell")
        settings.files.presenter = self
        settings.files.onMessage = { [weak self] in self?.toast($0); self?.reload() }
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        settings.files.presenter = self
        // Profile bàn phím báo về (kéo đổi chỗ phím, đổi profile nhanh) có thể vừa đổi trong lúc dùng bàn phím ngay trong app.
        settings.adoptKeyboardProfiles()
        reload()
    }
    func reload() { sections = build(self); tableView.reloadData() }

    override func numberOfSections(in tableView: UITableView) -> Int { sections.count }
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { sections[section].rows.count }
    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? { sections[section].header }
    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? { sections[section].footer }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let row = sections[indexPath.section].rows[indexPath.row]
        let cell = UITableViewCell(style: .default, reuseIdentifier: nil)
        cell.accessibilityIdentifier = row.id
        var content = UIListContentConfiguration.valueCell()
        content.text = row.title; content.secondaryText = row.detail
        content.textProperties.numberOfLines = 0
        cell.selectionStyle = .none
        switch row.kind {
        case let .toggle(path):
            let control = UISwitch(); control.isOn = preferences.configuration[keyPath: path]; control.accessibilityIdentifier = row.id
            control.addAction(UIAction { [weak self, weak control] _ in
                guard let self, let control else { return }; self.preferences.configuration[keyPath: path] = control.isOn
            }, for: .valueChanged)
            cell.accessoryView = control
            content = UIListContentConfiguration.cell(); content.text = row.title; content.textProperties.numberOfLines = 0
            content.secondaryText = row.detail; content.secondaryTextProperties.color = .secondaryLabel
            cell.contentConfiguration = content
        case let .choice(values, path):
            let control = UISegmentedControl(items: values); control.selectedSegmentIndex = preferences.configuration[keyPath: path]
            control.accessibilityIdentifier = row.id; control.accessibilityLabel = row.title
            control.addAction(UIAction { [weak self, weak control] _ in
                guard let self, let control else { return }; self.preferences.configuration[keyPath: path] = control.selectedSegmentIndex
            }, for: .valueChanged)
            embed(control, in: cell, title: row.title.isEmpty ? nil : row.title)
        case let .slider(range, step, path, format):
            let value = UILabel(); value.font = .preferredFont(forTextStyle: .subheadline); value.textColor = .secondaryLabel
            let control = UISlider(); control.minimumValue = Float(range.lowerBound); control.maximumValue = Float(range.upperBound)
            control.value = Float(preferences.configuration[keyPath: path]); control.accessibilityIdentifier = row.id
            control.accessibilityLabel = row.title
            value.text = format(preferences.configuration[keyPath: path])
            control.addAction(UIAction { [weak control, weak value] _ in
                guard let control else { return }
                let snapped = (Double(control.value) / step).rounded() * step
                value?.text = format(snapped)
            }, for: .valueChanged)
            // Lưu khi thả tay để không gửi sang bàn phím mỗi bước kéo.
            control.addAction(UIAction { [weak self, weak control] _ in
                guard let self, let control else { return }
                self.preferences.configuration[keyPath: path] = min(range.upperBound, max(range.lowerBound, (Double(control.value) / step).rounded() * step))
            }, for: [.touchUpInside, .touchUpOutside, .touchCancel])
            let header = UIStackView(arrangedSubviews: [label(row.title), value]); header.distribution = .equalSpacing
            embed(UIStackView(arrangedSubviews: [header, control]).vertical(spacing: 6), in: cell, title: nil)
        case .navigate:
            cell.accessoryType = .disclosureIndicator; cell.selectionStyle = .default
            cell.contentConfiguration = content
        case .action:
            content = UIListContentConfiguration.cell(); content.text = row.title; content.textProperties.color = view.tintColor
            content.secondaryText = row.detail; content.secondaryTextProperties.color = .secondaryLabel
            cell.selectionStyle = .default; cell.contentConfiguration = content; cell.accessibilityTraits = .button
        case .destructive:
            content = UIListContentConfiguration.cell(); content.text = row.title; content.textProperties.color = .systemRed
            cell.selectionStyle = .default; cell.contentConfiguration = content; cell.accessibilityTraits = .button
        case let .checked(on, _):
            cell.accessoryType = on ? .checkmark : .none; cell.selectionStyle = .default
            cell.contentConfiguration = content
            if on { cell.accessibilityTraits.insert(.selected) }
        case .accent:
            embed(accentPicker(), in: cell, title: nil)
        case .text:
            content = UIListContentConfiguration.cell(); content.text = row.title; content.textProperties.font = .preferredFont(forTextStyle: .subheadline)
            content.textProperties.numberOfLines = 0; cell.contentConfiguration = content
        }
        return cell
    }
    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        switch sections[indexPath.section].rows[indexPath.row].kind {
        case let .navigate(make): navigationController?.pushViewController(make(), animated: true)
        case let .action(run), let .destructive(run), let .checked(_, run): run(self); reload()
        default: break
        }
    }
    override func tableView(_ tableView: UITableView, trailingSwipeActionsConfigurationForRowAt indexPath: IndexPath) -> UISwipeActionsConfiguration? {
        let actions = sections[indexPath.section].rows[indexPath.row].swipe
        return actions.isEmpty ? nil : UISwipeActionsConfiguration(actions: actions)
    }

    private func label(_ text: String) -> UILabel {
        let label = UILabel(); label.text = text; label.numberOfLines = 0; label.font = .preferredFont(forTextStyle: .body)
        label.adjustsFontForContentSizeCategory = true
        return label
    }
    private func embed(_ control: UIView, in cell: UITableViewCell, title: String?) {
        let stack = UIStackView(arrangedSubviews: (title.map { [label($0)] } ?? []) + [control]).vertical(spacing: 8)
        stack.translatesAutoresizingMaskIntoConstraints = false
        cell.contentView.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: cell.contentView.layoutMarginsGuide.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: cell.contentView.layoutMarginsGuide.trailingAnchor),
            stack.topAnchor.constraint(equalTo: cell.contentView.topAnchor, constant: 10),
            stack.bottomAnchor.constraint(equalTo: cell.contentView.bottomAnchor, constant: -10)
        ])
    }
    /// Màu nhấn: mỗi màu một cột (ô tròn + tên), ô đang dùng có ✓. Màu tô phím nguyên âm, ↵ và điều khiển trên bàn phím.
    private func accentPicker() -> UIView {
        let row = UIStackView(); row.distribution = .fillEqually; row.alignment = .top
        let dark = traitCollection.userInterfaceStyle == .dark
        for (index, choice) in KeyboardAccent.allCases.enumerated() {
            let selected = preferences.configuration.accent == index
            let button = UIButton(type: .custom)
            button.backgroundColor = choice.color(dark: dark); button.layer.cornerRadius = 17; button.tintColor = .white
            if selected { button.setImage(UIImage(systemName: "checkmark", withConfiguration: UIImage.SymbolConfiguration(pointSize: 14, weight: .bold)), for: .normal) }
            button.accessibilityIdentifier = "accent-\(index)"; button.accessibilityLabel = KeyboardAccent.names[index]
            if selected { button.accessibilityTraits.insert(.selected) }
            button.widthAnchor.constraint(equalToConstant: 34).isActive = true; button.heightAnchor.constraint(equalToConstant: 34).isActive = true
            button.addAction(UIAction { [weak self] _ in self?.preferences.configuration.accent = index; self?.reload() }, for: .touchUpInside)
            let name = UILabel(); name.text = KeyboardAccent.names[index]; name.font = .preferredFont(forTextStyle: .caption2)
            name.textColor = .secondaryLabel; name.textAlignment = .center; name.isAccessibilityElement = false
            let column = UIStackView(arrangedSubviews: [button, name]); column.axis = .vertical; column.alignment = .center; column.spacing = 4
            row.addArrangedSubview(column)
        }
        return row
    }
    /// Thông báo ngắn nổi phía dưới, tự ẩn; không chặn thao tác như hộp thoại.
    func toast(_ text: String) {
        guard let host = navigationController?.view ?? view else { return }
        host.subviews.filter { $0.accessibilityIdentifier == "settings-toast" }.forEach { $0.removeFromSuperview() }
        let label = PaddedLabel(); label.text = text; label.numberOfLines = 0; label.textAlignment = .center
        label.font = .preferredFont(forTextStyle: .subheadline); label.textColor = .systemBackground; label.backgroundColor = .label
        label.layer.cornerRadius = 18; label.clipsToBounds = true; label.accessibilityIdentifier = "settings-toast"
        label.translatesAutoresizingMaskIntoConstraints = false
        host.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: host.centerXAnchor),
            label.widthAnchor.constraint(lessThanOrEqualTo: host.widthAnchor, constant: -48),
            label.bottomAnchor.constraint(equalTo: host.safeAreaLayoutGuide.bottomAnchor, constant: -24)
        ])
        UIAccessibility.post(notification: .announcement, argument: text)
        UIView.animate(withDuration: 0.3, delay: 2.2, options: []) { label.alpha = 0 } completion: { _ in label.removeFromSuperview() }
    }
    func confirm(_ title: String, message: String, action: String, run: @escaping () -> Void) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Huỷ", style: .cancel))
        alert.addAction(UIAlertAction(title: action, style: .destructive) { _ in run() })
        present(alert, animated: true)
    }
}

extension UIStackView {
    func vertical(spacing: CGFloat) -> UIStackView { axis = .vertical; self.spacing = spacing; return self }
}
