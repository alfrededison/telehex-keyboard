package com.telehex.telex

/**
 * Engine Telex cho **một âm tiết đang soạn**. Kotlin thuần, không phụ thuộc Android.
 *
 * Trạng thái: [chars] (đã biến đổi, chữ thường), [caps], [tone]. Ngoài ra giữ [rawKeys]
 * (đúng các phím đã gõ) để quay về chữ tiếng Anh khi chuỗi phím không thể là âm tiết tiếng Việt.
 *
 * Sau khi người dùng xoá bớt ký tự của âm tiết hợp lệ, [rawKeys] không còn khớp với [chars]
 * ([rawDirty]); khi đó không quay về phím gốc nữa mà hiện đúng [chars] (như Gboard).
 *
 * Gõ lại cùng phím để huỷ biến đổi (`ss` → s, `aaa` → aa, `ddd` → dd) là chủ ý của người gõ,
 * nên từ đó âm tiết ở chế độ "nguyên văn" ([literal]): hiện đúng [chars] dù không phải tiếng Việt
 * ("ass" → as). Chuỗi không hợp lệ mà không có thao tác huỷ ("pornt", "windows") mới quay về phím gốc.
 */
class TelexEngine(config: TelexConfig = TelexConfig()) {
    var config: TelexConfig = config
        set(value) { field = value; invalidateCache() }

    private val chars = ArrayList<Char>(8)
    private val caps = ArrayList<Boolean>(8)
    private val rawKeys = ArrayList<Char>(8)
    private val rawCaps = ArrayList<Boolean>(8)

    /** Dấu thanh hiện tại (0..5). */
    var tone: Int = Tone.NONE
        private set

    private var rawDirty = false
    private var literal = false
    /** Vị trí các 'ư' sinh từ phím w đứng một mình (không có u), để gõ w lần nữa trả về 'w' chứ không phải "uw". */
    private val loneW = HashSet<Int>()
    private var cachedText: String? = null
    private var cachedFallback: Boolean? = null

    val isEmpty: Boolean get() = chars.isEmpty() && rawKeys.isEmpty()
    val length: Int get() = chars.size

    /** Âm tiết có nguyên âm chưa (điều kiện để phím dấu có hiệu lực). */
    fun hasVowel(): Boolean = ToneRules.vowelIndices(chars).isNotEmpty()

    /** Âm tiết đang hiển thị dạng "phím gốc" vì không thể là tiếng Việt. */
    fun isFallback(): Boolean {
        cachedFallback?.let { return it }
        val fb = config.englishFallback && !rawDirty && !literal && !SyllableValidator.couldBecomeValid(baseString(), tone)
        cachedFallback = fb
        return fb
    }

    /** Âm tiết hiện tại có hợp lệ (đầy đủ) không. */
    fun isValid(): Boolean = !isFallback() && SyllableValidator.isValid(baseString(), tone)

    /** Chuỗi đang soạn để hiển thị / commit. */
    fun text(): String {
        cachedText?.let { return it }
        val t = if (isFallback()) rawText() else ToneRules.spell(chars, tone, caps, config.newToneStyle)
        cachedText = t
        return t
    }

    /** Chữ hoa theo từng phím đã gõ. */
    fun rawCaps(): List<Boolean> = rawCaps.toList()

    /**
     * Âm tiết đáng ngờ (không thể là tiếng Việt): gạch chân và đưa gợi ý sửa.
     * Khác [isFallback] ở chỗ vẫn đúng khi tắt tuỳ chọn khôi phục tiếng Anh.
     */
    fun isSuspicious(): Boolean = !rawDirty && !literal && !SyllableValidator.couldBecomeValid(baseString(), tone)

    /** Bản sao độc lập của trạng thái hiện tại (để thử "nếu gõ thêm phím X"). */
    fun copy(): TelexEngine {
        val e = TelexEngine(config)
        e.chars.addAll(chars); e.caps.addAll(caps)
        e.rawKeys.addAll(rawKeys); e.rawCaps.addAll(rawCaps)
        e.tone = tone; e.rawDirty = rawDirty; e.literal = literal; e.loneW.addAll(loneW)
        return e
    }

    /** Đúng các phím đã gõ (kèm chữ hoa). */
    fun rawText(): String {
        val sb = StringBuilder(rawKeys.size)
        for (i in rawKeys.indices) sb.append(if (rawCaps[i]) rawKeys[i].uppercaseChar() else rawKeys[i])
        return sb.toString()
    }

    /** 6 biến thể dấu cho thanh gợi ý; rỗng nếu chưa có nguyên âm hoặc đang ở chế độ phím gốc. */
    fun candidates(): List<Candidate> {
        if (isFallback() || !hasVowel()) return emptyList()
        return Tone.CANDIDATE_ORDER.map { t ->
            Candidate(t, ToneRules.spell(chars, t, caps, config.newToneStyle), t == tone)
        }
    }

    fun setTone(t: Int) {
        require(t in 0..5)
        tone = t
        invalidateCache()
    }

    fun reset() {
        chars.clear(); caps.clear(); rawKeys.clear(); rawCaps.clear()
        tone = Tone.NONE
        rawDirty = false
        literal = false
        loneW.clear()
        invalidateCache()
    }

    /**
     * Gõ một phím chữ `a`..`z`.
     * @param upper chữ hoa (vuốt lên). Giữ hoa khi engine biến đổi: `D` + `d` → `Đ`.
     */
    fun press(key: Char, upper: Boolean = false) {
        val k = key.lowercaseChar()
        require(k in 'a'..'z') { "press() chỉ nhận a..z, nhận: '$key'" }
        rawKeys.add(k); rawCaps.add(upper)
        apply(k, upper)
        invalidateCache()
    }

