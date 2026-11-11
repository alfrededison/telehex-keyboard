import UIKit

/// Loại phím quyết định màu. Theo bàn phím hệ thống của iOS: phụ âm, ký hiệu và phím cách nền sáng; phím chức năng nền xám;
/// như Android, nguyên âm tô màu nhấn nhạt và phím dấu tô cam nhạt; ↵ mang màu nhấn.
enum KeyKind { case consonant, vowel, tone, function, enter, space, symbol }

/// Bảng màu của bàn phím và các bảng cài đặt, theo giao diện bàn phím hệ thống iOS và danh sách nhóm của app Cài đặt.
struct KeyboardTheme: Equatable {
    var background: UIColor
    var keyBg: UIColor, keyFg: UIColor
    /// Phím nguyên âm: màu nhấn nhạt, như Android. Màu nhấn "Không" thì trùng phím chữ.
    var vowelBg: UIColor, vowelFg: UIColor
    /// Phím dấu Telex (s f r x j z): cam nhạt như Android; màu nhấn "Không" thì trùng phím chữ.
    var toneBg: UIColor, toneFg: UIColor
    var functionBg: UIColor, functionFg: UIColor
    var enterBg: UIColor, enterFg: UIColor
    /// Bóng 1pt dưới mỗi phím, như phím iOS.
    var keyShadow: UIColor
    var hintFg: UIColor
    /// Nền phím đang nhấn: phím chữ chuyển xám, phím chức năng chuyển sáng (như iOS).
    var keyPressedBg: UIColor, functionPressedBg: UIColor
    /// Ô danh sách trong bảng cài đặt, đường kẻ giữa các ô, chữ phụ.
    var cellBg: UIColor, cellPressedBg: UIColor, separator: UIColor, secondaryFg: UIColor
    /// Màu nhấn: công tắc, ô đang chọn, nét gõ vuốt.
    var accent: UIColor, accentFg: UIColor
    var toastBg: UIColor, toastFg: UIColor
    var isDark: Bool

    func background(for kind: KeyKind) -> UIColor {
        switch kind {
        case .function: return functionBg
        case .enter: return enterBg
        case .vowel: return vowelBg
        case .tone: return toneBg
        default: return keyBg
        }
    }
    func foreground(for kind: KeyKind) -> UIColor {
        switch kind {
        case .function: return functionFg
        case .enter: return enterFg
        case .vowel: return vowelFg
        case .tone: return toneFg
        default: return keyFg
        }
    }
    func pressedBackground(for kind: KeyKind) -> UIColor {
        switch kind {
        case .function, .enter: return functionPressedBg
        default: return keyPressedBg
        }
    }

    /// Màu nhấn tô phím nguyên âm (như Android), ↵ và các điều khiển; `none` bỏ màu cả nguyên âm lẫn phím dấu, ↵ xám.
    func withAccent(_ choice: KeyboardAccent) -> KeyboardTheme {
        var theme = self
        let vowel = choice.vowel(dark: isDark)
        theme.vowelBg = vowel.bg; theme.vowelFg = vowel.fg
        if choice == .none {
            theme.toneBg = keyBg; theme.toneFg = keyFg
            theme.enterBg = functionBg; theme.enterFg = functionFg
            theme.accent = isDark ? UIColor(hex: 0x8E8E93) : UIColor(hex: 0x6C6C70); theme.accentFg = .white
        } else {
            theme.enterBg = choice.color(dark: isDark); theme.enterFg = .white
            theme.accent = choice.color(dark: isDark); theme.accentFg = .white
        }
        return theme
    }

