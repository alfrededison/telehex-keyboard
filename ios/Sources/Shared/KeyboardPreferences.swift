import UIKit

/// Kiểu lưới của một profile, nhận ra từ số ô như `GridSpec` của Android: tổ ong 7 cột (35 ô), tổ ong có hàng số (45 ô),
/// truyền thống QWERTY 10 cột (38 ô) hoặc truyền thống có hàng số (48 ô). Ô đánh số theo thứ tự đọc, hàng trên trước. Phím cách nằm ở đâu
/// trong hàng cuối là do profile, để profile riêng lưu từ trước khi bố cục có sẵn dời phím cách vẫn hợp lệ.
enum GridSpec: Equatable, CaseIterable {
    case hex7, hex7Num, qwerty, qwertyNum
    static func of(count: Int) -> GridSpec? { allCases.first { $0.count == count } }
    /// Mép trái từng ô theo bề rộng ô, từng hàng.
    var lefts: [[CGFloat]] {
        func row(_ n: Int, _ left: CGFloat, _ width: CGFloat = 1) -> [CGFloat] { (0..<n).map { left + CGFloat($0) * width } }
        switch self {
        case .hex7: return [row(7, 0.5), row(7, 0), row(7, 0.5), row(7, 0), row(7, 0.5)]
        // Hàng số: 10 lục giác nhỏ rộng 0,75 ô, vừa đúng bề ngang 7,5 ô.
        case .hex7Num: return [row(10, 0, 0.75)] + GridSpec.hex7.lefts
        // Hàng Z: ⇧ sát mép trái, Z…M căn giữa (Z ngay dưới S), ⌫ sát mép phải.
        case .qwerty: return [row(10, 0), row(9, 0.5), [0] + row(7, 1.5) + [9], row(10, 0)]
        case .qwertyNum: return [row(10, 0)] + GridSpec.qwerty.lefts
        }
    }
    var rowSizes: [Int] { lefts.map(\.count) }
    /// Bề rộng ô từng hàng theo bề rộng ô thường (hàng số tổ ong 0,75).
    var widths: [CGFloat] { self == .hex7Num ? [0.75] + Array(repeating: 1, count: 5) : Array(repeating: 1, count: rowSizes.count) }
    /// Tổ ong: mép trên từng hàng theo bề rộng ô thường. Hàng cùng cỡ gối 1/4 chiều cao lục giác, hàng số nhỏ xếp chồng
    /// lên hàng chữ không gối, như `HexGeometry` của Android.
    var hexRowTops: [CGFloat] {
        var tops: [CGFloat] = [0]
        for row in 1..<widths.count {
            let previous = widths[row - 1] * 2 / HexShape.sqrt3
            tops.append(tops[row - 1] + (widths[row] == widths[row - 1] ? 0.75 * previous : previous))
        }
        return tops
    }
    /// Tổ ong: chiều cao lưới theo bề rộng ô thường (lưới 7 cột: 8/√3).
    var hexHeight: CGFloat { hexRowTops[hexRowTops.count - 1] + widths[widths.count - 1] * 2 / HexShape.sqrt3 }
    var count: Int { rowSizes.reduce(0, +) }
    /// Bề rộng lưới theo ô.
    var span: CGFloat { isHex ? 7.5 : 10 }
    var isHex: Bool { self == .hex7 || self == .hex7Num }
    /// Tên kiểu lưới ở danh sách profile.
    var title: String { switch self { case .hex7: "Tổ ong"; case .hex7Num: "Tổ ong có số"; case .qwerty: "QWERTY"; case .qwertyNum: "QWERTY có số" } }
    var spaceCells: Int { isHex ? 3 : 4 }
    /// Các ô của hàng cuối, nơi phím cách nằm (ô đầu giữ " ", các ô sau của phím cách để trống).
    var lastRow: Range<Int> { (count - rowSizes[rowSizes.count - 1])..<count }
    func position(_ index: Int) -> (row: Int, column: Int) {
        var rest = index
        for (row, size) in rowSizes.enumerated() { if rest < size { return (row, rest) }; rest -= size }
        return (rowSizes.count - 1, 0)
    }
    func left(_ index: Int) -> CGFloat { let (row, column) = position(index); return lefts[row][column] }
    func width(_ index: Int) -> CGFloat { widths[position(index).row] }
}

