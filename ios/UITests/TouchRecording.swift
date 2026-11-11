import XCTest
extension XCUIElement {
    /// Tap and log the element's centre. `app` gives the screen size for scaling.
    func recordedTap(in app: XCUIApplication) {
        XCTAssertTrue(waitForExistence(timeout: 10))
        for _ in 0..<40 {
            if isHittable && !frame.isInfinite && !frame.isEmpty { break }
            Thread.sleep(forTimeInterval: 0.1)
        }
        let f = frame, s = app.frame
        XCTAssertFalse(f.isInfinite)
        FileHandle.standardOutput.write(Data("TOUCH_TAP \(Date().timeIntervalSince1970) \(f.midX) \(f.midY) \(s.width) \(s.height)\n".utf8))
        tap()
    }
}

extension XCUIApplication {
    /// Swipe between two normalized points (0...1) and log it.
    func recordedSwipe(from a: CGVector, to b: CGVector, hold: TimeInterval = 0.1) {
        let s = frame
        FileHandle.standardOutput.write(Data(
            "TOUCH_SWIPE \(Date().timeIntervalSince1970) \(s.width * a.dx) \(s.height * a.dy) \(s.width * b.dx) \(s.height * b.dy) \(s.width) \(s.height)\n".utf8))
        coordinate(withNormalizedOffset: a).press(forDuration: hold, thenDragTo: coordinate(withNormalizedOffset: b))
    }
}
