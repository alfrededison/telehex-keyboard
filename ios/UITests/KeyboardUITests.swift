import XCTest

final class KeyboardUITests: XCTestCase {
    override func setUp() { continueAfterFailure = false }

    private func preview() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--embedded-preview","--record-touches","--reset-test-data"]
        app.launch(); app.textViews["typing-field"].recordedTap(in: app)
        return app
    }
    private func panelTap(_ id: String, in app: XCUIApplication) {
        let button = app.buttons[id]
        let panel = app.scrollViews["keyboard-panel"]
        XCTAssertTrue(button.waitForExistence(timeout: 10))
        for _ in 0..<16 where !button.isHittable {
            let f = panel.frame, s = app.frame
            let down = button.frame.midY < f.minY + 15
            let y = down ? f.midY - 45 : f.midY + 45
            let end = down ? y + 90 : y - 90
            app.recordedSwipe(from: CGVector(dx: f.midX/s.width,dy: y/s.height),to: CGVector(dx: f.midX/s.width,dy: end/s.height))
        }
        button.recordedTap(in: app)
    }
    /// Mở một mục trong thẻ "Cài đặt" của trang chính; kéo trang để ẩn bàn phím và cuộn tới mục.
    private func openSettings(_ id: String, in app: XCUIApplication) {
        let row = app.buttons[id]
        XCTAssertTrue(row.waitForExistence(timeout: 10))
        // Vuốt ở nửa trên (ngoài vùng bàn phím): trang cuộn lên và bàn phím ẩn đi.
        for _ in 0..<8 where !row.isHittable { app.recordedSwipe(from: CGVector(dx: 0.5, dy: 0.42), to: CGVector(dx: 0.5, dy: 0.15)) }
        row.recordedTap(in: app)
    }
    private func back(_ app: XCUIApplication) { app.navigationBars.buttons.element(boundBy: 0).recordedTap(in: app) }
    private func backHome(_ app: XCUIApplication) {
        for _ in 0..<4 where !app.navigationBars["TeleHex"].exists { back(app) }
        for _ in 0..<6 where !app.textViews["typing-field"].isHittable { app.recordedSwipe(from: CGVector(dx: 0.5, dy: 0.3), to: CGVector(dx: 0.5, dy: 0.8)) }
    }
    func testLayoutEditorLayerTransitions() {
        let app = preview()
        openSettings("settings-keyboard", in: app)
        app.cells["edit-layout"].recordedTap(in: app)
        app.buttons["key-w"].recordedTap(in: app)
        app.buttons["layout-layer-2"].recordedTap(in: app)
        app.buttons["key-r"].recordedTap(in: app)
        XCTAssertTrue(app.buttons["layout-layer-2"].isHittable)
        app.buttons["key-e"].recordedTap(in: app)
        app.buttons["key-w"].recordedTap(in: app)
        app.buttons["layout-layer-3"].recordedTap(in: app)
        app.buttons["key-r"].recordedTap(in: app)
        app.buttons["layout-layer-0"].recordedTap(in: app)
        app.buttons["key-w"].recordedTap(in: app)
        app.buttons["layout-layer-1"].recordedTap(in: app)
        app.buttons["layout-layer-2"].recordedTap(in: app)
        app.buttons["key-e"].recordedTap(in: app)
        app.buttons["layout-layer-4"].recordedTap(in: app)
        // Sửa profile có sẵn tạo bản riêng "Của tôi" và dùng luôn.
        XCTAssertTrue(app.cells["settings-profiles"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.cells["settings-profiles"].staticTexts["Của tôi"].exists)
        backHome(app)
        app.textViews["typing-field"].recordedTap(in: app)
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"")
        XCTAssertTrue(app.buttons["key-w"].waitForExistence(timeout: 5))
    }
    func testSystemFunctionSwipesCannotInsertInternalKeyCodes() {
        let app = XCUIApplication(); app.launchArguments = ["--record-touches"]; app.launch()
        app.textViews["typing-field"].recordedTap(in: app)
        let keyboard = XCUIApplication(bundleIdentifier: "com.telehex.keyboard.extension")
        for _ in 0..<5 where !keyboard.buttons["key-t"].waitForExistence(timeout: 3) { app.buttons["Next keyboard"].recordedTap(in: app) }
        let screen = app.frame
        for code in ["#settings","#symbols","#emoji"] {
            let key = keyboard.buttons["key-\(code)"].frame
            app.recordedSwipe(from: CGVector(dx: key.midX/screen.width,dy: key.midY/screen.height),to: CGVector(dx: key.midX/screen.width,dy: (key.midY - 55)/screen.height),hold: 0.05)
            XCTAssertEqual(app.textViews["typing-field"].value as? String,"")
        }
        let letter = keyboard.buttons["key-a"].frame
        app.recordedSwipe(from: CGVector(dx: letter.midX/screen.width,dy: letter.midY/screen.height),to: CGVector(dx: letter.midX/screen.width,dy: (letter.midY - 55)/screen.height),hold: 0.05)
        keyboard.buttons["key- "].recordedTap(in: app)
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"A ")
    }

    func testProfilesSymbolsAndEmoji() {
        let app = preview()
        openSettings("settings-keyboard", in: app)
        app.cells["edit-layout"].recordedTap(in: app)
        app.buttons["key-w"].recordedTap(in: app); app.buttons["key-r"].recordedTap(in: app)
        app.buttons["layout-layer-4"].recordedTap(in: app)
        backHome(app)
        app.textViews["typing-field"].recordedTap(in: app)
        // Lớp 123 gắn theo ô: đổi chỗ W và R ở lớp chữ không kéo ký hiệu theo. Phím 1/2 ở ô ⚙.
        app.buttons["key-#symbols"].recordedTap(in: app)
        app.buttons["key-q"].recordedTap(in: app)
        app.buttons["key-#settings"].recordedTap(in: app)
        app.buttons["key-r"].recordedTap(in: app)
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"1`")
        app.buttons["key-#symbols"].recordedTap(in: app)
        app.buttons["key-#emoji"].recordedTap(in: app)
        XCTAssertTrue(app.buttons["emoji-next"].exists)
        app.buttons["search-emoji"].recordedTap(in: app)
        for key in "cuoiw" { app.buttons["key-\(key)"].recordedTap(in: app) }
        app.buttons["save-editor"].recordedTap(in: app)
        let emoji = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@","emoji-😀")).firstMatch
        XCTAssertTrue(emoji.waitForExistence(timeout: 10)); panelTap(emoji.identifier,in: app)
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"1`😀")
    }
    func testSnippetsAndPrivacy() {
        let app = preview()
        openSettings("settings-snippets", in: app)
        app.cells["add-snippet"].recordedTap(in: app)
        // Gõ bằng chính bàn phím TeleHex (cần đã thêm trong Cài đặt Simulator, như testSystemExtension).
        let keyboard = XCUIApplication(bundleIdentifier: "com.telehex.keyboard.extension")
        func type(_ keys: String, into field: XCUIElement) {
            field.recordedTap(in: app)
            for _ in 0..<5 where !keyboard.buttons["key-t"].waitForExistence(timeout: 3) { app.buttons["Next keyboard"].recordedTap(in: app) }
            // Đợi iOS gắn bàn phím vào ô vừa chọn trước khi gõ phím đầu.
            Thread.sleep(forTimeInterval: 1)
            if keyboard.buttons["language-mode"].label == "EN" { keyboard.buttons["language-mode"].recordedTap(in: app) }
            for key in keys { keyboard.buttons["key-\(key)"].recordedTap(in: app) }
        }
        type("dc", into: app.textFields["snippet-key"])
        type("ddiaj chir", into: app.textViews["snippet-value"])
        app.buttons["save-snippet"].recordedTap(in: app)
        XCTAssertTrue(app.cells["snippet-dc"].waitForExistence(timeout: 5))
        backHome(app)
        app.textViews["typing-field"].recordedTap(in: app)
        for key in "dc " { app.buttons["key-\(key)"].recordedTap(in: app) }
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"địa chỉ ")
        // Ẩn danh bật nhanh ngay trên bàn phím.
        app.buttons["key-#settings"].recordedTap(in: app)
        let privacy = app.switches["incognito"]; privacy.recordedTap(in: app)
        XCTAssertEqual(privacy.value as? String,"1")
        app.buttons["close-settings"].recordedTap(in: app)
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"địa chỉ ")
    }
    func testTraditionalProfileShiftAndBackspace() {
        let app = preview()
        XCTAssertFalse(app.buttons["next-keyboard"].exists)
        app.buttons["key-#settings"].recordedTap(in: app)
        panelTap("profile-builtin.qwerty",in: app)
        app.buttons["close-settings"].recordedTap(in: app)
        XCTAssertTrue(app.buttons["key-#shift"].waitForExistence(timeout: 5))
        app.buttons["key-#shift"].recordedTap(in: app)
        for key in "xin chaof" { app.buttons["key-\(key)"].recordedTap(in: app) }
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"Xin chào")
        app.buttons["key-#backspace"].recordedTap(in: app)
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"Xin chà")
        app.buttons["key-#symbols"].recordedTap(in: app)
        app.buttons["key-q"].recordedTap(in: app); app.buttons["key-#shift"].recordedTap(in: app); app.buttons["key-."].recordedTap(in: app)
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"Xin chà1>")
    }
    func testGlideAndSpaceGestures() {
        let app = preview()
        let a = app.buttons["key-x"].frame, b = app.buttons["key-n"].frame, s = app.frame
        app.recordedSwipe(from: CGVector(dx: a.midX/s.width,dy: a.midY/s.height),to: CGVector(dx: b.midX/s.width,dy: b.midY/s.height),hold: 0.05)
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"")
        app.buttons["more-candidates"].recordedTap(in: app)
        XCTAssertTrue(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@","expanded-candidate-")).firstMatch.waitForExistence(timeout: 10))
        app.buttons["close-settings"].recordedTap(in: app)
        let space = app.buttons["key- "].frame
        app.recordedSwipe(from: CGVector(dx: space.midX/s.width,dy: space.midY/s.height),to: CGVector(dx: space.midX/s.width,dy: (space.midY + 40)/s.height),hold: 0.05)
        XCTAssertEqual(app.buttons["language-mode"].label,"EN")
        for key in "hello " { app.buttons["key-\(key)"].recordedTap(in: app) }
        app.recordedSwipe(from: CGVector(dx: space.midX/s.width,dy: space.midY/s.height),to: CGVector(dx: (space.midX - 100)/s.width,dy: space.midY/s.height),hold: 0.05)
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"")
    }
    func testAppearanceAndDialpad() {
        let app = preview()
        let original = app.buttons["key-w"].frame.minX
        openSettings("settings-keyboard", in: app)
        app.segmentedControls["theme"].buttons["Tối"].recordedTap(in: app)
        app.segmentedControls["one-hand"].buttons["Phải"].recordedTap(in: app)
        backHome(app)
        app.textViews["typing-field"].recordedTap(in: app)
        XCTAssertTrue(app.buttons["key-w"].waitForExistence(timeout: 5))
        XCTAssertGreaterThan(app.buttons["key-w"].frame.minX,original)
        let key = app.buttons["key-#symbols"].frame, s = app.frame
        let center = CGVector(dx: key.midX/s.width,dy: key.midY/s.height)
        app.recordedSwipe(from: center,to: center,hold: 1)
        XCTAssertTrue(app.buttons["key-1"].waitForExistence(timeout: 5))
        app.buttons["key-1"].recordedTap(in: app); app.buttons["key-0"].recordedTap(in: app)
        XCTAssertEqual(app.textViews["typing-field"].value as? String,"10")
        app.buttons["key-ABC"].recordedTap(in: app)
        XCTAssertTrue(app.buttons["key-w"].exists)
    }
    func testLanguagePackPickerInSettings() {
        let app = XCUIApplication(); app.launchArguments = ["--record-touches", "--reset-test-data"]; app.launch()
        openSettings("settings-data", in: app)
        app.cells["import-VI"].recordedTap(in: app)
        let cancel = app.buttons["Cancel"].firstMatch
        XCTAssertTrue(cancel.waitForExistence(timeout: 10))
        cancel.recordedTap(in: app)
        XCTAssertTrue(app.cells["import-VI"].waitForExistence(timeout: 10))
    }
    func testEnableKeyboardInSettings() {
        let settings = XCUIApplication(bundleIdentifier: "com.apple.Preferences")
        settings.launch()
        if !settings.cells["KEYBOARDS"].exists {
            let general = settings.staticTexts["General"].firstMatch
            if general.exists {
                for _ in 0..<4 where !general.isHittable {
                    settings.recordedSwipe(from: CGVector(dx: 0.5, dy: 0.75), to: CGVector(dx: 0.5, dy: 0.25))
                }
                general.recordedTap(in: settings)
            }
            let keyboard = settings.staticTexts["Keyboard"].firstMatch
            for _ in 0..<3 where !keyboard.isHittable {
                settings.recordedSwipe(from: CGVector(dx: 0.5, dy: 0.75), to: CGVector(dx: 0.5, dy: 0.25))
            }
            keyboard.recordedTap(in: settings)
        }
        settings.cells["KEYBOARDS"].recordedTap(in: settings)
        let installed = settings.cells.matching(NSPredicate(format: "label CONTAINS %@", "TeleHex")).firstMatch
        if !installed.exists {
            settings.cells["AddNewKeyboard"].recordedTap(in: settings)
            let available = settings.cells.matching(NSPredicate(format: "label CONTAINS %@", "TeleHex")).firstMatch
            XCTAssertTrue(available.waitForExistence(timeout: 10))
            available.recordedTap(in: settings)
        }
        XCTAssertTrue(settings.cells["AddNewKeyboard"].waitForExistence(timeout: 10))
        XCTAssertTrue(installed.exists)
    }

    func testVietnameseEnglishAndSettings() {
        let app = XCUIApplication()
        app.launchArguments = ["--embedded-preview", "--record-touches", "--reset-test-data"]
        app.launch()
        let field = app.textViews["typing-field"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.recordedTap(in: app)
        for key in "tieengs vieetj" { app.buttons["key-\(key)"].recordedTap(in: app) }
        XCTAssertEqual(field.value as? String, "tiếng việt")
        app.buttons["backspace"].recordedTap(in: app)
        XCTAssertEqual(field.value as? String, "tiếng việ")
        app.buttons["key- "].recordedTap(in: app)
        app.buttons["language-mode"].recordedTap(in: app)
        for key in "hel" { app.buttons["key-\(key)"].recordedTap(in: app) }
        XCTAssertTrue(app.buttons["candidate-help"].waitForExistence(timeout: 15))
        app.buttons["candidate-help"].recordedTap(in: app)
        app.buttons["key-,"].recordedTap(in: app)
        XCTAssertEqual(field.value as? String, "tiếng việ help,")
        app.buttons["key-#settings"].recordedTap(in: app)
        XCTAssertTrue(app.switches["incognito"].waitForExistence(timeout: 5))
        app.buttons["close-settings"].recordedTap(in: app)
        XCTAssertTrue(app.buttons["key-t"].isHittable)
    }

    func testSystemExtension() {
        let app = XCUIApplication()
        app.launchArguments = ["--record-touches"]
        app.launch()
        let field = app.textViews["typing-field"]
        XCTAssertTrue(field.waitForExistence(timeout: 10))
        field.recordedTap(in: app)
        let keyboard = XCUIApplication(bundleIdentifier: "com.telehex.keyboard.extension")
        for _ in 0..<5 where !keyboard.buttons["key-t"].waitForExistence(timeout: 5) {
            app.buttons["Next keyboard"].recordedTap(in: app)
        }
        XCTAssertTrue(keyboard.buttons["key-t"].exists)
        if keyboard.buttons["language-mode"].label == "EN" { keyboard.buttons["language-mode"].recordedTap(in: app) }
        for key in "tieengs vieetj" { keyboard.buttons["key-\(key)"].recordedTap(in: app) }
        XCTAssertEqual(field.value as? String, "tiếng việt")
        keyboard.buttons["key- "].recordedTap(in: app)
        keyboard.buttons["language-mode"].recordedTap(in: app)
        XCTAssertEqual(keyboard.buttons["language-mode"].label, "EN")
        for key in "hel" { keyboard.buttons["key-\(key)"].recordedTap(in: app) }
        XCTAssertEqual(field.value as? String, "tiếng việt hel")
        XCTAssertTrue(keyboard.buttons["candidate-help"].waitForExistence(timeout: 15))
        keyboard.buttons["candidate-help"].recordedTap(in: app)
        XCTAssertEqual(field.value as? String, "tiếng việt help ")
    }
}