struct KeyboardProfile: Codable, Equatable, Identifiable {
    var id: String
    var name: String
    var letters: [String]
    var swipe: [String]
    var symbols: [[String]]
    var builtIn: Bool { id.hasPrefix("builtin.") }
    var spec: GridSpec { GridSpec.of(count: letters.count) ?? .hex7 }

    /// Mặc định từ 0.7.1: hai hàng trên đọc zigzag ⚙ Q W E R T G Y H U I O P 123, hàng giữa A S D F … J K L.
    static let defaultLetters = ["q","e","t","y","u","o","#symbols", "#settings","w","r","g","h","i","p", "a","s","d","f","j","k","l", "z","x","c","v","b","n","m", "#emoji",","," ","","",".","\n"]
    /// Tổ ong có hàng số: hàng 1…0 (10 lục giác nhỏ) trên `defaultLetters`.
    static let hexNumLetters: [String] = "1234567890".map(String.init) + defaultLetters
    /// Truyền thống (0.8.0): đúng thứ tự QWERTY, ⇧ và ⌫ ở hàng Z. Hàng cuối (0.8.4): ba phím 123 ☺ ⚙ bên trái phím cách
    /// 4 ô, ba phím `,` `.` ↵ bên phải (đến 0.8.3: 123 ☺ ⚙ `,` bên trái).
    static let qwertyLetters: [String] = ["q","w","e","r","t","y","u","i","o","p", "a","s","d","f","g","h","j","k","l",
        "#shift","z","x","c","v","b","n","m","#backspace", "#symbols","#emoji","#settings"," ","","","",",",".","\n"]
    /// Truyền thống có hàng số: hàng 1…0 trên `qwertyLetters`.
    static let qwertyNumLetters: [String] = "1234567890".map(String.init) + qwertyLetters
    /// Ký tự vuốt xuống theo phím: số 1…0 dưới Q…P, emoji dưới hàng giữa, ký hiệu dưới hàng Z.
    static let swipeByCode = ["q":"1","w":"2","e":"3","r":"4","t":"5","y":"6","u":"7","i":"8","o":"9","p":"0",
                              "a":"😄","s":"😂","d":"😍","f":"👍","g":"🙏","h":"❤️","j":"😭","k":"🔥","l":"🎉",
                              "z":"@","x":"#","c":"&","v":"-","b":"/","n":"(","m":")", ",":"?",".":"!"]
    /// Bố cục có hàng số, như Android: hàng 1…0 cho ký tự Shift+số, Q…P cho ngoặc và ký hiệu, Z…M cho dấu câu (các ký
    /// tự cũ của Q…P và Z…M đã có ở hàng số).
    static let qwertyNumSwipeByCode = swipeByCode.merging([
        "1":"!","2":"@","3":"#","4":"$","5":"%","6":"^","7":"&","8":"*","9":"(","0":")",
        "q":"+","w":"=","e":"~","r":"|","t":"[","y":"]","u":"<","i":">","o":"{","p":"}",
        "z":"-","x":"_","c":"/","v":":","b":";","n":"'","m":"\"",
    ]) { $1 }
    /// Hai trang 123 gắn theo ô, như `SymbolPages` của Android: phím 1/2 ở ô ⚙ (tổ ong) hoặc ⇧ (truyền thống); trang 2
    /// có `<` `>` ở ô `,` `.`.
    static let hexSymbols: [[String]] = [
        ["1","3","5","7","9","0","", "#page","2","4","6","8","(",")", "@","#","$","_","&","-","+", "*","\"","'",":","/","!","?", "","","","","","",""],
        ["~","|","√","÷","§","\\","", "#page","`","•","π","×","{","}", "£","₫","€","¥","^","°","=", "%","©","®",";","✓","[","]", "","<","","","",">",""],
    ]
    /// Tổ ong có hàng số: cùng bộ ký tự với `qwertyNumSymbols`, 26 ô chữ và ô ⚙ (phím 1/2) theo thứ tự đọc, như Android.
    static let hexNumSymbols: [[String]] = [
        ["1","2","3","4","5","6","7","8","9","0", "@","#","$","_","&","-","", "#page","+","(",")","/","=","%",
         "*","\"","'",":",";","!","?", "\\","<",">","[","]","{","}", "","","","","","",""],
        ["~","`","|","•","√","π","÷","×","§","∆", "£","₫","€","¥","¢","^","", "#page","°","©","®","™","✓","…",
         "«","»","±","≤","≥","≠","∞", "¡","¿","²","³","½","‰","¶", "","","","","","",""],
    ]
    static let qwertySymbols: [[String]] = [
        ["1","2","3","4","5","6","7","8","9","0", "@","#","$","_","&","-","+","(",")",
         "#page","*","\"","'",":",";","!","?","", "","","/","","","","","","",""],
        ["~","`","|","•","√","π","÷","×","§","∆", "£","₫","€","¥","^","°","=","{","}",
         "#page","%","©","®","™","✓","[","]","", "","","\\","","","","","<",">",""],
    ]
    /// Có hàng số: đủ chỗ cho `/` `=` `%` và ngoặc ở trang 1 nên ⚙ giữ nguyên, như Android.
    static let qwertyNumSymbols: [[String]] = [
        ["1","2","3","4","5","6","7","8","9","0", "@","#","$","_","&","-","+","(",")","/", "*","\"","'",":",";","!","?","=","%",
         "#page","\\","<",">","[","]","{","}","", "","","","","","","","","",""],
        ["~","`","|","•","√","π","÷","×","§","∆", "£","₫","€","¥","¢","^","°","©","®","™", "✓","…","«","»","±","≤","≥","≠","∞",
         "#page","¡","¿","²","³","½","‰","¶","", "","","","","","","","","",""],
    ]
    static let presets: [KeyboardProfile] = [
        ("default","Mặc định",defaultLetters,hexSymbols,swipeByCode), ("qwerty","Truyền thống",qwertyLetters,qwertySymbols,swipeByCode),
        ("qwertyNum","Có hàng số",qwertyNumLetters,qwertyNumSymbols,qwertyNumSwipeByCode),
        // Hàng số tổ ong không có ký tự vuốt xuống: lục giác nhỏ không đủ chỗ, `! @ # …` có ở lớp 123. Như Android.
        ("hexNum","Tổ ong có hàng số",hexNumLetters,hexNumSymbols,qwertyNumSwipeByCode.filter { !KeyboardProfile.isDigit($0.key) }),
    ].map { preset in
        KeyboardProfile(id: "builtin." + preset.0, name: preset.1, letters: preset.2, swipe: preset.2.map { preset.4[$0] ?? "" }, symbols: preset.3)
    }
    /// Profile có sẵn cùng kiểu lưới: điểm xuất phát khi đặt lại một lớp.
    static func builtIn(for spec: GridSpec) -> KeyboardProfile { presets.first { $0.spec == spec } ?? presets[0] }
    static func isLetter(_ value: String) -> Bool { value.utf8.count == 1 && value.first.map { ("a"..."z").contains(String($0)) } == true }
    static func isDigit(_ value: String) -> Bool { value.utf8.count == 1 && value.first.map { ("0"..."9").contains(String($0)) } == true }
    /// Ô chữ hay ô số (hàng số): lớp 123 luôn hiện giá trị của trang ở đây; đổi chỗ được như chữ.
    static func isCharCell(_ value: String) -> Bool { isLetter(value) || isDigit(value) }
    /// Ô giữ được ký tự vuốt xuống: chữ, số và `,` `.`.
    static func swipeUsable(_ code: String) -> Bool { isCharCell(code) || code == "," || code == "." }
    /// Ô giữ được giá trị lớp 123: chữ, số, cùng ⚙ ⇧ `,` `.` (có giá trị thì thay phím của lớp chữ, trống thì giữ phím đó).
    static func symbolUsable(_ code: String) -> Bool { isCharCell(code) || ["#settings","#shift",",","."].contains(code) }
    /// Ô không đổi chỗ được: phím cách và ↵.
    static func fixed(_ code: String) -> Bool { code == " " || code.isEmpty || code == "\n" }
    func valid() -> Bool {
        guard let spec = GridSpec.of(count: letters.count), swipe.count == letters.count, symbols.count == 2, symbols.allSatisfy({ $0.count == letters.count }),
              !id.isEmpty, id.count <= 80, name.count <= 80, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let space = letters.firstIndex(of: " "), spec.lastRow.contains(space), spec.lastRow.contains(space + spec.spaceCells - 1),
              (1..<spec.spaceCells).allSatisfy({ letters[space + $0].isEmpty }), letters.last == "\n",
              Set(letters) == Set(Self.builtIn(for: spec).letters), letters.filter({ Self.isLetter($0) }).count == 26,
              letters.filter({ $0.isEmpty }).count == spec.spaceCells - 1 else { return false }
        return swipe.allSatisfy { $0.utf16.count <= 8 } && symbols.flatMap { $0 }.allSatisfy { $0.utf16.count <= 8 }
    }
    /// Giá trị vuốt xuống / 123 nằm dưới ô không dùng được (sau khi kéo phím chức năng) dời sang ô trống, như
    /// `KeyLayers.reconcile` của Android: lớp 123 ưu tiên ô chữ rồi mới tới ⚙ ⇧ `,` `.`.
    mutating func reconcile() {
        func place(_ layers: inout [[String]], usable: (String) -> Bool, preferred: (String) -> Bool) {
            var hidden: [(Int, String)] = []
            for page in layers.indices { for i in layers[page].indices where !usable(letters[i]) && !layers[page][i].isEmpty { hidden.append((page, layers[page][i])); layers[page][i] = "" } }
            for (origin, value) in hidden {
                let pages = [origin] + layers.indices.filter { $0 != origin }
                search: for pass in 0..<2 {
                    for page in pages { for i in layers[page].indices where (pass == 0 ? preferred(letters[i]) : usable(letters[i])) && layers[page][i].isEmpty { layers[page][i] = value; break search } }
                }
            }
        }
        var swipeLayers = [swipe]
        place(&swipeLayers, usable: Self.swipeUsable, preferred: Self.swipeUsable); swipe = swipeLayers[0]
        place(&symbols, usable: Self.symbolUsable, preferred: Self.isCharCell)
    }
}

