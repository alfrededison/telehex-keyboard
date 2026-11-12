import UIKit

/// Các trang cài đặt trong app, cùng nhóm và thứ tự với Cài đặt của Android. Cài đặt tới bàn phím qua App Group, áp khi
/// bàn phím mở lại; từ đã học và sao lưu đầy đủ nằm ở ⚙ trên bàn phím.
enum SettingsScreens {
    typealias Row = SettingsViewController.Row
    typealias Section = SettingsViewController.Section

    struct Entry { let title: String, symbol: String, color: UInt32, id: String, make: () -> UIViewController }
    static let entries: [Entry] = [
        Entry(title: "Bàn phím", symbol: "keyboard.fill", color: 0x5F6368, id: "settings-keyboard", make: keyboard),
        Entry(title: "Telex", symbol: "character.textbox", color: 0x9334E6, id: "settings-telex", make: telex),
        Entry(title: "Gõ tắt", symbol: "text.badge.plus", color: 0x1E8E3E, id: "settings-snippets", make: snippets),
        Entry(title: "Gợi ý và gói ngôn ngữ", symbol: "text.bubble.fill", color: 0x1A73E8, id: "settings-data", make: predictions),
        Entry(title: "Chống bấm nhầm", symbol: "hand.point.up.left.fill", color: 0xD93025, id: "settings-touch", make: touch),
        Entry(title: "Cử chỉ", symbol: "hand.draw.fill", color: 0xF29900, id: "settings-gestures", make: gestures),
        Entry(title: "Emoji và clipboard", symbol: "face.smiling.inverse", color: 0xF9AB00, id: "settings-emoji", make: emoji),
        Entry(title: "Sao lưu và khôi phục", symbol: "arrow.clockwise.icloud.fill", color: 0x12A4AF, id: "settings-backup", make: backup),
    ]

    static func keyboard() -> UIViewController {
        SettingsViewController(title: "Bàn phím") { screen in [
            Section(header: "Bố cục", rows: [
                Row(title: "Profile", detail: screen.preferences.profile.name, id: "settings-profiles", kind: .navigate(profiles)),
                Row(title: "Chỉnh sửa các lớp phím", id: "edit-layout", kind: .navigate { LayoutEditorViewController() }),
            ]),
            Section(footer: "Giữ một phím chữ đến khi nó nổi lên rồi kéo thả lên phím khác; lưu vào profile đang dùng (profile có sẵn tạo bản riêng). Cần cho phép Truy cập đầy đủ cho TeleHex; bật quyền xong là dùng được.", rows: [
                Row(title: "Giữ kéo để đổi chỗ phím ngay trên bàn phím", id: "rearrange", kind: .toggle(\.rearrange)),
                openFullAccessSettings(),
            ]),
            Section(header: "Chế độ một tay", footer: "Lưới thu còn khoảng 80%, dồn về một bên.", rows: [
                Row(title: "", id: "one-hand", kind: .choice(["Đủ rộng", "Trái", "Phải"], \.oneHand)),
            ]),
            Section(header: "Giao diện", footer: "Màu nhấn tô phím nguyên âm, ↵ và các điều khiển; \"Không\" để nguyên âm và phím dấu trắng như phụ âm.", rows: [
                Row(title: "", id: "theme", kind: .choice(["Hệ thống", "Sáng", "Tối"], \.theme)),
                Row(title: "Màu nhấn", id: "accent", kind: .accent),
                Row(title: "Chiều cao bàn phím", id: "height-scale", kind: .slider(0.8...1.4, step: 0.05, \.heightScale, format: { "\(Int(($0 * 100).rounded()))%" })),
            ]),
            Section(header: "Phản hồi", footer: "Rung và âm phím cần bật Truy cập đầy đủ cho TeleHex trong Cài đặt iOS.", rows: [
                Row(title: "Rung khi chạm", id: "haptics", kind: .toggle(\.haptics)),
                Row(title: "Âm phím", id: "sound", kind: .toggle(\.sound)),
            ]),
        ] }
    }

