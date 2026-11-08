package com.telehex.telex

import java.text.Normalizer
import kotlin.math.ln

/**
 * Sinh gợi ý sửa cho một chuỗi phím đã gõ, ưu tiên khôi phục dấu của từ đã biết, rồi thử:
 * - thay 1 phím bằng phím lân cận trên lưới lục giác (tối đa 6 ô);
 * - thiếu hoặc thừa 1 phím, đảo 2 phím liền nhau;
 * - lỗi Telex thường gặp (quên `w`, gõ dấu trước nguyên âm) — đã nằm trong hai nguồn trên.
 *
 * Chỉ giữ âm tiết hợp lệ. Không bao giờ tự thay chữ; caller chỉ hiện gợi ý.
 *
 * @param neighbors phím → các phím kề trên lưới.
 */
class Corrector(
    private val config: TelexConfig,
    private val neighbors: Map<Char, List<Char>>,
) {
    data class Suggestion(val text: String, val cost: Float, val rank: Int)

    fun suggest(
        rawKeys: String,
        caps: List<Boolean> = emptyList(),
        max: Int = 5,
        contextProbability: ((String) -> Double)? = null,
    ): List<String> {
        val keys = rawKeys.lowercase()
        if (keys.isEmpty() || keys.length > 10) return emptyList()
        val candidates = HashMap<String, Float>()
        fun offer(k: String, cost: Float) {
            if (k.isEmpty()) return
            val prev = candidates[k]
            if (prev == null || cost < prev) candidates[k] = cost
        }
        val n = keys.length
        // Thay bằng phím lân cận
        for (i in 0 until n) {
            for (nb in neighbors[keys[i]] ?: emptyList()) {
                if (nb !in 'a'..'z') continue
                offer(keys.substring(0, i) + nb + keys.substring(i + 1), 1f)
            }
        }
        // Thừa 1 phím
        for (i in 0 until n) offer(keys.removeRange(i, i + 1), 1.4f)
        // Thiếu 1 phím; quên phím biến đổi Telex (a/e/o/w/d) là lỗi phổ biến nên rẻ hơn
        for (i in 0..n) for (c in 'a'..'z') {
            val cost = if (c == 'a' || c == 'e' || c == 'o' || c == 'w' || c == 'd') 1.1f else 1.6f
            offer(keys.substring(0, i) + c + keys.substring(i), cost)
        }
        // Đảo 2 phím liền nhau
        for (i in 0 until n - 1) {
            offer(keys.substring(0, i) + keys[i + 1] + keys[i] + keys.substring(i + 2), 1.2f)
        }
        val original = render(keys, caps)
        val out = ArrayList<Suggestion>()
        // Khôi phục cả mũ/móc và thanh cùng lúc: "truoc" → "trước" cần hơn một phím Telex.
        // Không bỏ dấu người dùng đã gõ để đổi sang một từ khác có cùng chữ không dấu.
        val typed = TelexEngine(config.copy(englishFallback = false))
        for (i in keys.indices) typed.press(keys[i], caps.getOrElse(i) { false })
        val text = typed.text()
        for (word in restorationWords[plain(text)].orEmpty()) {
            if (typed.tone != Tone.NONE && word.none { ToneRules.toneOfChar(it) == typed.tone }) continue
            if (text.indices.any { i ->
                val base = ToneRules.stripTone(text[i].lowercaseChar())
                base !in 'a'..'z' && base != ToneRules.stripTone(word[i])
            }) continue
            val restored = ToneRules.spell(
                word.map { ToneRules.stripTone(it) }, word.maxOf { ToneRules.toneOfChar(it) },
                text.map { it.isUpperCase() }, config.newToneStyle,
            )
            if (restored != original) out.add(Suggestion(restored, 0.5f, CommonSyllables.rankOf(word)))
        }
        for ((k, cost) in candidates) {
            val text = renderValid(k, caps) ?: continue
            if (text == original) continue
            out.add(Suggestion(text, cost, CommonSyllables.rankOf(text)))
        }
        // Chấm toàn bộ ứng viên trước khi cắt top-k. Mỗi từ chỉ tra mô hình một lần.
        val unique = out.groupBy { it.text }.values.map { variants -> variants.minBy { score(it) } }
        val probabilities = if (contextProbability == null) emptyMap() else unique.associate { suggestion ->
            val p = contextProbability(suggestion.text.lowercase())
            suggestion.text to if (p.isFinite() && p > 0.0) p else 0.0
        }
        val nearestCost = unique.minOfOrNull { it.cost } ?: 0f
        val near = unique.filter { it.cost <= nearestCost + 0.8f }
        val best = near.maxOfOrNull { probabilities[it.text] ?: 0.0 } ?: 0.0
        val floor = maxOf(0.000001, best * 0.01)
        return unique
            .sortedBy { suggestion ->
                if (best == 0.0) return@sortedBy score(suggestion).toDouble()
                if (suggestion.cost > nearestCost + 0.8f) return@sortedBy score(suggestion) + 2.0
                // Tối đa 2 điểm: ngữ cảnh phân biệt các sửa gần nhau, không thắng lỗi gõ quá xa.
                val penalty = (-ln((probabilities.getValue(suggestion.text) + floor) / (best + floor)) * 0.45).coerceAtMost(2.0)
                // Có bằng chứng từ mô hình thì danh sách phổ biến nhỏ không được đè từ ngoài danh sách.
                suggestion.cost + rarity(suggestion).coerceAtMost(0.5f) + penalty
            }
            .map { it.text }
            .take(max)
    }

    /** Điểm thấp = tốt: khoảng cách bàn phím, cộng thêm nếu âm tiết hiếm. */
    private fun score(s: Suggestion): Float {
        return s.cost + rarity(s)
    }

    private fun rarity(s: Suggestion): Float = if (s.rank == Int.MAX_VALUE) 3f else s.rank / 300f

    private fun render(keys: String, caps: List<Boolean>): String {
        val e = TelexEngine(config)
        for (i in keys.indices) e.press(keys[i], caps.getOrElse(i) { false })
        return e.text()
    }

    /** Chuỗi phím có cho ra âm tiết tiếng Việt hợp lệ, đầy đủ không. */
    private fun renderValid(keys: String, caps: List<Boolean>): String? {
        val e = TelexEngine(config)
        for (i in keys.indices) e.press(keys[i], caps.getOrElse(i) { false })
        // Validator cho phép thiếu thanh khi đang gõ; gợi ý sửa phải là âm tiết hoàn chỉnh.
        if (e.text().endsWith("c", true) || e.text().endsWith("ch", true) ||
            e.text().endsWith("p", true) || e.text().endsWith("t", true)) {
            if (e.tone != Tone.ACUTE && e.tone != Tone.DOT) return null
        }
        return if (e.isValid()) e.text() else null
    }

    private companion object {
        fun plain(word: String): String = Normalizer.normalize(word.lowercase().replace('đ', 'd'), Normalizer.Form.NFD)
            .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() }

        val restorationWords: Map<String, List<String>> by lazy { CommonSyllables.RANK.keys.groupBy { plain(it) } }
    }
}