struct KeyboardConfiguration: Codable, Equatable {
    var newToneStyle = false, lAsGrave = false, suggestions = true, incognito = false, glide = true
    var activeProfile = "builtin.default"
    var profiles: [KeyboardProfile] = []
    var snippets: [String: String] = [:]
    var theme = 0, accent = 0, oneHand = 0, emojiGender = 0, emojiTone = 0
    var swipeThreshold = 24.0, holdDelay = 0.8, cursorDelay = 0.3
    /// Giữ kéo để đổi chỗ phím ngay trên bàn phím: mặc định bật, chỉ chạy khi có Truy cập đầy đủ (để profile tạo ra về được app).
    var touchAdaptation = false, autoCapitalization = true, rearrange = true
    var haptics = false, sound = false
    var heightScale = 1.0
    var offsets: [String: [Double]] = [:]
    /// Chuỗi phím không thể là tiếng Việt thì giữ nguyên phím đã gõ (windows không thành ưindows), như Android.
    var englishFallback = true
    /// Lưu lịch sử clipboard trên bàn phím: mặc định bật, chỉ chạy khi có Truy cập đầy đủ. Bàn phím chỉ đọc clipboard khi mở
    /// bảng clipboard, nên bật sẵn không làm iOS hỏi quyền dán mỗi lần hiện bàn phím.
    var clipboardHistory = true
    /// Vùng chạm động: chạm lưng chừng giữa hai phím chữ thì chọn phím giữ âm tiết hợp lệ, như Android (mặc định tắt).
    var dynamicHitZones = false
    /// Mỗi bước (pt) khi kéo phím cách để xoá từ hay di con trỏ, như Android (25–100, mặc định 50).
    var stepSize = 50.0
    /// Profile đang dùng không còn (profile có sẵn đã bỏ ở 0.7.1, hoặc dữ liệu cũ) thì về Mặc định thay vì bỏ cả cấu hình.
    mutating func normalize() {
        if !(KeyboardProfile.presets + profiles).contains(where: { $0.id == activeProfile }) { activeProfile = KeyboardProfile.presets[0].id }
    }
    func valid() -> Bool {
        profiles.count <= 50 && profiles.allSatisfy({ $0.valid() && !$0.builtIn }) && Set(profiles.map(\.id)).count == profiles.count &&
        Set((KeyboardProfile.presets + profiles).map { $0.name.lowercased() }).count == KeyboardProfile.presets.count + profiles.count &&
        (KeyboardProfile.presets + profiles).contains { $0.id == activeProfile } &&
        (0...2).contains(theme) && (0...5).contains(accent) && (0...2).contains(oneHand) &&
        (0...2).contains(emojiGender) && (0...5).contains(emojiTone) &&
        heightScale.isFinite && (0.8...1.4).contains(heightScale) && swipeThreshold.isFinite && (12...80).contains(swipeThreshold) && holdDelay.isFinite && (0.4...2).contains(holdDelay) &&
        cursorDelay.isFinite && (0.15...1).contains(cursorDelay) && stepSize.isFinite && (25...100).contains(stepSize) && snippets.count <= 1000 &&
        snippets.allSatisfy { !$0.key.isEmpty && $0.key.count <= 32 && $0.value.count <= 2000 } &&
        offsets.count <= 200 && offsets.allSatisfy { $0.key.count <= 100 && $0.value.count == 2 && $0.value.allSatisfy { $0.isFinite && abs($0) <= 0.3 } }
    }
}

