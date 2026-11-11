import UIKit
import CoreText

final class EmojiCatalog {
    struct Entry { let emoji: String, category: String, name: String, keywords: [String], searchable: String, modifierBase: Bool }
    let entries: [Entry]
    private let available: Set<String>
    private var drawable: [String: Bool] = [:]
    init(text: String) {
        entries = text.split(separator: "\n").compactMap { line in
            let fields = line.split(separator: "\t", omittingEmptySubsequences: false).map(String.init)
            guard fields.count >= 5 else { return nil }
            let words = fields[3].split(separator: "|").map(String.init)
            return Entry(emoji: fields[0], category: fields[1], name: fields[2], keywords: words,
                         searchable: Self.normalize(([fields[2]] + words).joined(separator: "|")), modifierBase: fields[4] == "1")
        }
        available = Set(entries.map(\.emoji))
    }
    static func normalize(_ text: String) -> String {
        text.folding(options: [.caseInsensitive, .diacriticInsensitive], locale: Locale(identifier: "vi_VN")).replacingOccurrences(of: "đ", with: "d").trimmingCharacters(in: .whitespacesAndNewlines)
    }
    func canDraw(_ emoji: String) -> Bool {
        if let known = drawable[emoji] { return known }
        let font = UIFont(name: "AppleColorEmoji", size: 24) ?? .systemFont(ofSize: 24)
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: emoji, attributes: [.font: font]))
        let runs = CTLineGetGlyphRuns(line) as! [CTRun]
        var glyphs: [CGGlyph] = []
        for run in runs {
            var buffer = [CGGlyph](repeating: 0, count: CTRunGetGlyphCount(run))
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &buffer)
            glyphs += buffer
        }
        let result = !glyphs.isEmpty && !glyphs.contains(0) && (!emoji.contains("‍") || glyphs.count == 1)
        drawable[emoji] = result
        return result
    }
    func search(_ query: String, category: String? = nil, limit: Int = 200) -> [Entry] {
        let key = Self.normalize(query)
        let candidates = entries.filter { (category == nil || $0.category == category) && (key.isEmpty || $0.searchable.contains(key)) }
        return candidates.sorted { left, right in
            let a = left.searchable.hasPrefix(key), b = right.searchable.hasPrefix(key)
            return a && !b
        }.lazy.filter { self.canDraw($0.emoji) }.prefix(limit).map { $0 }
    }
    func suggestions(_ word: String) -> [String] {
        let key = Self.normalize(word)
        guard !key.isEmpty else { return [] }
        return entries.lazy.filter { $0.keywords.contains { Self.normalize($0) == key } && self.canDraw($0.emoji) }.prefix(2).map(\.emoji)
    }
    func variant(_ emoji: String, gender: Int, tone: Int) -> String {
        let explicit = emoji.contains("♀") || emoji.contains("♂") || ["👩","👨","👧","👦","👵","👴"].contains { emoji.unicodeScalars.first == $0.unicodeScalars.first }
        var result = emoji
        if gender != 0 && !explicit {
            let female = gender == 1
            let candidate: String
            if emoji.unicodeScalars.first == "🧑".unicodeScalars.first { candidate = (female ? "👩" : "👨") + String(String.UnicodeScalarView(emoji.unicodeScalars.dropFirst())) }
            else if emoji == "🧒" { candidate = female ? "👧" : "👦" }
            else if emoji == "🧓" { candidate = female ? "👵" : "👴" }
            else if emoji == "🫅" { candidate = female ? "👸" : "🤴" }
            else {
                let direction = emoji.hasSuffix("‍➡️") ? "‍➡️" : ""
                candidate = (direction.isEmpty ? emoji : String(emoji.dropLast(direction.count))) + (female ? "‍♀️" : "‍♂️") + direction
            }
            if available.contains(candidate) && canDraw(candidate) { result = candidate }
        }
        guard (1...5).contains(tone), entries.contains(where: { $0.emoji == result && $0.modifierBase }),
              !result.unicodeScalars.contains(where: { (0x1F3FB...0x1F3FF).contains($0.value) }), let first = result.unicodeScalars.first else { return result }
        var rest = String(String.UnicodeScalarView(result.unicodeScalars.dropFirst()))
        if rest.hasPrefix("️") { rest = String(String.UnicodeScalarView(rest.unicodeScalars.dropFirst())) }
        let modified = String(first) + String(UnicodeScalar(0x1F3FA + tone)!) + rest
        return canDraw(modified) ? modified : result
    }
}
