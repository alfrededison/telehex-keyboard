import XCTest
import TeleHexCore
@testable import TeleHex

final class HexKeyboardTests: XCTestCase {
    func testHexKeysStopGrowingOnIPad() throws {
        // iPhone dọc lớn nhất (440pt) chưa chạm trần: chiều cao theo bề rộng như cũ.
        XCTAssertLessThan((440 - 2 * HexKeyboardView.pad) / GridSpec.hex7.span, HexKeyboardView.hexMaxKey)
        let capped = 2 * HexKeyboardView.pad + HexKeyboardView.hexMaxKey * GridSpec.hex7.span
        XCTAssertEqual(HexKeyboardView.height(for: 1032, screenHeight: 1376, spec: .hex7),
                       HexKeyboardView.height(for: capped, screenHeight: 1376, spec: .hex7), accuracy: 0.01)

        let suite = try XCTUnwrap(UserDefaults(suiteName: "telehex.ipad.tests"))
        suite.removePersistentDomain(forName: "telehex.ipad.tests")
        let keyboard = HexKeyboardView(preferences: KeyboardPreferences(defaults: suite))
        keyboard.frame = CGRect(x: 0, y: 0, width: 1032, height: HexKeyboardView.height(for: 1032, screenHeight: 1376))
        keyboard.layoutIfNeeded()
        let q = try XCTUnwrap(keyboard.subviews.first { $0.accessibilityIdentifier == "key-q" })
        XCTAssertEqual(q.frame.width, HexKeyboardView.hexMaxKey, accuracy: 0.01)
        let keys = keyboard.subviews.filter { $0.accessibilityIdentifier?.hasPrefix("key-") == true }
        let left = try XCTUnwrap(keys.map(\.frame.minX).min()), right = try XCTUnwrap(keys.map(\.frame.maxX).max())
        XCTAssertEqual(left, 1032 - right, accuracy: HexKeyboardView.hexMaxKey, "lưới tổ ong nằm giữa")
    }
    func testOverlapHitsTheVisiblePolygonInsteadOfTheLaterRowRectangle() throws {
        let suite = try XCTUnwrap(UserDefaults(suiteName: "telehex.geometry.tests"))
        let keyboard = HexKeyboardView(preferences: KeyboardPreferences(defaults: suite))
        keyboard.frame = CGRect(x: 0, y: 0, width: 402, height: 300)
        keyboard.layoutIfNeeded()
        let w = try XCTUnwrap(keyboard.subviews.first { $0.accessibilityIdentifier == "key-w" })
        let e = try XCTUnwrap(keyboard.subviews.first { $0.accessibilityIdentifier == "key-e" })
        let overlap = w.frame.intersection(e.frame)
        var verified = 0
        for x in stride(from: overlap.minX + 1, to: overlap.maxX, by: 1) {
            for y in stride(from: overlap.minY + 1, to: overlap.maxY, by: 1) {
                let point = CGPoint(x: x, y: y)
                if w.point(inside: w.convert(point, from: keyboard), with: nil) &&
                    !e.point(inside: e.convert(point, from: keyboard), with: nil) {
                    let hit = keyboard.hitTest(point, with: nil)
                    XCTAssertTrue(hit === w || hit?.isDescendant(of: w) == true)
                    verified += 1
                }
            }
        }
        XCTAssertGreaterThan(verified, 0)
    }