extension KeyboardConfiguration {
    private enum CodingKeys: String, CodingKey {
        case newToneStyle, lAsGrave, suggestions, incognito, glide, activeProfile, profiles, snippets, theme, accent, oneHand
        case emojiGender, emojiTone, swipeThreshold, holdDelay, cursorDelay, touchAdaptation, autoCapitalization, rearrange
        case haptics, sound, heightScale, offsets, englishFallback, clipboardHistory, dynamicHitZones, stepSize
    }
    /// Trường thiếu (cấu hình hay bản sao lưu từ bản cũ hơn) lấy giá trị mặc định thay vì làm hỏng cả cấu hình.
    init(from decoder: Decoder) throws {
        self.init()
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func read<T: Decodable>(_ key: CodingKeys, _ value: inout T) throws { if let v = try c.decodeIfPresent(T.self, forKey: key) { value = v } }
        try read(.newToneStyle, &newToneStyle); try read(.lAsGrave, &lAsGrave); try read(.suggestions, &suggestions)
        try read(.incognito, &incognito); try read(.glide, &glide); try read(.activeProfile, &activeProfile)
        try read(.profiles, &profiles); try read(.snippets, &snippets); try read(.theme, &theme); try read(.accent, &accent)
        try read(.oneHand, &oneHand); try read(.emojiGender, &emojiGender); try read(.emojiTone, &emojiTone)
        try read(.swipeThreshold, &swipeThreshold); try read(.holdDelay, &holdDelay); try read(.cursorDelay, &cursorDelay)
        try read(.touchAdaptation, &touchAdaptation); try read(.autoCapitalization, &autoCapitalization); try read(.rearrange, &rearrange)
        try read(.haptics, &haptics); try read(.sound, &sound); try read(.heightScale, &heightScale); try read(.offsets, &offsets)
        try read(.englishFallback, &englishFallback); try read(.clipboardHistory, &clipboardHistory)
        try read(.dynamicHitZones, &dynamicHitZones); try read(.stepSize, &stepSize)
    }
}

