import UIKit

extension HexKeyboardView {
    /// Bảng con quay về bảng nào khi chạm "‹" ở thanh trên.
    static let panelParents = ["confirm-clear": "settings"]
    static let panelTitles = ["settings": "Cài đặt nhanh", "confirm-clear": "Xoá dữ liệu học", "candidates": "Gợi ý", "emoji": "Emoji", "clipboard": "Clipboard"]
    enum RowStyle { case navigate, action, destructive, checked, plain }

    func closePanel() {
        panelBack.isHidden = true; panelUp.isHidden = true; panelTitle.isHidden = true; backspace.isHidden = false
        mode.isHidden = false; globe.isHidden = !showsGlobe
        visiblePanel = nil; editLayer = nil; selectedCell = nil; panel.isHidden = true; currentSection = nil
        inlineDocuments = []; inlineSave = nil; inlineCancel = nil; emojiReturnEditor = nil; editorBar.isHidden = true
        more.isHidden = suggestions.isEmpty; candidateScroll.isHidden = false
        keys.forEach { $0.0.isHidden = false }; reloadKeys()
    }
    /// Mở một bảng: thanh trên có "‹ bảng cha" (nếu có), tiêu đề và "Xong"; ⌫ chỉ còn ở bảng emoji và gợi ý.
    func startPanel(_ name: String) {
        visiblePanel = name; editLayer = nil; selectedCell = nil; panel.isHidden = false; editorBar.isHidden = true
        panel.setContentOffset(.zero,animated: false); hidePopup()
        keys.forEach { $0.0.isHidden = true }
        panelStack.arrangedSubviews.forEach { $0.removeFromSuperview() }; currentSection = nil
        panelBack.isHidden = false; mode.isHidden = true; globe.isHidden = true
        panelTitle.text = Self.panelTitles[name]; panelTitle.isHidden = false
        if let parent = Self.panelParents[name] { panelUp.setTitle(Self.panelTitles[parent], for: .normal); panelUp.isHidden = false }
        else { panelUp.isHidden = true }
        backspace.isHidden = !(name == "emoji" || name == "candidates")
        candidateScroll.isHidden = true; more.isHidden = true
    }
    /// Bắt đầu một nhóm ô bo góc; `header` là tiêu đề nhỏ phía trên như danh sách nhóm của iOS.
    func section(_ header: String? = nil) {
        endSection()
        if let header {
            let label = PaddedLabel(); label.insets = UIEdgeInsets(top: 6, left: 16, bottom: 6, right: 16)
            label.text = header.uppercased(); label.font = .systemFont(ofSize: 12, weight: .regular); label.textColor = theme.secondaryFg
            label.numberOfLines = 0; label.accessibilityTraits = .header
            panelStack.addArrangedSubview(label)
        }
        let group = UIStackView(); group.axis = .vertical; group.spacing = 0
        group.backgroundColor = theme.cellBg; group.layer.cornerRadius = 10; group.clipsToBounds = true
        panelStack.addArrangedSubview(group); currentSection = group
    }
    func endSection() {
        if let group = currentSection { panelStack.setCustomSpacing(20, after: group) }
        currentSection = nil
    }
    /// Thêm một ô vào nhóm đang mở (tự mở nhóm mới nếu chưa có), kẻ vạch mảnh giữa các ô.
    func addCell(_ view: UIView) {
        if currentSection == nil { section() }
        guard let group = currentSection else { return }
        if !group.arrangedSubviews.isEmpty {
            let line = UIView(); line.isUserInteractionEnabled = false
            let ink = UIView(); ink.backgroundColor = theme.separator; ink.translatesAutoresizingMaskIntoConstraints = false
            line.addSubview(ink)
            NSLayoutConstraint.activate([
                line.heightAnchor.constraint(equalToConstant: 1 / max(1, UIScreen.main.scale)),
                ink.leadingAnchor.constraint(equalTo: line.leadingAnchor, constant: 16), ink.trailingAnchor.constraint(equalTo: line.trailingAnchor),
                ink.topAnchor.constraint(equalTo: line.topAnchor), ink.bottomAnchor.constraint(equalTo: line.bottomAnchor)
            ])
            group.addArrangedSubview(line)
        }
        group.addArrangedSubview(view)
    }
    /// Một ô bấm được: › cho ô mở bảng con, ✓ cho lựa chọn đang dùng, chữ màu nhấn cho thao tác, đỏ cho xoá.
    /// `trailing`: nút phụ đặt cạnh hàng bên phải (vd. ghim), ngoài vùng chạm của hàng.
    @discardableResult func row(_ title: String, id: String, style: RowStyle = .navigate, detail: String? = nil, icon: (String, UInt32)? = nil, trailing: UIView? = nil, action: @escaping (HexKeyboardView) -> Void) -> ListRowButton {
        let button = ListRowButton(type: .custom)
        configure(button,title,id) { [weak self] in if let self { action(self) } }
        button.titleLabel?.font = .systemFont(ofSize: 17); button.titleLabel?.numberOfLines = 2; button.titleLabel?.lineBreakMode = .byTruncatingTail
        button.normalColor = .clear; button.pressedColor = theme.cellPressedBg
        button.setTitleColor(style == .action ? theme.accent : style == .destructive ? UIColor.systemRed : theme.keyFg, for: .normal)
        button.contentHorizontalAlignment = .leading
        var left: CGFloat = 16
        if let icon {
            let tile = UIImageView(image: UIImage(systemName: icon.0, withConfiguration: UIImage.SymbolConfiguration(pointSize: 15, weight: .medium)))
            tile.tintColor = .white; tile.backgroundColor = UIColor(hex: icon.1); tile.contentMode = .center
            tile.layer.cornerRadius = 7; tile.clipsToBounds = true; tile.isUserInteractionEnabled = false
            // Giữa hàng theo chiều dọc dù hàng cao bao nhiêu (chữ có dấu làm hàng cao hơn 46pt).
            tile.translatesAutoresizingMaskIntoConstraints = false; button.addSubview(tile); left = 57
            NSLayoutConstraint.activate([
                tile.leadingAnchor.constraint(equalTo: button.leadingAnchor, constant: 16), tile.centerYAnchor.constraint(equalTo: button.centerYAnchor),
                tile.widthAnchor.constraint(equalToConstant: 29), tile.heightAnchor.constraint(equalToConstant: 29)
            ])
        }
        let symbol: String? = style == .navigate ? "chevron.right" : style == .checked ? "checkmark" : nil
        if let symbol {
            button.accessory.image = UIImage(systemName: symbol, withConfiguration: UIImage.SymbolConfiguration(pointSize: style == .checked ? 16 : 14, weight: .semibold))
            button.accessory.tintColor = style == .checked ? theme.accent : theme.secondaryFg
        }
        if style == .checked { button.accessibilityTraits.insert(.selected) }
        button.detail.text = detail; button.detail.textColor = theme.secondaryFg
        let right: CGFloat = 16 + (symbol == nil ? 0 : 26) + (detail == nil ? 0 : min(150, button.detail.intrinsicContentSize.width + 6))
        button.contentEdgeInsets = UIEdgeInsets(top: 11, left: left, bottom: 11, right: right)
        button.heightAnchor.constraint(greaterThanOrEqualToConstant: 46).isActive = true
        if let trailing {
            let container = UIView()
            [button, trailing].forEach { $0.translatesAutoresizingMaskIntoConstraints = false; container.addSubview($0) }
            trailing.setContentHuggingPriority(.required, for: .horizontal); trailing.setContentCompressionResistancePriority(.required, for: .horizontal)
            NSLayoutConstraint.activate([
                button.leadingAnchor.constraint(equalTo: container.leadingAnchor), button.topAnchor.constraint(equalTo: container.topAnchor),
                button.bottomAnchor.constraint(equalTo: container.bottomAnchor), button.trailingAnchor.constraint(equalTo: trailing.leadingAnchor),
                trailing.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -8), trailing.centerYAnchor.constraint(equalTo: container.centerYAnchor),
                trailing.widthAnchor.constraint(greaterThanOrEqualToConstant: 44), trailing.heightAnchor.constraint(equalToConstant: 34)
            ])
            addCell(container)
        } else { addCell(button) }
        return button
    }
    /// Chú thích dưới nhóm (đóng nhóm đang mở).
    func note(_ text: String) {
        endSection()
        let label = PaddedLabel(); label.insets = UIEdgeInsets(top: 2, left: 16, bottom: 4, right: 16)
        label.text = text; label.numberOfLines = 0; label.font = .systemFont(ofSize: 13); label.textColor = theme.secondaryFg
        panelStack.addArrangedSubview(label); panelStack.setCustomSpacing(18, after: label)
    }
    private func cell(_ views: [UIView], axis: NSLayoutConstraint.Axis = .horizontal) -> UIStackView {
        let stack = UIStackView(arrangedSubviews: views); stack.axis = axis; stack.spacing = axis == .horizontal ? 12 : 8
        stack.alignment = axis == .horizontal ? .center : .fill
        stack.isLayoutMarginsRelativeArrangement = true; stack.layoutMargins = UIEdgeInsets(top: 8, left: 16, bottom: 8, right: 16)
        stack.heightAnchor.constraint(greaterThanOrEqualToConstant: 46).isActive = true
        return stack
    }
    private func cellLabel(_ title: String) -> UILabel {
        let label = UILabel(); label.text = title; label.numberOfLines = 2; label.font = .systemFont(ofSize: 17); label.textColor = theme.keyFg
        label.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        return label
    }
    func toggle(_ title: String, id: String, path: WritableKeyPath<KeyboardConfiguration,Bool>) {
        let control = UISwitch(); control.isOn = preferences.configuration[keyPath: path]; control.accessibilityIdentifier = id
        control.onTintColor = theme.accent
        control.setContentCompressionResistancePriority(.required, for: .horizontal)
        control.addAction(UIAction { [weak self, weak control] _ in
            guard let self, let control else { return }
            self.preferences.configuration[keyPath: path] = control.isOn; self.onPreferencesChanged?()
        },for: .valueChanged)
        addCell(cell([cellLabel(title), control]))
    }
    /// ⚙ trên bàn phím chỉ còn thao tác nhanh và phần dữ liệu chỉ bàn phím giữ (dữ liệu học, sao lưu đầy đủ); các cài đặt
    /// khác chỉnh trong app TeleHex và tới bàn phím qua App Group.
    func show(_ name: String) {
        startPanel(name)
        switch name {
        case "settings":
            section("Profile")
            for profile in preferences.allProfiles {
                row(profile.name,id: "profile-\(profile.id)",style: profile.id == preferences.profile.id ? .checked : .plain,
                    detail: profile.builtIn ? (profile.spec.isHex ? "Tổ ong" : "QWERTY") : nil) { view in
                    view.preferences.configuration.activeProfile = profile.id; view.onPreferencesChanged?(); view.show("settings")
                }
            }
            section("Quyền riêng tư")
            toggle("Ẩn danh — không học từ đã gõ",id: "incognito",path: \.incognito)
            section()
            row("Emoji",id: "open-emoji",icon: ("face.smiling.inverse",0xF9AB00)) { $0.openEmoji() }
            if clipboardEnabled { row("Clipboard",id: "open-clipboard",icon: ("doc.on.clipboard.fill",0x5F6368)) { $0.show("clipboard") } }
            section("Dữ liệu")
            row("Sao lưu đầy đủ…",id: "export-backup",style: .action) { $0.onCommand?("export-backup") }
            row("Khôi phục từ tệp…",id: "import-backup",style: .action) { $0.onCommand?("import-backup") }
            row("Xoá dữ liệu đã học…",id: "clear-personal",style: .destructive) { $0.show("confirm-clear") }
            note("Bản sao lưu đầy đủ gồm cả từ đã học. Giao diện, cử chỉ, gõ tắt, gói ngôn ngữ và chỉnh phím nằm trong app TeleHex.")
        case "confirm-clear":
            note("Chỉ xoá từ tiếng Việt đã học. Các gói VI/EN và profile được giữ lại.")
            section()
            row("Xác nhận xoá dữ liệu đã học",id: "confirm-clear-personal",style: .destructive) { $0.onCommand?("clear-personal"); $0.show("settings") }
            endSection()
        case "emoji": showEmoji()
        case "clipboard": showClipboard()
        default: closePanel()
        }
        setNeedsLayout()
    }
    /// Lịch sử clipboard: chạm để dán, nút ghim giữ mục lâu; mục thường tự xoá sau 1 giờ.
    func showClipboard() {
        // Ghi chữ đang chép khi người dùng mở bảng (iOS có thể hỏi quyền dán).
        clipboard?.capture()
        let items = clipboard?.items ?? []
        if items.isEmpty { note("Chép chữ ở ứng dụng bất kỳ để lưu vào đây. Mục thường tự xoá sau 1 giờ; ghim để giữ lâu.") }
        for (header, group) in [("Đã ghim", items.filter(\.pinned)), ("Gần đây", items.filter { !$0.pinned })] where !group.isEmpty {
            section(header)
            for item in group {
                let pin = UIButton(type: .system)
                configure(pin, "", "clipboard-pin") { [weak self] in self?.clipboard?.togglePin(item.text); self?.show("clipboard") }
                pin.setImage(UIImage(systemName: item.pinned ? "pin.fill" : "pin", withConfiguration: UIImage.SymbolConfiguration(pointSize: 16, weight: .regular)), for: .normal)
                pin.tintColor = theme.accent; pin.accessibilityLabel = item.pinned ? "Bỏ ghim" : "Ghim"
                let preview = item.text.replacingOccurrences(of: "\n", with: " ")
                let button = row(preview, id: "clipboard-item", style: .plain, trailing: pin) { view in
                    view.closePanel(); view.onInsertText?(item.text)
                }
                button.accessibilityHint = "Chạm để dán"
            }
        }
        if items.contains(where: { !$0.pinned }) {
            section()
            row("Xoá các mục chưa ghim",id: "clipboard-clear",style: .destructive) { $0.clipboard?.clearUnpinned(); $0.show("clipboard") }
        }
        endSection()
    }
    func showCandidates() {
        startPanel("candidates")
        note("Chạm để chèn · Giữ để xoá từ đã học")
        grid(suggestions.map { ($0,$0) }, prefix: "expanded-candidate") { view, text in view.onCandidate?(text); view.closePanel() }
        setNeedsLayout()
    }
    /// Lưới ô chọn: 3 cột ô sáng (gợi ý mở rộng) hoặc 8 cột emoji không nền.
    func grid(_ values: [(String,String)], prefix: String, columns: Int = 3, action: @escaping (HexKeyboardView,String) -> Void) {
        endSection()
        let emoji = prefix == "emoji"
        for start in stride(from: 0,to: values.count,by: columns) {
            let row = UIStackView(); row.spacing = 6; row.distribution = .fillEqually
            for (title,value) in values[start..<min(values.count,start+columns)] {
                let button = PillButton(type: .custom)
                configure(button,title,"\(prefix)-\(value)") { [weak self] in if let self { action(self,value) } }
                if prefix == "expanded-candidate" { button.addGestureRecognizer(UILongPressGestureRecognizer(target: self,action: #selector(forgetCandidate(_:)))) }
                button.titleLabel?.font = .systemFont(ofSize: emoji ? 28 : 18); button.titleLabel?.numberOfLines = 2
                button.normalColor = emoji ? .clear : theme.cellBg; button.pressedColor = emoji ? barPressed : theme.cellPressedBg; button.layer.cornerRadius = 8
                button.heightAnchor.constraint(greaterThanOrEqualToConstant: emoji ? 44 : 46).isActive = true
                row.addArrangedSubview(button)
            }
            for _ in row.arrangedSubviews.count..<columns { row.addArrangedSubview(UIView()) }
            panelStack.addArrangedSubview(row); panelStack.setCustomSpacing(6, after: row)
        }
    }
    func message(_ text: String) {
        showBanner(text)
        let task = DispatchWorkItem { [weak self] in self?.hideBanner() }
        bannerTask = task
        DispatchQueue.main.asyncAfter(deadline: .now()+2.5, execute: task)
    }
    func startLayoutEditor(_ layer: Int) {
        guard (0...3).contains(layer) else { return }
        if let selected = selectedCell, selected.0 != layer && !(selected.0 >= 2 && layer >= 2) {
            selectedCell = nil
        }
        // VI/EN và ⌫ không dùng khi sửa lớp phím: nhường cả thanh trên cho sáu nút chọn lớp.
        panelBack.isHidden = true; panelUp.isHidden = true; panelTitle.isHidden = true; backspace.isHidden = true
        mode.isHidden = true; globe.isHidden = !showsGlobe
        visiblePanel = nil; panel.isHidden = true; editLayer = layer; page = -1; dialpad = false
        editorBar.arrangedSubviews.forEach { $0.removeFromSuperview() }; editorBar.isHidden = false; editorBar.distribution = .fillEqually
        for (title,value) in [("Chữ",0),("Vuốt ↓",1),("123·1",2),("123·2",3),("↺",5),("Xong",4)] {
            let button = PillButton(type: .custom)
            configure(button,title,"layout-layer-\(value)") { [weak self] in
                guard let self else { return }
                if value == 5 {
                    var profile = self.preferences.profile; let defaults = KeyboardProfile.builtIn(for: profile.spec)
                    if layer == 0 { profile.letters = defaults.letters; profile.reconcile() }
                    else if layer == 1 { profile.swipe = defaults.swipe; profile.reconcile() }
                    else { profile.symbols[layer - 2] = defaults.symbols[layer - 2]; profile.reconcile() }
                    if self.preferences.saveProfile(profile) { self.onPreferencesChanged?(); self.startLayoutEditor(layer) }
                }
                else if value == 4 {
                    self.editLayer = nil; self.editorBar.isHidden = true
                    if let done = self.onLayoutEditorDone { done() } else { self.closePanel() }
                }
                else { self.startLayoutEditor(value) }
            }
            button.titleLabel?.font = .systemFont(ofSize: 14, weight: value == 4 ? .semibold : .regular); button.layer.cornerRadius = 8
            style(chip: button, selected: value == layer || value == 4)
            editorBar.addArrangedSubview(button)
        }
        reloadKeys(); message(layer == 1 ? "Chạm để sửa · Giữ kéo để đổi chỗ" : "Chạm hai phím hoặc giữ kéo để đổi chỗ")
    }
    func editCell(_ index: Int, layer: Int) {
        guard preferences.profile.letters.indices.contains(index), !KeyboardProfile.fixed(preferences.profile.letters[index]) else { return }
        if layer == 1 {
            guard KeyboardProfile.swipeUsable(preferences.profile.letters[index]) else { return }
            // ✕ về lại trình sửa lớp vuốt xuống chứ không về Cài đặt.
            beginEditor(title: "Ký tự vuốt xuống",fields: ["Nội dung"],values: [preferences.profile.swipe[index]],ascii: [false],
                        cancel: { [weak self] in self?.closePanel(); self?.startLayoutEditor(1) }) { [weak self] values in
                guard let self else { return }; var profile = self.preferences.profile; profile.swipe[index] = values[0]
                if self.preferences.saveProfile(profile) { self.onPreferencesChanged?(); self.inlineDocuments=[]; self.inlineSave=nil; self.startLayoutEditor(1) }
                else { self.message("Nội dung quá dài") }
            }; return
        }
        if let first = selectedCell {
            swapCells(first.1,index,layer: layer,sourceLayer: first.0); selectedCell = nil
        } else { selectedCell = (layer,index); message("Chạm phím đích; đổi trang 123 để chuyển sang trang khác") }
    }
    func swapCells(_ a: Int, _ b: Int, layer: Int, sourceLayer: Int? = nil) {
        var profile = preferences.profile
        guard profile.letters.indices.contains(a), profile.letters.indices.contains(b),
              !KeyboardProfile.fixed(profile.letters[a]), !KeyboardProfile.fixed(profile.letters[b]) else { return }
        if layer == 0 {
            guard KeyboardProfile.isLetter(profile.letters[a]) || KeyboardProfile.isLetter(profile.letters[b]) else { return }
            profile.letters.swapAt(a,b); profile.reconcile()
        } else if layer == 1 {
            guard KeyboardProfile.swipeUsable(profile.letters[a]), KeyboardProfile.swipeUsable(profile.letters[b]) else { return }
            profile.swipe.swapAt(a,b)
        }
        else {
            guard KeyboardProfile.symbolUsable(profile.letters[a]), KeyboardProfile.symbolUsable(profile.letters[b]) else { return }
            let first = (sourceLayer ?? layer) - 2, second = layer - 2
            guard profile.symbols.indices.contains(first), profile.symbols.indices.contains(second) else { return }
            let value = profile.symbols[first][a]; profile.symbols[first][a] = profile.symbols[second][b]; profile.symbols[second][b] = value
        }
        if preferences.saveProfile(profile) { onPreferencesChanged?(); reloadKeys() }
    }
    func beginEditor(title: String, fields: [String], values: [String], ascii: [Bool], cancel: (() -> Void)? = nil, save: @escaping ([String]) -> Void) {
        closePanel(); inlineSession.configure(preferences); inlineNames = fields; inlineDocuments = values.map(EditorDocument.init); inlineAscii = ascii
        inlineIndex = 0; inlineSession.reset(); inlineSession.englishMode = ascii[0]; inlineSave = save; inlineCancel = cancel; page = -1; dialpad = false
        inlineTitle = title
        updateEditorBar(); reloadKeys()
    }
    func updateEditorBar() {
        editorBar.arrangedSubviews.forEach { $0.removeFromSuperview() }; editorBar.isHidden = false; editorBar.distribution = .fill
        candidateScroll.isHidden = true; more.isHidden = true
        panelTitle.text = inlineTitle; panelTitle.isHidden = false
        // Hàng ô nhập rộng hết bàn phím: tên ô nhỏ màu phụ, nội dung cỡ lớn; ô đang sửa có viền màu nhấn và con trỏ.
        // Các ô nhập chia đều phần còn lại, ✕ ✓ rộng cố định.
        // Các ô nhập chia đều phần còn lại; ✕ ✓ rộng cố định.
        var first: UIView?
        for index in inlineDocuments.indices {
            let button = EditorFieldButton(type: .custom)
            configure(button,"","editor-field-\(index)") { [weak self] in
                guard let self else { return }; self.inlineIndex=index; self.inlineSession.reset(); self.inlineSession.englishMode = self.inlineAscii[index]; self.updateEditorBar()
            }
            let isActive = index == inlineIndex
            button.caption.text = inlineNames[index]; button.caption.textColor = isActive ? theme.accent : theme.secondaryFg
            button.value.text = inlineDocuments[index].text + (isActive ? "\u{258F}" : ""); button.value.textColor = theme.keyFg
            button.accessibilityLabel = inlineNames[index]; button.accessibilityValue = inlineDocuments[index].text
            button.layer.cornerRadius = 10; button.normalColor = theme.cellBg; button.pressedColor = theme.cellPressedBg
            button.layer.borderWidth = isActive ? 2 : 0; button.layer.borderColor = theme.accent.cgColor
            button.setContentCompressionResistancePriority(.defaultLow, for: .horizontal); button.setContentHuggingPriority(.defaultLow, for: .horizontal)
            editorBar.addArrangedSubview(button)
            if let first { button.widthAnchor.constraint(equalTo: first.widthAnchor).isActive = true } else { first = button }
        }
        let cancel = PillButton(type: .custom); configure(cancel,"✕","cancel-editor") { [weak self] in
            guard let self else { return }
            if let action = self.inlineCancel { action() }
            else { self.closePanel(); self.show("settings") }
        }
        cancel.accessibilityLabel = "Huỷ"; cancel.titleLabel?.font = .systemFont(ofSize: 18, weight: .medium); cancel.layer.cornerRadius = 10; style(chip: cancel, selected: false)
        cancel.widthAnchor.constraint(equalToConstant: 44).isActive = true
        editorBar.addArrangedSubview(cancel)
        let done = PillButton(type: .custom); configure(done,"✓","save-editor") { [weak self] in
            guard let self, let save = self.inlineSave else { return }
            let values = self.inlineDocuments.map(\.text); self.inlineDocuments=[]; self.inlineSave=nil; self.editorBar.isHidden=true; self.panelTitle.isHidden=true; self.more.isHidden=self.suggestions.isEmpty; self.candidateScroll.isHidden=false
            save(values)
        }
        done.accessibilityLabel = "Lưu"; done.titleLabel?.font = .systemFont(ofSize: 18, weight: .bold); done.layer.cornerRadius = 10; style(chip: done, selected: true)
        done.widthAnchor.constraint(equalToConstant: 44).isActive = true
        editorBar.addArrangedSubview(done)
        mode.setTitle(inlineSession.englishMode ? "EN" : "VI",for: .normal)
        setNeedsLayout()
    }
    func openEmoji() {
        if !inlineDocuments.isEmpty {
            emojiReturnEditor = InlineEditorState(title: inlineTitle,documents: inlineDocuments,names: inlineNames,ascii: inlineAscii,index: inlineIndex,session: inlineSession,save: inlineSave,cancel: inlineCancel)
            inlineDocuments = []; inlineSave = nil; inlineCancel = nil
            inlineSession = TypingSession()
        }
        query = ""; category = nil; emojiPage = 0
        showEmoji()
    }
    func returnFromEmoji(inserting value: String? = nil) {
        let editor = emojiReturnEditor
        closePanel()
        if let editor {
            inlineTitle = editor.title; inlineDocuments = editor.documents; inlineNames = editor.names; inlineAscii = editor.ascii
            inlineIndex = editor.index; inlineSession = editor.session; inlineSave = editor.save; inlineCancel = editor.cancel
            page = -1; dialpad = false
            updateEditorBar(); reloadKeys()
        }
        if let value { type(value) }
    }
    func searchEmoji() {
        let editor = emojiReturnEditor
        beginEditor(title: "Tìm emoji",fields: ["Tìm"],values: [query],ascii: [false],cancel: { [weak self] in
            self?.closePanel(); self?.emojiReturnEditor = editor; self?.showEmoji()
        }) { [weak self] values in
            self?.closePanel(); self?.emojiReturnEditor = editor
            self?.query = values[0]; self?.emojiPage = 0; self?.showEmoji()
        }
    }
    func showEmoji() {
        startPanel("emoji")
        // Ô tìm kiếm: chạm để gõ từ khoá bằng chính bàn phím.
        let search = PillButton(type: .custom)
        configure(search, query.isEmpty ? "Tìm emoji…" : query, "search-emoji") { [weak self] in self?.searchEmoji() }
        search.setImage(UIImage(systemName: "magnifyingglass", withConfiguration: UIImage.SymbolConfiguration(pointSize: 15, weight: .medium)), for: .normal)
        search.tintColor = theme.secondaryFg; search.setTitleColor(query.isEmpty ? theme.secondaryFg : theme.keyFg, for: .normal)
        search.titleLabel?.font = .systemFont(ofSize: 17); search.contentHorizontalAlignment = .leading
        search.contentEdgeInsets = UIEdgeInsets(top: 0, left: 10, bottom: 0, right: 10); search.titleEdgeInsets = UIEdgeInsets(top: 0, left: 6, bottom: 0, right: -6)
        search.normalColor = theme.cellBg; search.pressedColor = theme.cellPressedBg; search.layer.cornerRadius = 10
        search.heightAnchor.constraint(equalToConstant: 38).isActive = true
        panelStack.addArrangedSubview(search); panelStack.setCustomSpacing(8, after: search)
        // Nhóm emoji: hàng chip cuộn ngang, nhóm đang chọn tô màu nhấn.
        let names = Array(NSOrderedSet(array: emoji?.entries.map(\.category) ?? [])).compactMap { $0 as? String }
        let chips = UIStackView(); chips.spacing = 6
        for name in [nil] + names.map(Optional.some) {
            let chip = PillButton(type: .custom)
            configure(chip, name ?? "Tất cả", "emoji-category-\(name ?? "all")") { [weak self] in
                guard let self else { return }; self.category = name; self.emojiPage = 0; self.showEmoji()
            }
            chip.titleLabel?.font = .systemFont(ofSize: 14); chip.layer.cornerRadius = 15
            chip.contentEdgeInsets = UIEdgeInsets(top: 0, left: 12, bottom: 0, right: 12)
            chip.heightAnchor.constraint(equalToConstant: 30).isActive = true
            style(chip: chip, selected: name == category)
            chips.addArrangedSubview(chip)
        }
        let strip = PanelScrollView(); strip.showsHorizontalScrollIndicator = false; strip.addSubview(chips)
        chips.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            chips.leadingAnchor.constraint(equalTo: strip.contentLayoutGuide.leadingAnchor), chips.trailingAnchor.constraint(equalTo: strip.contentLayoutGuide.trailingAnchor),
            chips.topAnchor.constraint(equalTo: strip.contentLayoutGuide.topAnchor), chips.bottomAnchor.constraint(equalTo: strip.contentLayoutGuide.bottomAnchor),
            chips.heightAnchor.constraint(equalTo: strip.frameLayoutGuide.heightAnchor), strip.heightAnchor.constraint(equalToConstant: 30)
        ])
        panelStack.addArrangedSubview(strip); panelStack.setCustomSpacing(8, after: strip)
        let matches = emoji?.search(query,category: category,limit: 10000) ?? []
        emojiPage = min(emojiPage,max(0,(matches.count - 1) / 60))
        let entries = Array(matches.dropFirst(emojiPage * 60).prefix(60))
        grid(entries.map { ($0.emoji,$0.emoji) },prefix: "emoji",columns: 8) { view, emoji in
            let value = view.emoji?.variant(emoji,gender: view.preferences.configuration.emojiGender,tone: view.preferences.configuration.emojiTone) ?? emoji
            view.returnFromEmoji(inserting: value)
        }
        if entries.isEmpty { note("Không có emoji phù hợp") }
        if matches.count > 60 {
            let pages = (matches.count + 59) / 60
            let previous = PillButton(type: .custom), next = PillButton(type: .custom)
            configure(previous, "‹ Trước", "emoji-previous") { [weak self] in guard let self else { return }; self.emojiPage = max(0, self.emojiPage - 1); self.showEmoji() }
            configure(next, "Sau ›", "emoji-next") { [weak self] in guard let self else { return }; self.emojiPage = min(pages - 1, self.emojiPage + 1); self.showEmoji() }
            let label = UILabel(); label.text = "\(emojiPage + 1) / \(pages)"; label.textAlignment = .center
            label.font = .systemFont(ofSize: 14); label.textColor = theme.secondaryFg
            for button in [previous, next] {
                style(chip: button, selected: false); button.layer.cornerRadius = 8; button.titleLabel?.font = .systemFont(ofSize: 15)
                button.heightAnchor.constraint(equalToConstant: 38).isActive = true
            }
            let pager = UIStackView(arrangedSubviews: [previous, label, next]); pager.distribution = .fillEqually; pager.spacing = 6
            panelStack.addArrangedSubview(pager)
        }
        setNeedsLayout()
    }
}
