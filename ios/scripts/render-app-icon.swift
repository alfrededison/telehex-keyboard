// Dùng: swift ios/scripts/render-app-icon.swift ios/Sources/App/Assets.xcassets/AppIcon.appiconset/AppIcon.png
// Vẽ icon iOS 1024×1024 từ vector icon Android (ic_launcher_foreground/background), khung như icon Play Store:
// vùng an toàn 18…90 của viewport 108 phủ cả ảnh, nền #DCE8FB, không trong suốt.
import AppKit
import CoreGraphics

let size = 1024
let scale = CGFloat(size) / 72, offset: CGFloat = 18
func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: (x - offset) * scale, y: CGFloat(size) - (y - offset) * scale) }
func color(_ hex: UInt32) -> CGColor {
    CGColor(srgbRed: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1)
}
/// Đọc pathData chỉ gồm M L H Q Z với toạ độ tuyệt đối.
func path(_ data: String) -> CGPath {
    let p = CGMutablePath()
    var tokens: [String] = []
    var current = ""
    for ch in data {
        if "MLHQZ".contains(ch) { if !current.isEmpty { tokens.append(current) }; tokens.append(String(ch)); current = "" }
        else if ch == "," || ch == " " || ch == "\n" { if !current.isEmpty { tokens.append(current) }; current = "" }
        else { current.append(ch) }
    }
    if !current.isEmpty { tokens.append(current) }
    var i = 0, command = "M", last = CGPoint.zero
    func number() -> CGFloat { defer { i += 1 }; return CGFloat(Double(tokens[i])!) }
    while i < tokens.count {
        if "MLHQZ".contains(tokens[i]) { command = tokens[i]; i += 1; if command == "Z" { p.closeSubpath(); continue } }
        switch command {
        case "M": let x = number(), y = number(); last = CGPoint(x: x, y: y); p.move(to: pt(x, y)); command = "L"
        case "L": let x = number(), y = number(); last = CGPoint(x: x, y: y); p.addLine(to: pt(x, y))
        case "H": let x = number(); last = CGPoint(x: x, y: last.y); p.addLine(to: pt(x, last.y))
        case "Q": let cx = number(), cy = number(), x = number(), y = number(); last = CGPoint(x: x, y: y); p.addQuadCurve(to: pt(x, y), control: pt(cx, cy))
        default: i += 1
        }
    }
    return p
}
let shapes: [(UInt32, String)] = [
    (0x0A3A80, "M54,22 L80,37 L80,71 L54,86 L28,71 L28,37 Z"),
    (0xFFFFFF, "M54,30 L73,41 L73,67 L54,78 L35,67 L35,41 Z"),
    (0x0A3A80, """
M58.3432,63.173 L64.3973,64.1892 Q63.2297,67.5189 60.7108,69.2595 Q58.1919,71 54.4081,71 Q48.4189,71 45.5432,67.0865 Q43.273,63.9514 43.273,59.173
Q43.273,53.4649 46.2567,50.2324 Q49.2405,47 53.8027,47 Q58.927,47 61.8892,50.3838 Q64.8513,53.7676 64.7216,60.7514
H49.5 Q49.5648,63.4541 50.9703,64.9568 Q52.3757,66.4595 54.473,66.4595 Q55.9,66.4595 56.873,65.6811 Q57.8459,64.9027 58.3432,63.173 Z
M58.6892,57.0324 Q58.6243,54.3946 57.327,53.0216 Q56.0297,51.6486 54.1703,51.6486 Q52.1811,51.6486 50.8838,53.0973 Q49.5865,54.5459 49.6081,57.0324 Z
"""),
    (0xE8882F, "M45.5,44 L51.5,37.5 Q54,35 56.5,37.5 L62.5,44 L58,44 L54,40 L50,44 Z"),
]
let context = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8, bytesPerRow: 0,
                        space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
context.setFillColor(color(0xDCE8FB)); context.fill(CGRect(x: 0, y: 0, width: size, height: size))
for (fill, data) in shapes {
    context.addPath(path(data)); context.setFillColor(color(fill)); context.fillPath(using: .evenOdd)
}
let image = context.makeImage()!
let rep = NSBitmapImageRep(cgImage: image)
try! rep.representation(using: .png, properties: [:])!.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