final class KeyboardPreferences {
    private let defaults: UserDefaults
    var configuration: KeyboardConfiguration {
        didSet {
            if let data = try? JSONEncoder().encode(configuration) { defaults.set(data, forKey: "telehex.configuration.v1") }
            if oldValue != configuration { onChange?(oldValue, configuration) }
        }
    }
    /// Cấu hình vừa đổi (cũ, mới). App dùng để gửi sang bàn phím; bàn phím dùng để đánh dấu phần tự sửa.
    var onChange: ((KeyboardConfiguration, KeyboardConfiguration) -> Void)?
    private var applyingShared = false
    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        var stored = defaults.data(forKey: "telehex.configuration.v1").flatMap { try? JSONDecoder().decode(KeyboardConfiguration.self, from: $0) }
        stored?.normalize()
        if let stored, stored.valid() { configuration = stored }
        else {
            configuration = KeyboardConfiguration()
            configuration.newToneStyle = defaults.bool(forKey: "newToneStyle")
            configuration.lAsGrave = defaults.bool(forKey: "lAsGrave")
            configuration.suggestions = defaults.object(forKey: "suggestions") as? Bool ?? true
        }
    }
    var newToneStyle: Bool { get { configuration.newToneStyle } set { configuration.newToneStyle = newValue } }
    var lAsGrave: Bool { get { configuration.lAsGrave } set { configuration.lAsGrave = newValue } }
    var suggestions: Bool { get { configuration.suggestions } set { configuration.suggestions = newValue } }
    var allProfiles: [KeyboardProfile] { KeyboardProfile.presets + configuration.profiles }
    var profile: KeyboardProfile { allProfiles.first { $0.id == configuration.activeProfile } ?? KeyboardProfile.presets[0] }
    @discardableResult func saveProfile(_ edited: KeyboardProfile) -> Bool {
        guard edited.valid() else { return false }
        var copy = edited
        if copy.builtIn {
            let originalID = copy.id
            copy.id = UUID().uuidString; copy.name = uniqueName("Của tôi")
            for (key,value) in configuration.offsets where key.hasPrefix(originalID + ":") {
                if configuration.offsets.count < 200 { configuration.offsets[copy.id + key.dropFirst(originalID.count)] = value }
            }
        }
        guard !allProfiles.contains(where: { $0.id != copy.id && $0.name.caseInsensitiveCompare(copy.name) == .orderedSame }) else { return false }
        if let index = configuration.profiles.firstIndex(where: { $0.id == copy.id }) { configuration.profiles[index] = copy }
        else { guard configuration.profiles.count < 50 else { return false }; configuration.profiles.append(copy) }
        configuration.activeProfile = copy.id
        return true
    }
    /// Bàn phím: nhớ lúc tự sửa từng phần (đổi profile, ẩn danh, kéo đổi chỗ phím) để phần mới hơn thắng khi app gửi cài đặt.
    func trackLocalEdits(now: @escaping () -> Date = Date.init) {
        onChange = { [weak self] old, new in
            guard let self, !self.applyingShared else { return }
            for part in SharedSettings.parts(changedFrom: old, to: new) {
                self.defaults.set(now().timeIntervalSince1970, forKey: "telehex.local.stamp.\(part.rawValue)")
            }
            // Profile tạo ngay trên bàn phím (kéo đổi chỗ phím trên profile có sẵn): app không biết, cần giữ khi app gửi profile.
            let created = Set(new.profiles.map(\.id)).subtracting(old.profiles.map(\.id))
            if !created.isEmpty { self.keyboardCreatedProfiles.formUnion(created) }
        }
    }
    /// Id các profile bàn phím tự tạo. Profile không có trong danh sách app gửi chỉ được giữ nếu nằm ở đây, nên profile đã xoá
    /// trong app không sống lại trên bàn phím.
    private var keyboardCreatedProfiles: Set<String> {
        get { Set(defaults.stringArray(forKey: "telehex.local.createdProfiles") ?? []) }
        set { defaults.set(Array(newValue.prefix(100)), forKey: "telehex.local.createdProfiles") }
    }
    struct SharedUpdate: Equatable { var changed = false, clearPersonal = false, reloadPacks = false }
    /// Bàn phím: áp cài đặt app gửi qua App Group. Mỗi phần chỉ áp một lần cho mỗi lần app sửa, và chỉ khi app sửa sau lần
    /// bàn phím tự sửa phần đó. Độ lệch chạm luôn của bàn phím; profile bàn phím tự tạo được giữ lại. Mốc chỉ được ghi nhận khi
    /// cấu hình sau khi trộn hợp lệ, để lần mở sau thử lại; lệnh (xoá dữ liệu học, độ lệch, nạp lại gói) chạy độc lập.
    func applyShared(from shared: UserDefaults?) -> SharedUpdate {
        guard let shared else { return SharedUpdate() }
        var update = SharedUpdate()
        var next = configuration
        var consumed: [(String, Double)] = []
        func fresh(_ key: String) -> Double? {
            let stamp = shared.double(forKey: key)
            guard stamp > 0, stamp != defaults.double(forKey: "telehex.applied." + key) else { return nil }
            consumed.append((key, stamp))
            return stamp
        }
        if let app = SharedSettings.configuration(in: shared) {
            for part in SharedSettings.Part.allCases {
                guard let stamp = fresh(SharedSettings.stampKey(part)),
                      stamp > defaults.double(forKey: "telehex.local.stamp.\(part.rawValue)") else { continue }
                switch part {
                case .general:
                    var merged = app
                    merged.profiles = next.profiles; merged.activeProfile = next.activeProfile; merged.incognito = next.incognito; merged.offsets = next.offsets
                    next = merged
                case .profiles:
                    let ids = Set(app.profiles.map(\.id)), created = keyboardCreatedProfiles
                    var names = Set((KeyboardProfile.presets + app.profiles).map { $0.name.lowercased() })
                    // Profile bàn phím tự tạo trùng tên profile của app (cùng là "Của tôi") thì đổi tên chứ không bỏ.
                    let keep = next.profiles.filter { created.contains($0.id) && !ids.contains($0.id) }.map { profile -> KeyboardProfile in
                        var copy = profile
                        if names.contains(copy.name.lowercased()) {
                            copy.name = (2...1000).lazy.map { "\(profile.name) \($0)" }.first { !names.contains($0.lowercased()) } ?? UUID().uuidString
                        }
                        names.insert(copy.name.lowercased())
                        return copy
                    }
                    next.profiles = Array((app.profiles + keep).prefix(50)); next.activeProfile = app.activeProfile
                case .incognito: next.incognito = app.incognito
                }
            }
        }
        next.normalize()
        if next.valid() {
            for (key, stamp) in consumed { defaults.set(stamp, forKey: "telehex.applied." + key) }
        } else { next = configuration }
        func command(_ name: SharedSettings.Command) -> Bool {
            let key = SharedSettings.commandKey(name), stamp = shared.double(forKey: key)
            guard stamp > 0, stamp != defaults.double(forKey: "telehex.applied." + key) else { return false }
            defaults.set(stamp, forKey: "telehex.applied." + key)
            return true
        }
        if command(.resetOffsets) { next.offsets = [:] }
        update.clearPersonal = command(.clearPersonal)
        update.reloadPacks = command(.reloadPacks)
        if next != configuration {
            applyingShared = true; configuration = next; applyingShared = false
            update.changed = true
        }
        return update
    }
    func uniqueName(_ base: String) -> String {
        let names = Set(allProfiles.map { $0.name.lowercased() })
        if !names.contains(base.lowercased()) { return base }
        return (2...1000).map { "\(base) \($0)" }.first { !names.contains($0.lowercased()) } ?? UUID().uuidString
    }
}