    /**
     * Xoá một ký tự của âm tiết đang soạn.
     * @return `false` nếu không còn gì để xoá trong âm tiết (caller xoá trong ô nhập).
     */
    fun backspace(): Boolean {
        if (isEmpty) return false
        if (isFallback()) {
            // Ở chế độ phím gốc, phím gốc là nguồn sự thật: bỏ phím cuối rồi gõ lại từ đầu.
            rawKeys.removeAt(rawKeys.size - 1); rawCaps.removeAt(rawCaps.size - 1)
            replay()
        } else {
            // Xoá theo ký tự đã biến đổi, giữ dấu nếu còn nguyên âm ("tiếng" → "tiến").
            if (chars.isNotEmpty()) { chars.removeAt(chars.size - 1); caps.removeAt(caps.size - 1); loneW.remove(chars.size) }
            if (!hasVowel()) tone = Tone.NONE
            rawDirty = true
        }
        if (chars.isEmpty()) {
            rawKeys.clear(); rawCaps.clear(); tone = Tone.NONE; rawDirty = false; literal = false
        }
        invalidateCache()
        return true
    }

    private fun replay() {
        val keys = rawKeys.toList(); val ups = rawCaps.toList()
        chars.clear(); caps.clear(); tone = Tone.NONE; literal = false; loneW.clear()
        for (i in keys.indices) apply(keys[i], ups[i])
    }

    /** Người gõ vừa huỷ một biến đổi bằng cách gõ lại cùng phím: từ đây hiện nguyên văn. */
    private fun cancelled() { literal = true }

    private fun add(ch: Char, upper: Boolean) { chars.add(ch); caps.add(upper) }

    private fun apply(k: Char, upper: Boolean) {
        val hasV = hasVowel()
        val tv = config.toneOf(k)
        when {
            tv in 1..5 && hasV -> {
                if (tone == tv) { tone = Tone.NONE; add(k, upper); cancelled() } else tone = tv
            }
            tv == Tone.REMOVE && hasV && tone > 0 -> tone = Tone.NONE
            k == 'a' || k == 'e' || k == 'o' -> applyCircumflex(k, upper)
            k == 'd' -> applyD(upper)
            k == 'w' -> applyW(upper)
            else -> {
                // "thuơ" + phụ âm cuối → "thươ" (thuowng → thương), vì "uơ" chỉ đứng cuối (thuở, huơ, khuơ).
                val n = chars.size
                if (n >= 2 && chars[n - 1] == 'ơ' && chars[n - 2] == 'u' && isSoftUoOnset(n - 2)) chars[n - 2] = 'ư'
                add(k, upper)
            }
        }
    }

    /** Phụ âm đầu h / th / kh trước vị trí [uoIndex]: "uo" + w cho "uơ" thay vì "ươ". */
    private fun isSoftUoOnset(uoIndex: Int): Boolean {
        val onset = chars.subList(0, uoIndex).joinToString("")
        return onset == "h" || onset == "th" || onset == "kh"
    }

    private fun applyCircumflex(k: Char, upper: Boolean) {
        val c = when (k) { 'a' -> 'â'; 'e' -> 'ê'; else -> 'ô' }
        var j = -1
        for (i in chars.indices.reversed()) if (chars[i] == k || chars[i] == c) { j = i; break }
        when {
            j >= 0 && chars[j] == k -> chars[j] = c
            j >= 0 && chars[j] == c -> { chars[j] = k; add(k, upper); cancelled() }
            else -> add(k, upper)
        }
    }

    private fun applyD(upper: Boolean) {
        when {
            chars.isNotEmpty() && chars[0] == 'd' -> chars[0] = 'đ'
            chars.isNotEmpty() && chars[0] == 'đ' -> { chars[0] = 'd'; add('d', upper); cancelled() }
            else -> add('d', upper)
        }
    }

    private fun applyW(upper: Boolean) {
        val p = lastIndexOfUo()
        if (p >= 0 && !(p > 0 && chars[p - 1] == 'q')) {
            // "uo" ở cuối sau h/th/kh → "uơ" (thuở, huơ); có phụ âm cuối thì thành "ươ" khi gõ tiếp.
            if (p + 2 == chars.size && isSoftUoOnset(p)) chars[p + 1] = 'ơ' else { chars[p] = 'ư'; chars[p + 1] = 'ơ' }
            return
        }
        for (i in chars.indices.reversed()) {
            val ch = chars[i]
            val m = when (ch) { 'a' -> 'ă'; 'o' -> 'ơ'; 'u' -> 'ư'; else -> null }
            if (m != null && !(ch == 'u' && i > 0 && chars[i - 1] == 'q')) { chars[i] = m; return }
            val r = when (ch) { 'ă' -> 'a'; 'ơ' -> 'o'; 'ư' -> 'u'; else -> null }
            if (r != null) {
                if (ch == 'ư' && i in loneW) { chars[i] = 'w'; loneW.remove(i) } // "ww" → w
                else { chars[i] = r; add('w', upper) }
                cancelled(); return
            }
        }
        add('ư', upper)
        loneW.add(chars.size - 1)
    }

    private fun lastIndexOfUo(): Int {
        for (i in chars.size - 2 downTo 0) if (chars[i] == 'u' && chars[i + 1] == 'o') return i
        return -1
    }

    private fun baseString(): String {
        val sb = StringBuilder(chars.size)
        for (c in chars) sb.append(c)
        return sb.toString()
    }

    private fun invalidateCache() { cachedText = null; cachedFallback = null }

    data class Candidate(val tone: Int, val text: String, val selected: Boolean)
}