    static let light = KeyboardTheme(
        background: UIColor(hex: 0xD1D4DA),
        keyBg: UIColor(hex: 0xFFFFFF), keyFg: UIColor(hex: 0x000000),
        vowelBg: UIColor(hex: 0xDCE8FB), vowelFg: UIColor(hex: 0x0A3A80),
        toneBg: UIColor(hex: 0xFFE2CC), toneFg: UIColor(hex: 0x7A3300),
        functionBg: UIColor(hex: 0xA9AFBA), functionFg: UIColor(hex: 0x000000),
        enterBg: UIColor(hex: 0x1A73E8), enterFg: .white,
        keyShadow: UIColor(hex: 0x898A8D),
        hintFg: UIColor(hex: 0x6E7076),
        keyPressedBg: UIColor(hex: 0xA9AFBA), functionPressedBg: UIColor(hex: 0xFFFFFF),
        cellBg: UIColor(hex: 0xFFFFFF), cellPressedBg: UIColor(hex: 0xE3E3E8), separator: UIColor(hex: 0xC6C6C8), secondaryFg: UIColor(hex: 0x5F6066),
        accent: UIColor(hex: 0x1A73E8), accentFg: .white,
        toastBg: UIColor(hex: 0x303134), toastFg: .white,
        isDark: false)
    static let dark = KeyboardTheme(
        background: UIColor(hex: 0x1E1E20),
        keyBg: UIColor(hex: 0x5C5C60), keyFg: UIColor(hex: 0xFFFFFF),
        vowelBg: UIColor(hex: 0x1E3A64), vowelFg: UIColor(hex: 0xCFE0FB),
        toneBg: UIColor(hex: 0x5A3313), toneFg: UIColor(hex: 0xFFD9BC),
        functionBg: UIColor(hex: 0x3A3A3D), functionFg: UIColor(hex: 0xFFFFFF),
        enterBg: UIColor(hex: 0x4C8DF6), enterFg: .white,
        keyShadow: UIColor(hex: 0x000000, alpha: 0.55),
        hintFg: UIColor(hex: 0xB4B4B9),
        keyPressedBg: UIColor(hex: 0x3A3A3D), functionPressedBg: UIColor(hex: 0x5C5C60),
        cellBg: UIColor(hex: 0x2C2C2E), cellPressedBg: UIColor(hex: 0x3A3A3C), separator: UIColor(hex: 0x48484A), secondaryFg: UIColor(hex: 0x9C9CA2),
        accent: UIColor(hex: 0x4C8DF6), accentFg: .white,
        toastBg: UIColor(hex: 0xE8EAED), toastFg: UIColor(hex: 0x202124),
        isDark: true)

    /// `theme`: 0 hệ thống, 1 sáng, 2 tối; `accent`: chỉ số trong `KeyboardAccent.allCases`.
    static func resolve(theme: Int, accent: Int, systemDark: Bool) -> KeyboardTheme {
        let dark = theme == 2 || (theme == 0 && systemDark)
        return (dark ? Self.dark : Self.light).withAccent(KeyboardAccent.at(accent))
    }
}

/// Màu nhấn người dùng chọn, cùng thứ tự với Cài đặt: Xanh, Lá, Ngọc, Tím, Hồng, Không.
enum KeyboardAccent: CaseIterable, Equatable {
    case blue, green, teal, purple, pink, none
    static let names = ["Xanh", "Lá", "Ngọc", "Tím", "Hồng", "Không"]
    static func at(_ index: Int) -> KeyboardAccent { allCases[min(max(0, index), allCases.count - 1)] }
    func color(dark: Bool) -> UIColor {
        UIColor(hex: dark ? [0x4C8DF6, 0x34A853, 0x14A89A, 0x9C6ADE, 0xE0538A, 0x8E8E93][index]
                          : [0x1A73E8, 0x1E8E3E, 0x00897B, 0x7E57C2, 0xD81B60, 0x6C6C70][index])
    }
    /// Nền/chữ phím nguyên âm, cùng giá trị với `Accent.lightBg/lightFg/darkBg/darkFg` của Android. `none`: như phím chữ.
    func vowel(dark: Bool) -> (bg: UIColor, fg: UIColor) {
        let light: [(UInt32, UInt32)] = [(0xDCE8FB, 0x0A3A80), (0xD6F0DE, 0x0B5A2A), (0xD2F0ED, 0x0B5550), (0xE8DEF8, 0x4A2382), (0xFADDE9, 0x8A1C4A), (0xFFFFFF, 0x000000)]
        let night: [(UInt32, UInt32)] = [(0x1E3A64, 0xCFE0FB), (0x1B4A2E, 0xC8EBD3), (0x134A47, 0xC6ECE9), (0x3B2A5E, 0xE3D5FA), (0x5A2340, 0xFBD3E4), (0x5C5C60, 0xFFFFFF)]
        let pair = (dark ? night : light)[index]
        return (UIColor(hex: pair.0), UIColor(hex: pair.1))
    }
    private var index: Int { Self.allCases.firstIndex(of: self)! }
}

extension UIColor {
    convenience init(hex: UInt32, alpha: CGFloat = 1) {
        self.init(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
    }
}