    static func profiles() -> UIViewController {
        SettingsViewController(title: "Profile") { screen in
            let preferences = screen.preferences
            let rows = preferences.allProfiles.map { profile in
                Row(title: profile.name, detail: profile.builtIn ? (profile.spec.isHex ? "Tổ ong" : "QWERTY") : nil, id: "profile-\(profile.id)",
                    kind: .checked(profile.id == preferences.profile.id) { _ in preferences.configuration.activeProfile = profile.id },
                    swipe: profile.builtIn ? [] : [
                        UIContextualAction(style: .destructive, title: "Xoá") { [weak screen] _, _, done in
                            preferences.configuration.profiles.removeAll { $0.id == profile.id }
                            if preferences.configuration.activeProfile == profile.id { preferences.configuration.activeProfile = KeyboardProfile.presets[0].id }
                            screen?.reload(); done(true)
                        },
                        UIContextualAction(style: .normal, title: "Đổi tên") { [weak screen] _, _, done in
                            screen?.askName(title: "Đổi tên profile", value: profile.name) { name in
                                guard !preferences.allProfiles.contains(where: { $0.id != profile.id && $0.name.caseInsensitiveCompare(name) == .orderedSame }) else { return "Tên đã có" }
                                var edited = profile; edited.name = name
                                return preferences.saveProfile(edited) ? nil : "Tên không hợp lệ"
                            }
                            done(true)
                        },
                    ])
            }
            return [
                Section(header: "Bố cục", footer: preferences.profile.builtIn ? "Profile có sẵn: sửa phím sẽ tự tạo bản riêng. Vuốt trái profile riêng để đổi tên hoặc xoá." : "Vuốt trái profile riêng để đổi tên hoặc xoá.", rows: rows),
                Section(rows: [
                    Row(title: "Tạo profile / Nhân bản", id: "duplicate-profile", kind: .action { screen in
                        screen.askName(title: "Tên profile mới", value: preferences.uniqueName("Của tôi")) { name in
                            var profile = preferences.profile; profile.id = UUID().uuidString; profile.name = name
                            return preferences.saveProfile(profile) ? nil : "Tên hoặc dữ liệu không hợp lệ"
                        }
                    }),
                ]),
            ]
        }
    }

    static func telex() -> UIViewController {
        SettingsViewController(title: "Telex") { screen in
            let preferences = screen.preferences
            return [
                Section(header: "Kiểu đặt dấu", rows: [
                    Row(title: "Cũ (hòa, thúy)", id: "tone-old", kind: .checked(!preferences.newToneStyle) { _ in preferences.newToneStyle = false }),
                    Row(title: "Mới (hoà, thuý)", id: "tone-new", kind: .checked(preferences.newToneStyle) { _ in preferences.newToneStyle = true }),
                ]),
                Section(footer: "Vuốt xuống phím cách để bật/tắt EN. Ô email, URL và ô ASCII tự mở ở EN.", rows: [
                    Row(title: "L cũng là dấu huyền (lal → là)", id: "l-as-grave", kind: .toggle(\.lAsGrave)),
                    Row(title: "Tự viết hoa chữ đầu câu", id: "auto-capitalization", kind: .toggle(\.autoCapitalization)),
                    Row(title: "Tự khôi phục từ tiếng Anh (windows không thành ưindows)", id: "english-fallback", kind: .toggle(\.englishFallback)),
                ]),
            ]
        }
    }

    static func snippets() -> UIViewController {
        SettingsViewController(title: "Gõ tắt") { screen in
            let preferences = screen.preferences
            let names = preferences.configuration.snippets.keys.sorted()
            return [
                Section(footer: "Gõ chữ viết tắt rồi chạm cách để thay bằng nội dung.", rows: [
                    Row(title: "Thêm gõ tắt", id: "add-snippet", kind: .navigate { SnippetEditorViewController(key: nil) }),
                ]),
                Section(header: names.isEmpty ? nil : "Đã lưu", footer: names.isEmpty ? nil : "Vuốt trái để xoá.", rows: names.map { key in
                    Row(title: key, detail: preferences.configuration.snippets[key], id: "snippet-\(key)", kind: .navigate { SnippetEditorViewController(key: key) },
                        swipe: [UIContextualAction(style: .destructive, title: "Xoá") { [weak screen] _, _, done in
                            preferences.configuration.snippets.removeValue(forKey: key); screen?.reload(); done(true)
                        }])
                }),
            ]
        }
    }

