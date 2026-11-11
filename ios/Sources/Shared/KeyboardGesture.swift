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
    /// Đang gọi `emit`: phím vừa nhả có thể tự đổi lớp (gọi `cancel`), lúc đó không huỷ chính lần chạm này.
    private var emitting = false
    private func send(_ event: Event) { emitting = true; emit?(event); emitting = false }
    /// Huỷ lần chạm đang theo dõi (đổi lớp/bố cục, bàn phím ẩn); phần còn lại của lần chạm bị bỏ qua.
    func cancel() {
        guard origin != nil, !emitting else { return }
        timer?.invalidate(); timer = nil; origin = nil
        emit?(.cancel)
        if state == .possible { state = .failed } else if state == .began || state == .changed { state = .cancelled }
    }
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent) {
        guard origin == nil else { timer?.invalidate(); send(.cancel); state = state == .possible ? .failed : .cancelled; return }
        guard touches.count == 1, let touch = touches.first, let view, let key = keyAt?(touch.location(in: view)) else { state = .failed; return }
        start = touch.location(in: view); last = start; origin = key; path = [start]; visited = [key]
        send(.begin(key, start))
        timer = Timer.scheduledTimer(withTimeInterval: holdDelay?(key) ?? 0.8, repeats: false) { [weak self] _ in
            guard let self, let origin = self.origin, hypot(self.last.x - self.start.x, self.last.y - self.start.y) < 12, self.state == .possible else { return }
            self.held = true; self.state = .began
            self.send(.move(origin, self.start, self.last, self.path, self.visited, true))
        }
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent) {
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
    override func reset() { super.reset(); timer?.invalidate(); timer = nil; origin = nil; path = []; visited = []; held = false }
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