    func testEnglishCompletionsUseTheSameBundledDictionaryAsAndroid() throws {
        func text(_ name: String, _ ext: String) throws -> String {
            let url = try XCTUnwrap(Bundle.main.url(forResource: name, withExtension: ext))
            return try String(contentsOf: url, encoding: .utf8)
        }
        let en = try text("en-frequency", "txt")
        XCTAssertEqual(en.split(separator: "\n").count, 50000)
        let vocabulary = KeyboardVocabulary(englishText: en, vietnameseText: try text("vi-frequency", "txt"), phrasesText: try text("vi-phrases", "tsv"))
        XCTAssertEqual(vocabulary.suggestions(prefix: "hel", before: "hel", englishMode: true).first, "help")
        XCTAssertTrue(vocabulary.suggestions(prefix: "hel", before: "name@hel", englishMode: true).isEmpty)
        XCTAssertTrue(vocabulary.suggestions(prefix: "ng", before: "ng", englishMode: false).contains("người"))
    }
    func testTouchAdaptationAndEditorUseSeparateDocuments() throws {
        let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        prefs.configuration.touchAdaptation = true
        let keyboard = HexKeyboardView(preferences: prefs)
        keyboard.frame = CGRect(x: 0,y: 0,width: 402,height: 300); keyboard.layoutIfNeeded()
        let key = try XCTUnwrap(keyboard.keys.first { $0.2 == "w" })
        keyboard.adaptTouch(CGPoint(x: key.0.center.x + 3,y: key.0.center.y),index: key.1)
        XCTAssertGreaterThan(try XCTUnwrap(prefs.configuration.offsets["builtin.default:w"]?.first),0)
        var host = ""
        keyboard.onKey = { host += $0 }
        var saved: [String] = []
        keyboard.beginEditor(title: "Gõ tắt",fields: ["Từ"],values: [""],ascii: [false]) { saved = $0 }
        for key in "tieengs" { keyboard.type(String(key)) }
        XCTAssertEqual(keyboard.inlineDocuments.first?.text,"tiếng")
        XCTAssertEqual(host,"")
        XCTAssertTrue(saved.isEmpty)
    }
    func testAccentTintsVowelKeysLikeAndroid() throws {
        let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        prefs.configuration.theme = 1; prefs.configuration.accent = 3 // Tím
        let view = HexKeyboardView(preferences: prefs)
        view.frame = CGRect(x: 0,y: 0,width: 402,height: 300); view.applyTheme(); view.layoutIfNeeded()
        let a = try XCTUnwrap(view.keys.first { $0.2 == "a" }).0, q = try XCTUnwrap(view.keys.first { $0.2 == "q" }).0
        XCTAssertEqual(a.kind, .vowel)
        XCTAssertEqual(a.theme.background(for: a.kind), UIColor(hex: 0xE8DEF8))
        XCTAssertEqual(a.theme.foreground(for: a.kind), UIColor(hex: 0x4A2382))
        XCTAssertEqual(q.theme.background(for: q.kind), UIColor(hex: 0xFFFFFF))
        // Phím dấu cam nhạt như Android, không đổi theo màu nhấn.
        let s = try XCTUnwrap(view.keys.first { $0.2 == "s" }).0
        XCTAssertEqual(s.kind, .tone)
        XCTAssertEqual(s.theme.background(for: s.kind), UIColor(hex: 0xFFE2CC))
        XCTAssertEqual(s.theme.foreground(for: s.kind), UIColor(hex: 0x7A3300))
        // "Không": nguyên âm và phím dấu trắng như phụ âm.
        prefs.configuration.accent = 5; view.applyTheme()
        XCTAssertEqual(a.theme.background(for: .vowel), a.theme.background(for: .consonant))
        XCTAssertEqual(s.theme.background(for: .tone), s.theme.background(for: .consonant))
    }
    func testEditorsNeverShrinkKeys() throws {
        let view = HexKeyboardView(preferences: KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!))
        view.frame = CGRect(x: 0,y: 0,width: 402,height: 300); view.layoutIfNeeded()
        func frame(_ code: String) throws -> CGRect { try XCTUnwrap(view.keys.first { $0.2 == code }).0.frame }
        let before = try frame("q")
        // Tìm emoji: hàng ô nhập riêng dưới thanh trên; host cho bàn phím cao thêm `extraHeight` nên phím giữ nguyên cỡ.
        view.openEmoji(); view.searchEmoji()
        XCTAssertEqual(view.extraHeight, HexKeyboardView.editorRowHeight)
        XCTAssertEqual(view.panelTitle.text, "Tìm emoji")
        view.frame.size.height += view.extraHeight; view.layoutIfNeeded()
        XCTAssertGreaterThanOrEqual(view.editorBar.frame.minY, HexKeyboardView.barHeight)
        XCTAssertEqual(try frame("q").size, before.size)
        XCTAssertEqual(try frame("q").minY, before.minY + HexKeyboardView.editorRowHeight, accuracy: 0.5)
        // Sửa lớp phím: sáu nút chọn lớp nằm trên thanh trên, không cần cao thêm.
        view.closePanel(); view.startLayoutEditor(0)
        XCTAssertEqual(view.extraHeight, 0)
        view.frame.size.height = 300; view.layoutIfNeeded()
        XCTAssertTrue(view.mode.isHidden); XCTAssertTrue(view.backspace.isHidden)
        XCTAssertLessThanOrEqual(view.editorBar.frame.maxY, HexKeyboardView.barHeight)
        XCTAssertEqual(try frame("q"), before)
    }
    func testSwipeCharacterEditorCancelReturnsToLayerEditor() throws {
        let view = HexKeyboardView(preferences: KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!))
        view.frame = CGRect(x: 0,y: 0,width: 402,height: 300); view.layoutIfNeeded()
        view.startLayoutEditor(1)
        let q = try XCTUnwrap(view.keys.first { $0.2 == "q" })
        view.editCell(q.1, layer: 1)
        XCTAssertFalse(view.inlineDocuments.isEmpty)
        let cancel = try XCTUnwrap(view.editorBar.arrangedSubviews.first { $0.accessibilityIdentifier == "cancel-editor" } as? UIButton)
        cancel.sendActions(for: .touchUpInside)
        XCTAssertEqual(view.editLayer, 1)
        XCTAssertNil(view.visiblePanel)
        XCTAssertTrue(view.inlineDocuments.isEmpty)
    }
    func testRollingFromAFunctionKeyOntoALetterTypesTheLetter() throws {
        let view = HexKeyboardView(preferences: KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!))
        view.frame = CGRect(x: 0,y: 0,width: 402,height: 300); view.layoutIfNeeded()
        var typed = ""; view.onKey = { typed += $0 }
        let settings = try XCTUnwrap(view.keys.first { $0.2 == "#settings" }), w = try XCTUnwrap(view.keys.first { $0.2 == "w" })
        let start = CGPoint(x: settings.0.frame.maxX - 6, y: settings.0.frame.midY), end = CGPoint(x: w.0.frame.minX + 8, y: w.0.frame.midY)
        XCTAssertEqual(view.displayedCell(at: start), settings.1)
        XCTAssertEqual(view.displayedCell(at: end), w.1)
        view.handle(.end(settings.1, start, end, [start, end], [settings.1, w.1], false))
        XCTAssertEqual(typed, "w")
        XCTAssertNil(view.visiblePanel)
        // Chạm rồi nhả ngay trên ⚙ vẫn mở Cài đặt.
        view.handle(.end(settings.1, start, start, [start], [settings.1], false))
        XCTAssertEqual(view.visiblePanel, "settings")
    }
    func testLearnedTouchOffsetsNeverStealFunctionKeys() throws {
        let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        prefs.configuration.touchAdaptation = true
        // Độ lệch học được của w kéo vùng w sang trái, đè lên ⚙.
        prefs.configuration.offsets = ["builtin.default:w": [-0.3, 0]]
        let view = HexKeyboardView(preferences: prefs)
        view.frame = CGRect(x: 0,y: 0,width: 402,height: 300); view.layoutIfNeeded()
        let settings = try XCTUnwrap(view.keys.first { $0.2 == "#settings" }), w = try XCTUnwrap(view.keys.first { $0.2 == "w" })
        XCTAssertEqual(view.cell(at: CGPoint(x: settings.0.frame.maxX - 6, y: settings.0.frame.midY)), settings.1)
        XCTAssertEqual(view.cell(at: w.0.center), w.1)
    }
    func testSettingsKeyShowsClipboardIconOnlyWhenClipboardHistoryIsAvailable() throws {
        for profile in KeyboardProfile.presets {
            let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
            prefs.configuration.activeProfile = profile.id
            let view = HexKeyboardView(preferences: prefs)
            view.frame = CGRect(x: 0, y: 0, width: 402, height: 300); view.layoutIfNeeded()
            view.update(candidates: [], english: false, uppercase: false)
            let settings = try XCTUnwrap(view.keys.first { $0.2 == "#settings" }?.0)
            XCTAssertNil(settings.hintSymbolName)
            view.clipboardEnabled = true
            view.update(candidates: [], english: false, uppercase: false)
            XCTAssertEqual(settings.hintSymbolName, "doc.on.clipboard")
            let image = UIGraphicsImageRenderer(bounds: view.bounds).image { view.layer.render(in: $0.cgContext) }
            let attachment = XCTAttachment(image: image); attachment.name = "settings-clipboard-\(profile.id)"; attachment.lifetime = .keepAlways; add(attachment)
        }
    }
    func testPackRestoredOnTheKeyboardIsUsedUntilTheAppChangesPacks() throws {
        let temp = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: temp) }
        let local = KeyboardDataStore(root: temp.appendingPathComponent("local"))
        let shared = KeyboardDataStore(root: temp.appendingPathComponent("group"))
        try shared.save(Data("group".utf8), name: "vi.ngram")
        // Không có App Group (bản không ký): chỉ kho riêng.
        try local.save(Data("old".utf8), name: "vi.ngram")
        XCTAssertEqual(VocabularyLoader.pack("vi.ngram", local: local, group: nil, groupStamp: 0), Data("old".utf8))
        // Có group: gói của app thắng gói cũ trong kho riêng.
        XCTAssertEqual(VocabularyLoader.pack("vi.ngram", local: local, group: shared, groupStamp: 0), Data("group".utf8))
        // Khôi phục trên bàn phím khi không ghi được group: lưu kho riêng, đánh dấu, dùng tới khi app nạp/xoá gói.
        let files = KeyboardFiles(store: local, preferences: KeyboardPreferences(defaults: try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))))
        files.group = { KeyboardDataStore(root: URL(fileURLWithPath: "/dev/null/telehex")) }
        XCTAssertTrue(try files.restore(KeyboardBackup(configuration: KeyboardConfiguration(), files: ["vi.ngram": Data("restored".utf8)]), personal: false))
        let stamp = VocabularyLoader.groupPackStamp
        XCTAssertEqual(VocabularyLoader.pack("vi.ngram", local: local, group: shared, groupStamp: stamp), Data("restored".utf8))
        XCTAssertEqual(VocabularyLoader.pack("vi.ngram", local: local, group: shared, groupStamp: stamp + 1), Data("group".utf8))
        // Ghi được group: gói vào group, bỏ bản riêng.
        files.group = { shared }
        XCTAssertFalse(try files.restore(KeyboardBackup(configuration: KeyboardConfiguration(), files: ["vi.ngram": Data("new".utf8)]), personal: false))
        XCTAssertEqual(VocabularyLoader.pack("vi.ngram", local: local, group: shared, groupStamp: stamp), Data("new".utf8))
        XCTAssertNil(local.read("vi.ngram"))
    }
    func testKeyboardBackupWithoutPacksKeepsLocalPacks() throws {
        let local = KeyboardDataStore(root: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString))
        defer { try? FileManager.default.removeItem(at: local.root) }
        try local.save(Data("pack".utf8), name: "vi.ngram")
        try local.save(Data("1".utf8), name: "vi.ngram.local")
        try local.save(Data("TELEHEX-PERSONAL-1\n".utf8), name: "personal.txt")
        let files = KeyboardFiles(store: local, preferences: KeyboardPreferences(defaults: try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))))
        files.group = { nil }
        // Sao lưu đầy đủ từ bàn phím (không kèm gói) rồi khôi phục: thay từ đã học, giữ gói và mốc của nó.
        let backup = KeyboardBackup(configuration: KeyboardConfiguration(), files: ["personal.txt": Data("TELEHEX-PERSONAL-1\nu\txin\t1\n".utf8)])
        try files.restore(backup, personal: true)
        XCTAssertEqual(local.read("vi.ngram"), Data("pack".utf8))
        XCTAssertEqual(local.read("vi.ngram.local"), Data("1".utf8))
        XCTAssertEqual(local.read("personal.txt"), Data("TELEHEX-PERSONAL-1\nu\txin\t1\n".utf8))
        XCTAssertNil(local.read("en.ngram"))
    }
    func testClipboardHistoryKeepsPinnedAndExpiresOthers() throws {
        let temp = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: temp) }
        var clock = Date(timeIntervalSince1970: 1000)
        let history = ClipboardHistory(store: KeyboardDataStore(root: temp)) { clock }
        history.add("một"); history.add("hai"); history.add("một"); history.add("   ")
        XCTAssertEqual(history.items.map(\.text), ["một", "hai"])
        history.togglePin("hai")
        for index in 0..<40 { history.add("mục \(index)") }
        XCTAssertEqual(history.items.filter { !$0.pinned }.count, ClipboardHistory.limit)
        XCTAssertTrue(history.items.contains { $0.text == "hai" && $0.pinned })
        // Lưu lại và đọc lại; quá 1 giờ thì chỉ còn mục ghim.
        clock = clock.addingTimeInterval(3601)
        let reopened = ClipboardHistory(store: KeyboardDataStore(root: temp)) { clock }
        XCTAssertEqual(reopened.items.map(\.text), ["hai"])
        reopened.add("ba"); reopened.clearUnpinned()
        XCTAssertEqual(reopened.items.map(\.text), ["hai"])
        reopened.clearAll()
        XCTAssertTrue(ClipboardHistory(store: KeyboardDataStore(root: temp)).items.isEmpty)
    }
    func testDragToRearrangeNeedsFullAccessAndClipboardButtonFollowsTheOption() throws {
        let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        prefs.configuration.rearrange = true
        let view = HexKeyboardView(preferences: prefs)
        view.frame = CGRect(x: 0,y: 0,width: 402,height: 300); view.layoutIfNeeded()
        let w = try XCTUnwrap(view.keys.first { $0.2 == "w" })
        view.handle(.move(w.1, w.0.center, w.0.center, [w.0.center], [w.1], true))
        XCTAssertNil(view.dragged)
        view.clearGesture()
        view.rearrangeAllowed = true
        view.handle(.move(w.1, w.0.center, w.0.center, [w.0.center], [w.1], true))
        XCTAssertNotNil(view.dragged)
        view.clearGesture()
        func button(_ id: String) -> UIButton? {
            var stack: [UIView] = [view]
            while let next = stack.popLast() {
                if let control = next as? UIButton, control.accessibilityIdentifier == id { return control }
                stack += next.subviews
            }
            return nil
        }
        view.update(candidates: [], english: false, uppercase: false)
        XCTAssertNil(button("toolbar-clipboard"))
        view.clipboardEnabled = true; view.update(candidates: [], english: false, uppercase: false)
        XCTAssertNotNil(button("toolbar-clipboard"))
        // Vuốt xuống phím ⚙ mở clipboard.
        let settings = try XCTUnwrap(view.keys.first { $0.2 == "#settings" })
        let start = settings.0.center, end = CGPoint(x: start.x, y: start.y + 40)
        view.handle(.end(settings.1, start, end, [start, end], [settings.1], false))
        XCTAssertEqual(view.visiblePanel, "clipboard")
    }
    func testKeyboardProfilesReachTheAppWithFullAccessAndOldConfigsStillLoad() throws {
        let shared = try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))
        let settings = AppSettings(preferences: KeyboardPreferences(defaults: try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))),
                                   store: KeyboardDataStore(root: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)),
                                   shared: { shared })
        settings.preferences.configuration.theme = 2
        let keyboard = KeyboardPreferences(defaults: try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString)))
        var mine = KeyboardProfile.presets[0]; mine.letters.swapAt(1, 2); XCTAssertTrue(keyboard.saveProfile(mine))
        SharedSettings.reportKeyboardProfiles(keyboard.configuration, in: shared, now: Date().addingTimeInterval(60))
        settings.adoptKeyboardProfiles()
        XCTAssertEqual(settings.preferences.configuration.activeProfile, keyboard.configuration.activeProfile)
        XCTAssertEqual(settings.preferences.configuration.profiles.map(\.id), keyboard.configuration.profiles.map(\.id))
        XCTAssertEqual(settings.preferences.configuration.theme, 2)
        // Cấu hình lưu từ bản cũ thiếu trường mới vẫn đọc được, trường thiếu lấy mặc định.
        let old = Data(#"{"theme":2,"activeProfile":"builtin.qwerty","snippets":{"dc":"được"}}"#.utf8)
        let decoded = try JSONDecoder().decode(KeyboardConfiguration.self, from: old)
        XCTAssertEqual(decoded.theme, 2); XCTAssertEqual(decoded.activeProfile, "builtin.qwerty")
        XCTAssertTrue(decoded.englishFallback); XCTAssertTrue(decoded.clipboardHistory); XCTAssertTrue(decoded.rearrange)
    }
    func testDynamicHitZonesPreferTheKeyThatKeepsTheSyllableValid() throws {
        let view = HexKeyboardView(preferences: KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!))
        view.frame = CGRect(x: 0,y: 0,width: 402,height: 300); view.layoutIfNeeded()
        let a = try XCTUnwrap(view.keys.first { $0.2 == "a" }).0, s = try XCTUnwrap(view.keys.first { $0.2 == "s" }).0
        // Lưng chừng giữa A và S, hơi lệch về A.
        let between = CGPoint(x: a.center.x + (s.center.x - a.center.x) * 0.45, y: a.center.y)
        XCTAssertEqual(view.cell(at: between), view.keys.first { $0.2 == "a" }?.1)
        view.validNextLetter = { $0 != "a" }
        XCTAssertEqual(view.cell(at: between), view.keys.first { $0.2 == "s" }?.1)
        // Chạm rõ giữa phím vẫn gõ đúng phím đó.
        XCTAssertEqual(view.cell(at: a.center), view.keys.first { $0.2 == "a" }?.1)
        // Đường vuốt và trình soạn không dùng vùng chạm động.
        XCTAssertEqual(view.cell(at: between, dynamic: false), view.keys.first { $0.2 == "a" }?.1)
        view.beginEditor(title: "Tìm emoji", fields: ["Tìm"], values: [""], ascii: [false]) { _ in }
        view.layoutIfNeeded()
        let a2 = try XCTUnwrap(view.keys.first { $0.2 == "a" }).0, s2 = try XCTUnwrap(view.keys.first { $0.2 == "s" }).0
        XCTAssertEqual(view.cell(at: CGPoint(x: a2.center.x + (s2.center.x - a2.center.x) * 0.45, y: a2.center.y)), view.keys.first { $0.2 == "a" }?.1)
        view.closePanel()
        // Bước xoá từ: quá 1,2 bước mới xoá từ, mỗi bước thêm một từ.
        XCTAssertEqual(view.wordCount(-59), 0); XCTAssertEqual(view.wordCount(-60), 1); XCTAssertEqual(view.wordCount(-110), 2)
        view.preferences.configuration.stepSize = 25
        XCTAssertEqual(view.wordCount(-60), 2)
    }
    func testGlideTraceDrawsAboveKeys() throws {
        let view = HexKeyboardView(preferences: KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!))
        let key = try XCTUnwrap(view.keys.first).0
        XCTAssertGreaterThan(view.traceLayer.zPosition, key.layer.zPosition)
        XCTAssertGreaterThan(view.popup.layer.zPosition, view.traceLayer.zPosition)
    }
    func testIdleBarShowsShortcutsAndQuickSettingsNavigate() throws {
        let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        let view = HexKeyboardView(preferences: prefs)
        view.frame = CGRect(x: 0,y: 0,width: 402,height: 300)
        func button(_ id: String) -> UIButton? {
            var stack: [UIView] = [view]
            while let next = stack.popLast() {
                if let control = next as? UIButton, control.accessibilityIdentifier == id { return control }
                stack += next.subviews
            }
            return nil
        }
        view.update(candidates: [], english: false, uppercase: false); view.layoutIfNeeded()
        XCTAssertNotNil(button("toolbar-settings")); XCTAssertNotNil(button("toolbar-emoji"))
        XCTAssertTrue(view.more.isHidden)
        view.update(candidates: ["là","và"], english: false, uppercase: false); view.layoutIfNeeded()
        XCTAssertNil(button("toolbar-settings"))
        XCTAssertNotNil(button("candidate-là"))
        // ⚙ trên bàn phím chỉ còn thao tác nhanh. Chọn profile áp ngay; "‹" từ bảng xác nhận quay về; "Xong" đóng bảng.
        view.show("settings")
        XCTAssertTrue(view.backspace.isHidden)
        XCTAssertNotNil(button("export-backup")); XCTAssertNil(button("settings-appearance"))
        try XCTUnwrap(button("profile-builtin.qwerty")).sendActions(for: .touchUpInside)
        XCTAssertEqual(prefs.configuration.activeProfile, "builtin.qwerty")
        XCTAssertEqual(view.visiblePanel, "settings")
        try XCTUnwrap(button("clear-personal")).sendActions(for: .touchUpInside)
        XCTAssertEqual(view.visiblePanel, "confirm-clear")
        XCTAssertEqual(view.panelUp.title(for: .normal), "Cài đặt nhanh")
        view.panelUp.sendActions(for: .touchUpInside)
        XCTAssertEqual(view.visiblePanel, "settings")
        view.panelBack.sendActions(for: .touchUpInside)
        XCTAssertNil(view.visiblePanel)
        XCTAssertFalse(view.backspace.isHidden)
    }
    func testAppSettingsReachTheKeyboardWithoutLosingQuickEdits() throws {
        let shared = try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))
        var clock = 100.0
        let app = KeyboardPreferences(defaults: try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString)))
        app.onChange = { old, new in
            SharedSettings.publish(new, parts: SharedSettings.parts(changedFrom: old, to: new), in: shared, now: Date(timeIntervalSince1970: clock))
        }
        let keyboard = KeyboardPreferences(defaults: try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString)))
        keyboard.trackLocalEdits { Date(timeIntervalSince1970: clock) }
        XCTAssertEqual(keyboard.applyShared(from: nil), KeyboardPreferences.SharedUpdate())
        XCTAssertFalse(keyboard.applyShared(from: shared).changed)
        // App đổi giao diện, profile, gõ tắt: bàn phím áp một lần.
        app.configuration.theme = 2; app.configuration.activeProfile = "builtin.qwerty"; app.configuration.snippets = ["dc": "được"]
        XCTAssertTrue(keyboard.applyShared(from: shared).changed)
        XCTAssertEqual(keyboard.configuration.theme, 2)
        XCTAssertEqual(keyboard.configuration.activeProfile, "builtin.qwerty")
        XCTAssertEqual(keyboard.configuration.snippets, ["dc": "được"])
        XCTAssertFalse(keyboard.applyShared(from: shared).changed)
        // Đổi profile và học độ lệch ngay trên bàn phím, sau đó app chỉ đổi giao diện: giữ profile và độ lệch của bàn phím.
        clock = 200; keyboard.configuration.activeProfile = "builtin.default"; keyboard.configuration.offsets = ["builtin.default:a": [0.1, 0]]
        clock = 300; app.configuration.theme = 1
        XCTAssertTrue(keyboard.applyShared(from: shared).changed)
        XCTAssertEqual(keyboard.configuration.theme, 1)
        XCTAssertEqual(keyboard.configuration.activeProfile, "builtin.default")
        XCTAssertEqual(keyboard.configuration.offsets.count, 1)
        // App đổi profile sau bàn phím thì app thắng; profile chỉ có trên bàn phím (tạo khi kéo đổi chỗ) vẫn còn.
        clock = 350; var mine = KeyboardProfile.presets[0]; mine.letters.swapAt(1, 2); XCTAssertTrue(keyboard.saveProfile(mine))
        let local = keyboard.configuration.activeProfile
        clock = 400; app.configuration.activeProfile = "builtin.default"; app.configuration.activeProfile = "builtin.qwerty"
        XCTAssertTrue(keyboard.applyShared(from: shared).changed)
        XCTAssertEqual(keyboard.configuration.activeProfile, "builtin.qwerty")
        XCTAssertTrue(keyboard.configuration.profiles.contains { $0.id == local })
        // Profile app tạo rồi xoá: bàn phím bỏ theo, không giữ như profile của mình.
        clock = 420; var appOwn = KeyboardProfile.presets[1]; appOwn.letters.swapAt(1, 2); XCTAssertTrue(app.saveProfile(appOwn))
        let appID = app.configuration.activeProfile
        XCTAssertTrue(keyboard.applyShared(from: shared).changed)
        XCTAssertTrue(keyboard.configuration.profiles.contains { $0.id == appID })
        XCTAssertEqual(keyboard.configuration.profiles.first { $0.id == local }?.name, "Của tôi 2")
        clock = 440; app.configuration.profiles.removeAll { $0.id == appID }; app.configuration.activeProfile = "builtin.default"
        XCTAssertTrue(keyboard.applyShared(from: shared).changed)
        XCTAssertFalse(keyboard.configuration.profiles.contains { $0.id == appID })
        XCTAssertTrue(keyboard.configuration.profiles.contains { $0.id == local })
        // Lệnh từ app chạy một lần.
        SharedSettings.send(.clearPersonal, in: shared, now: Date(timeIntervalSince1970: 500))
        SharedSettings.send(.resetOffsets, in: shared, now: Date(timeIntervalSince1970: 500))
        let update = keyboard.applyShared(from: shared)
        XCTAssertTrue(update.clearPersonal); XCTAssertTrue(keyboard.configuration.offsets.isEmpty)
        XCTAssertFalse(keyboard.applyShared(from: shared).clearPersonal)
        // AppSettings tự gửi phần vừa đổi.
        clock = 600
        let settings = AppSettings(preferences: KeyboardPreferences(defaults: try XCTUnwrap(UserDefaults(suiteName: UUID().uuidString))),
                                   store: KeyboardDataStore(root: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)),
                                   shared: { shared })
        settings.preferences.configuration.emojiTone = 3
        XCTAssertEqual(SharedSettings.configuration(in: shared)?.emojiTone, 3)
    }
    func testSymbolPagesFollowCellsAndPageKeySitsOnSettings() throws {
        let view = HexKeyboardView(preferences: KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!))
        view.frame = CGRect(x: 0,y: 0,width: 402,height: 300); view.layoutIfNeeded()
        var host = ""
        view.onKey = { host += $0 }
        view.activate("#symbols",index: 6)
        let settings = try XCTUnwrap(view.keys.first { $0.2 == "#settings" })
        XCTAssertEqual(settings.0.title(for: .normal),"1/2")
        XCTAssertEqual(view.keys.first { $0.2 == "q" }?.0.title(for: .normal),"1")
        XCTAssertEqual(view.keys.first { $0.2 == "," }?.0.title(for: .normal),",")
        view.activate("q",index: 0)
        view.activate("#settings",index: settings.1)
        XCTAssertEqual(view.keys.first { $0.2 == "#settings" }?.0.title(for: .normal),"2/2")
        XCTAssertEqual(view.keys.first { $0.2 == "," }?.0.title(for: .normal),"<")
        view.activate(",",index: 29)
        XCTAssertEqual(host,"1<")
        XCTAssertNil(view.visiblePanel)
    }
    func testTraditionalLayoutShiftBackspaceAndRectHitTesting() throws {
        let prefs = KeyboardPreferences(defaults: UserDefaults(suiteName: UUID().uuidString)!)
        prefs.configuration.activeProfile = "builtin.qwerty"
        let document = EditorDocument("")
        let driver = KeyboardDriver(preferences: prefs,store: KeyboardDataStore(root: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)),
                                    document: { document },traits: { (.default,.none) },loadVocabulary: { _,_ in })
        let view = driver.view
        driver.update(); view.frame = CGRect(x: 0,y: 0,width: 402,height: 300); view.layoutIfNeeded()
        XCTAssertGreaterThan(view.keys.first { $0.2 == "q" }?.0.frame.width ?? 0,0)
        XCTAssertTrue(view.globe.isHidden)
        func key(_ code: String) throws -> (HexKeyButton,Int,String) { try XCTUnwrap(view.keys.first { $0.2 == code }) }
        // Phím chữ nhật theo thứ tự QWERTY: Q…P cùng một hàng, Z ngay dưới S.
        XCTAssertEqual(try key("q").0.frame.minY,try key("p").0.frame.minY)
        XCTAssertEqual(try key("z").0.frame.midX,try key("s").0.frame.midX,accuracy: 0.5)
        // ⇧ một lần: chỉ chữ kế tiếp hoa.
        view.activate("#shift",index: try key("#shift").1)
        XCTAssertEqual(try key("h").0.title(for: .normal),"H")
        view.activate("h",index: try key("h").1); view.activate("i",index: try key("i").1)
        XCTAssertEqual(document.text,"Hi")
        view.activate("#backspace",index: try key("#backspace").1)
        XCTAssertEqual(document.text,"H")
        // Chạm đúp ⇧ khoá chữ hoa; chạm lần nữa thì tắt.
        driver.pressShift(); driver.pressShift()
        XCTAssertTrue(view.capsLocked)
        view.activate("a",index: try key("a").1); view.activate("b",index: try key("b").1)
        XCTAssertEqual(document.text,"HAB")
        driver.pressShift()
        XCTAssertFalse(view.capsLocked)
        view.activate("c",index: try key("c").1)
        XCTAssertEqual(document.text,"HABc")
        // Khoảng trống nửa ô cạnh ⇧ vẫn nhận ⇧, không bỏ lỡ lần chạm.
        let shift = try key("#shift").0.frame
        XCTAssertEqual(view.cell(at: CGPoint(x: shift.maxX + 4,y: shift.midY)),try key("#shift").1)
    }
}