    static func predictions() -> UIViewController {
        SettingsViewController(title: "Gợi ý và gói ngôn ngữ") { screen in
            let settings = screen.settings
            func pack(_ english: Bool) -> Section {
                let code = english ? "EN" : "VI", loaded = settings.packLoaded(english: english)
                var rows = [Row(title: "Nạp gói \(code) .ngram…", id: "import-\(code)", kind: .action { _ in settings.files.run("import-\(code)") })]
                if loaded {
                    rows.append(Row(title: "Xoá gói \(code)", id: "remove-\(code)", kind: .destructive { screen in
                        settings.removePack(english: english); screen.toast("Đã xoá gói \(code)")
                    }))
                }
                return Section(header: "Gói \(english ? "tiếng Anh" : "tiếng Việt")", footer: "Hiện tại: \(loaded ? "đã nạp" : "chưa nạp") · tối đa \(KeyboardDataStore.packLimit / 1024 / 1024) MiB", rows: rows)
            }
            return [
                Section(footer: "Bàn phím không tự đổi chữ đã gõ. Gợi ý tiếng Việt học từ chính bạn; giữ một gợi ý trên bàn phím để quên từ đó. Mọi gợi ý chạy trên máy.", rows: [
                    Row(title: "Gợi ý từ", id: "suggestions", kind: .toggle(\.suggestions)),
                    Row(title: "Gõ vuốt: lướt qua các phím chữ rồi chạm gợi ý để chèn", id: "glide-enabled", kind: .toggle(\.glide)),
                    Row(title: "Ẩn danh — không học từ đã gõ", id: "incognito", kind: .toggle(\.incognito)),
                ]),
                pack(false), pack(true),
                Section(header: "Dữ liệu học", footer: "Chỉ xoá từ tiếng Việt đã học; giữ các gói ngôn ngữ. Bàn phím xoá khi mở lại.", rows: [
                    Row(title: "Xoá dữ liệu đã học…", id: "clear-personal", kind: .destructive { screen in
                        screen.confirm("Xoá dữ liệu đã học?", message: "Từ tiếng Việt bàn phím đã học sẽ bị xoá ở lần mở bàn phím tiếp theo.", action: "Xoá") {
                            settings.send(.clearPersonal); screen.toast("Bàn phím sẽ xoá dữ liệu đã học khi mở lại")
                        }
                    }),
                ]),
            ]
        }
    }

    static func touch() -> UIViewController {
        SettingsViewController(title: "Chống bấm nhầm") { screen in [
            Section(footer: "Bàn phím học chỗ bạn hay chạm lệch trên từng phím chữ và nới vùng chạm theo đó. Vùng chạm động: chạm lưng chừng giữa hai phím chữ thì chọn phím giữ âm tiết tiếng Việt hợp lệ. Chạm giữa phím luôn gõ đúng phím; phím chức năng không bị lấn.", rows: [
                Row(title: "Học độ lệch chạm", id: "touch-adaptation", kind: .toggle(\.touchAdaptation)),
                Row(title: "Vùng chạm động: nới vùng phím hợp lệ tiếp theo (một dạng tự sửa ngầm)", id: "dynamic-hit-zones", kind: .toggle(\.dynamicHitZones)),
                Row(title: "Xoá độ lệch chạm", id: "reset-offsets", kind: .action { screen in
                    screen.settings.send(.resetOffsets); screen.toast("Bàn phím sẽ xoá độ lệch chạm khi mở lại")
                }),
            ]),
        ] }
    }

    static func gestures() -> UIViewController {
        SettingsViewController(title: "Cử chỉ") { _ in [
            Section(header: "Độ nhạy", rows: [
                Row(title: "Ngưỡng vuốt", id: "swipe-threshold", kind: .slider(12...80, step: 1, \.swipeThreshold, format: { "\(Int($0)) pt" })),
                Row(title: "Giữ phím để đổi chỗ", id: "hold-delay", kind: .slider(0.4...2, step: 0.05, \.holdDelay, format: { String(format: "%.2f giây", $0) })),
                Row(title: "Giữ phím cách để di con trỏ", id: "cursor-delay", kind: .slider(0.15...1, step: 0.05, \.cursorDelay, format: { String(format: "%.2f giây", $0) })),
                Row(title: "Mỗi bước xoá từ / di con trỏ", id: "step-size", kind: .slider(25...100, step: 1, \.stepSize, format: { "\(Int($0)) pt" })),
            ]),
            Section(header: "Cách dùng", rows: [
                "Chữ: vuốt lên đảo hoa/thường, vuốt xuống gõ ký tự phụ.",
                "Lướt qua từ 3 phím chữ để gõ vuốt (tiếng Việt và tiếng Anh).",
                "Phím cách: vuốt trái xoá, kéo xa xoá từ, phải dán, lên khoá hoa, xuống đổi VI/EN, giữ rồi kéo để di con trỏ.",
                "Có vùng chọn: vuốt lên phím cách để chép, vuốt trái để cắt. Cắt, chép, dán cần Truy cập đầy đủ.",
                "Bố cục truyền thống: chạm đúp ⇧ để khoá hoa. Giữ ⌫ để xoá liên tục.",
                "Hai ngón vuốt trái/phải: hoàn tác/làm lại.",
            ].enumerated().map { Row(title: $1, id: "gesture-help-\($0)", kind: .text) }),
        ] }
    }

