package com.telehex.telex

/**
 * Kiểm tra âm tiết tiếng Việt theo cấu trúc phụ âm đầu + vần (+ dấu thanh).
 *
 * Bảng vần sinh từ nhân nguyên âm × phụ âm cuối cho phép. Cố ý **rộng rãi**: thà nhận nhầm
 * vài vần hiếm còn hơn từ chối từ thật, vì kết quả dùng để (1) quay về chữ tiếng Anh khi
 * chuỗi phím không thể là tiếng Việt, và (2) gạch chân âm tiết lạ.
 */
object SyllableValidator {
    private val ONSETS = listOf(
        "", "b", "c", "ch", "d", "đ", "g", "gh", "gi", "h", "k", "kh", "l", "m", "n", "ng", "ngh",
        "nh", "p", "ph", "qu", "r", "s", "t", "th", "tr", "v", "x",
    )

    private val NO_CODA = listOf(
        "a", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "y",
        "ai", "ao", "au", "ay", "âu", "ây", "eo", "êu", "ia", "iu", "oa", "oe", "oi", "ôi", "ơi",
        "ua", "uê", "ui", "uơ", "uy", "ưa", "ưi", "ưu",
        "iêu", "yêu", "oai", "oay", "oeo", "uây", "uôi", "ươi", "ươu", "uya", "uyu", "oao",
    )
    private val CODA_C_NG = listOf(
        "a", "ă", "â", "e", "o", "ô", "u", "ư", "iê", "yê", "ươ", "uô", "oa", "oă", "uâ", "oo",
    )
    private val CODA_CH_NH = listOf("a", "ê", "i", "oa", "uê", "uy", "y")
    private val CODA_M_P = listOf(
        "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "iê", "yê", "ươ", "uô", "oa",
    )
    private val CODA_N_T = listOf(
        "a", "ă", "â", "e", "ê", "i", "o", "ô", "ơ", "u", "ư", "iê", "yê", "ươ", "uô",
        "oa", "oă", "oe", "uâ", "uê", "uy", "uyê", "y",
    )

    /** Tất cả vần hợp lệ (không dấu thanh). */
    val RHYMES: Set<String> by lazy {
        buildSet {
            addAll(NO_CODA)
            for (n in CODA_C_NG) { add(n + "c"); add(n + "ng") }
            for (n in CODA_CH_NH) { add(n + "ch"); add(n + "nh") }
            for (n in CODA_M_P) { add(n + "m"); add(n + "p") }
            for (n in CODA_N_T) { add(n + "n"); add(n + "t") }
        }
    }

    /** Tập âm tiết đầy đủ (phụ âm đầu + vần), không dấu thanh. */
    private val FULL: Set<String> by lazy {
        buildSet {
            for (o in ONSETS) for (r in RHYMES) if (spellingOk(o, r)) add(o + r)
        }
    }

    /** Mọi tiền tố của [FULL], kể cả chuỗi rỗng và các âm tiết đầy đủ. */
    private val PREFIXES: Set<String> by lazy {
        buildSet {
            add("")
            add("q") // "q" chỉ hợp lệ khi đi với "u"
            for (s in FULL) for (i in 1..s.length) add(s.substring(0, i))
        }
    }

    private fun spellingOk(onset: String, rhyme: String): Boolean {
        val first = rhyme[0]
        val frontVowel = first == 'e' || first == 'ê' || first == 'i'
        return when (onset) {
            "c" -> !(frontVowel || first == 'y')
            "k" -> frontVowel || first == 'y'
            "g" -> first != 'e' && first != 'ê' // "gì", "gìn" hợp lệ; "ge" phải là "ghe"
            "ng" -> !frontVowel
            "gh", "ngh" -> frontVowel
            "gi" -> first != 'i'
            "qu" -> first != 'u' && first != 'ư' && first != 'o'
            else -> true
        }
    }

    /** Âm tiết (đã bỏ dấu thanh) có phải tiền tố của một âm tiết hợp lệ không. */
    fun isValidPrefix(base: String): Boolean = base in PREFIXES

    /** Âm tiết (đã bỏ dấu thanh) có đầy đủ và hợp lệ không, kể cả ràng buộc dấu với p/t/c/ch. */
    fun isValid(base: String, tone: Int): Boolean {
        if (base !in FULL) return false
        if (tone == Tone.GRAVE || tone == Tone.HOOK || tone == Tone.TILDE) {
            if (endsWithStop(base)) return false
        }
        return true
    }

    private fun endsWithStop(s: String): Boolean =
        s.endsWith("c") || s.endsWith("ch") || s.endsWith("p") || s.endsWith("t")

    /**
     * Các chữ có thể còn biến đổi về sau bằng phím Telex (aa, w, dd...). Khi kiểm tra
     * tiền tố, mỗi chữ gốc được phép "trở thành" dạng có mũ/móc.
     */
    private val EXPANSIONS: Map<Char, String> = mapOf(
        'a' to "aăâ", 'e' to "eê", 'o' to "oôơ", 'u' to "uư", 'd' to "dđ",
    )

    /**
     * Có cách nào để chuỗi hiện tại (chữ thường, không dấu thanh) tiếp tục thành âm tiết hợp lệ
     * không, nếu một số chữ gốc sau này được gắn mũ/móc.
     */
    fun couldBecomeValid(base: String, tone: Int = Tone.NONE): Boolean {
        if (base.isEmpty()) return true
        if (base.length > 8) return false
        // Phụ âm cuối p/t/c/ch luôn kết thúc âm tiết và chỉ đi với sắc/nặng.
        if ((tone == Tone.GRAVE || tone == Tone.HOOK || tone == Tone.TILDE) && endsWithStop(base)) return false
        return expand(base, 0, StringBuilder(base.length))
    }

    private fun expand(base: String, i: Int, sb: StringBuilder): Boolean {
        if (i == base.length) return sb.toString() in PREFIXES
        val options = EXPANSIONS[base[i]] ?: base[i].toString()
        for (opt in options) {
            sb.append(opt)
            val ok = expand(base, i + 1, sb)
            sb.setLength(sb.length - 1)
            if (ok) return true
        }
        return false
    }
}
