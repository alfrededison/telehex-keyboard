import UIKit

final class KeyboardGesture: UIGestureRecognizer {
    enum Event { case begin(Int, CGPoint), move(Int, CGPoint, CGPoint, [CGPoint], Set<Int>, Bool), end(Int, CGPoint, CGPoint, [CGPoint], Set<Int>, Bool), cancel }
    var keyAt: ((CGPoint) -> Int?)?
    /// Phím dưới các điểm trên đường vuốt (không áp vùng chạm động); nil thì dùng `keyAt`.
    var trackAt: ((CGPoint) -> Int?)?
    var emit: ((Event) -> Void)?
    var holdDelay: ((Int) -> Double)?
    private var start = CGPoint.zero, last = CGPoint.zero
    private var origin: Int?
    private var path: [CGPoint] = []
    private var visited = Set<Int>()
    private var held = false
    private var timer: Timer?
    /// Gõ nhanh hai ngón cái như Android: ngón thứ hai chạm xuống khi ngón đầu còn chạm tĩnh. Hai lần nhả được giữ lại tới
    /// khi cả hai ngón nhấc, rồi gõ từng phím theo thứ tự nhả; cả hai cùng vuốt ngang thì là cử chỉ hai ngón, không gõ gì.
    private struct Finger { let touch: UITouch; let key: Int; let start: CGPoint; var last: CGPoint }
    private var primaryTouch: UITouch?
    private var second: Finger?
    private var multi = false
    private var released: [(key: Int, start: CGPoint, last: CGPoint)] = []
    /// `cancel()` được gọi trong lúc đang phát phím đầu (phím đó đổi lớp): bỏ phím còn lại.
    private var cancelledWhileEmitting = false
    /// Đang gọi `emit`: phím vừa nhả có thể tự đổi lớp (gọi `cancel`), lúc đó không huỷ chính lần chạm này.
    private var emitting = false
    private func send(_ event: Event) { emitting = true; emit?(event); emitting = false }
    /// Huỷ lần chạm đang theo dõi (đổi lớp/bố cục, bàn phím ẩn); phần còn lại của lần chạm bị bỏ qua.
    func cancel() {
        if emitting { cancelledWhileEmitting = true }
        guard origin != nil, !emitting else { return }
        timer?.invalidate(); timer = nil; origin = nil
        emit?(.cancel)
        if state == .possible { state = .failed } else if state == .began || state == .changed { state = .cancelled }
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        if origin != nil {
            // Ngón thứ hai trong lúc ngón đầu còn chạm tĩnh (chưa giữ lâu, chưa vuốt): chờ cả hai nhả rồi gõ lần lượt
            if !multi, !held, state == .possible, touches.count == 1, let touch = touches.first, let view,
               hypot(last.x - start.x, last.y - start.y) <= 12, let key = keyAt?(touch.location(in: view)) {
                timer?.invalidate(); multi = true
                let point = touch.location(in: view)
                second = Finger(touch: touch, key: key, start: point, last: point)
                return
            }
            timer?.invalidate(); send(.cancel); state = state == .possible ? .failed : .cancelled; return
        }
        // Hai ngón chạm xuống cùng lúc trên hai phím: cũng gõ cả hai
        let ordered = touches.sorted { $0.timestamp < $1.timestamp }
        guard ordered.count <= 2, let touch = ordered.first, let view, let key = keyAt?(touch.location(in: view)) else { state = .failed; return }
        start = touch.location(in: view); last = start; origin = key; path = [start]; visited = [key]; primaryTouch = touch
        if ordered.count == 2 {
            let point = ordered[1].location(in: view)
            guard let other = keyAt?(point) else { origin = nil; primaryTouch = nil; state = .failed; return }
            multi = true; second = Finger(touch: ordered[1], key: other, start: point, last: point)
            send(.begin(key, start)); return
        }
        send(.begin(key, start))
        timer = Timer.scheduledTimer(withTimeInterval: holdDelay?(key) ?? 0.8, repeats: false) { [weak self] _ in
            guard let self, let origin = self.origin, hypot(self.last.x - self.start.x, self.last.y - self.start.y) < 12, self.state == .possible else { return }
            self.held = true; self.state = .began
            self.send(.move(origin, self.start, self.last, self.path, self.visited, true))
        }
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
        if multi {
            // Hai ngón: chỉ ghi vị trí, quyết định khi cả hai nhả
            guard let view else { return }
            for touch in touches {
                if touch === primaryTouch { last = touch.location(in: view) }
                else if touch === second?.touch { second?.last = touch.location(in: view) }
            }
            return
        }
        guard let touch = touches.first, let view, let origin else { return }
        last = touch.location(in: view)
        if path.last.map({ hypot($0.x - last.x, $0.y - last.y) > 2 }) == true { path.append(last); if path.count > 256 { path.remove(at: 1) } }
        if let index = (trackAt ?? keyAt)?(last) { visited.insert(index) }
        if hypot(last.x - start.x, last.y - start.y) > 12 {
            timer?.invalidate()
            if state == .possible { state = .began } else { state = .changed }
        }
        if state == .began || state == .changed { send(.move(origin, start, last, path, visited, held)) }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent) {
        timer?.invalidate()
        if multi, let origin {
            guard let view else { return }
            for touch in touches.sorted(by: { $0.timestamp < $1.timestamp }) {
                if touch === primaryTouch { released.append((origin, start, touch.location(in: view))); primaryTouch = nil }
                else if let finger = second, touch === finger.touch { released.append((finger.key, finger.start, touch.location(in: view))); second = nil }
            }
            guard primaryTouch == nil, second == nil else { return }
            resolveMulti()
            return
        }
        guard let origin else { if state == .possible { state = .failed }; return }
        if let touch = touches.first, let view { last = touch.location(in: view); path.append(last) }
        send(.end(origin, start, last, path, visited, held))
        if state == .possible || state == .began || state == .changed { state = .ended }
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent) {
        timer?.invalidate()
        guard origin != nil else { if state == .possible { state = .failed }; return }
        send(.cancel); state = .cancelled
    }
    /// Cả hai ngón đã nhấc: cùng vuốt ngang một hướng là cử chỉ hai ngón (hoàn tác/làm lại do bộ nhận vuốt hai ngón xử lý),
    /// không gõ gì; còn lại gõ từng phím theo thứ tự nhả, bỏ phím sau nếu phím trước đã đổi lớp.
    private func resolveMulti() {
        let fingers = released
        released = []; multi = false; origin = nil
        let moves = fingers.map { CGPoint(x: $0.last.x - $0.start.x, y: $0.last.y - $0.start.y) }
        let swipes = moves.filter { abs($0.x) > 12 && abs($0.x) > abs($0.y) }
        if fingers.count >= 2, swipes.count == fingers.count, Set(swipes.map { $0.x < 0 }).count == 1 {
            send(.cancel); state = state == .possible ? .failed : .cancelled; return
        }
        cancelledWhileEmitting = false
        for finger in fingers {
            if cancelledWhileEmitting { break }
            send(.end(finger.key, finger.start, finger.last, [finger.start, finger.last], [finger.key], false))
        }
        if state == .possible || state == .began || state == .changed { state = .ended }
    }
    override func reset() {
        super.reset(); timer?.invalidate(); timer = nil; origin = nil; path = []; visited = []; held = false
        primaryTouch = nil; second = nil; multi = false; released = []
    }
    deinit { timer?.invalidate() }
}

final class EditorDocument: TextDocument {
    var text: String
    let documentID: UUID? = UUID()
    init(_ text: String) { self.text = text }
    var beforeInput: String? { text }
    var afterInput: String? { "" }
    var selection: String? { nil }
    func insert(_ value: String) { text += value }
    func deleteBackward() { if !text.isEmpty { text.removeLast() } }
}
