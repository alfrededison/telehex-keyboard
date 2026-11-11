import UIKit
final class TouchRecordingWindow: UIWindow {
    private let enabled = ProcessInfo.processInfo.arguments.contains("--record-touches")

    override func sendEvent(_ event: UIEvent) {
        super.sendEvent(event)
        guard enabled else { return }
        for touch in event.allTouches ?? [] where touch.phase == .began {
            let p = touch.location(in: self)
            let ring = UIView(frame: CGRect(x: p.x - 22, y: p.y - 22, width: 44, height: 44))
            ring.backgroundColor = UIColor.systemOrange.withAlphaComponent(0.45)
            ring.layer.cornerRadius = 22
            ring.isUserInteractionEnabled = false
            addSubview(ring)
            UIView.animate(withDuration: 0.6, animations: { ring.alpha = 0 }, completion: { _ in ring.removeFromSuperview() })
        }
    }
}