    static func emoji() -> UIViewController {
        SettingsViewController(title: "Emoji và clipboard") { screen in [
            Section(header: "Clipboard", footer: "Mở clipboard từ ⚙ trên bàn phím hoặc vuốt xuống phím ⚙: chữ đang chép được lưu lại, chạm để dán. Mục thường tự xoá sau 1 giờ, mục ghim giữ lâu; không vào bản sao lưu; tắt là xoá hết. Cần cho phép Truy cập đầy đủ cho TeleHex (iOS chỉ cho bàn phím đọc clipboard khi có quyền này); bật quyền xong là dùng được. iOS hỏi \"TeleHex muốn dán…\" mỗi lần trừ khi đặt Cài đặt › Ứng dụng › TeleHex › Dán từ ứng dụng khác = Cho phép.", rows: [
                Row(title: "Lưu lịch sử clipboard", id: "clipboard-history", kind: .toggle(\.clipboardHistory)),
            ]),
            Section(header: "Truy cập đầy đủ", footer: fullAccessHelp, rows: [openFullAccessSettings()]),
            Section(header: "Giới tính mặc định", footer: "Cho emoji trung tính có biến thể nam/nữ.", rows: [
                Row(title: "", id: "emoji-gender", kind: .choice(["Nguyên bản", "Nữ", "Nam"], \.emojiGender)),
            ]),
            Section(header: "Tông da", rows: [
                Row(title: "", id: "emoji-tone", kind: .choice(["Gốc", "🏻", "🏼", "🏽", "🏾", "🏿"], \.emojiTone)),
            ]),
        ] }
    }

    static func backup() -> UIViewController {
        SettingsViewController(title: "Sao lưu và khôi phục") { screen in [
            Section(footer: "Tệp JSON gồm cấu hình, profile, gõ tắt và gói VI/EN. Từ đã học chỉ nằm trong bàn phím: sao lưu đầy đủ từ ⚙ trên bàn phím. Không tương thích ZIP Android.", rows: [
                Row(title: "Xuất bản sao lưu cài đặt…", id: "export-settings", kind: .action { screen in screen.settings.files.run("export-settings") }),
                Row(title: "Khôi phục từ tệp…", id: "import-settings", kind: .action { screen in screen.settings.files.run("import-settings") }),
            ]),
        ] }
    }
}

/// iOS không cho app biết bàn phím có Truy cập đầy đủ hay không, nên app không đoán trạng thái: chức năng cần quyền bật sẵn,
/// bàn phím tự kiểm tra quyền khi chạy, app chỉ hướng dẫn cách bật.
let fullAccessHelp = "Cài đặt › Cài đặt chung › Bàn phím › Bàn phím › TeleHex › bật Cho phép Truy cập đầy đủ. Bật xong là dùng được, không cần làm gì thêm. TeleHex không gửi nội dung gõ đi đâu; quyền này chỉ dùng cho cắt/chép/dán và lịch sử clipboard, kéo đổi chỗ phím, rung và âm phím."
func openFullAccessSettings() -> SettingsViewController.Row {
    .init(title: "Mở Cài đặt để bật Truy cập đầy đủ", id: "open-system-settings", kind: .action { _ in
        if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
    })
}

extension SettingsViewController {
    /// Hỏi tên (profile); `save` trả lỗi để hỏi lại, nil khi xong.
    func askName(title: String, value: String, save: @escaping (String) -> String?) {
        let alert = UIAlertController(title: title, message: nil, preferredStyle: .alert)
        alert.addTextField { $0.text = value; $0.clearButtonMode = .whileEditing; $0.autocapitalizationType = .sentences }
        alert.addAction(UIAlertAction(title: "Huỷ", style: .cancel))
        alert.addAction(UIAlertAction(title: "Lưu", style: .default) { [weak self, weak alert] _ in
            let name = (alert?.textFields?.first?.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            if let error = name.isEmpty ? "Tên không được trống" : save(name) { self?.toast(error) }
            self?.reload()
        })
        present(alert, animated: true)
    }
}
