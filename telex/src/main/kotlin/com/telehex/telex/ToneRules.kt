package com.telehex.telex

/** Quy tắc nguyên âm và vị trí đặt dấu tiếng Việt. */
object ToneRules {
    const val VOWELS = "aăâeêioôơuưy"
    private const val MODIFIED = "ăâêôơư"

    private val TONED: Map<Char, String> = mapOf(
        'a' to "aáàảãạ", 'ă' to "ăắằẳẵặ", 'â' to "âấầẩẫậ",
        'e' to "eéèẻẽẹ", 'ê' to "êếềểễệ",
        'i' to "iíìỉĩị",
        'o' to "oóòỏõọ", 'ô' to "ôốồổỗộ", 'ơ' to "ơớờởỡợ",
        'u' to "uúùủũụ", 'ư' to "ưứừửữự",
        'y' to "yýỳỷỹỵ",
    )

    /** Chữ gốc (không dấu thanh) của một ký tự có dấu, hoặc chính nó. */
    private val BASE_OF: Map<Char, Char> = buildMap {
        for ((base, forms) in TONED) for (ch in forms) put(ch, base)
    }

    fun isVowel(ch: Char): Boolean = VOWELS.indexOf(ch) >= 0

    /** Bỏ dấu thanh (giữ mũ/móc): "ế" → "ê". */
    fun stripTone(ch: Char): Char = BASE_OF[ch] ?: ch

    /** Mã dấu thanh của một ký tự (0 nếu không có). */
    fun toneOfChar(ch: Char): Int {
        val base = BASE_OF[ch] ?: return 0
        return TONED.getValue(base).indexOf(ch)
    }

    /**
     * Chỉ số các nguyên âm "tính được" trong âm tiết: bỏ `u` sau `q`, bỏ `i` trong `gi` + nguyên âm.
     */
    fun vowelIndices(word: List<Char>): List<Int> {
        val out = ArrayList<Int>(3)
        for (i in word.indices) {
            val ch = word[i]
            if (!isVowel(ch)) continue
            if (ch == 'u' && i > 0 && word[i - 1] == 'q') continue
            if (ch == 'i' && i == 1 && word[0] == 'g' && word.size > 2 && isVowel(word[2])) continue
            out.add(i)
        }
        return out
    }

    /** Vị trí đặt dấu thanh, hoặc -1 nếu không có nguyên âm. */
    fun toneIndex(word: List<Char>, newToneStyle: Boolean): Int {
        val idx = vowelIndices(word)
        if (idx.isEmpty()) return -1
        val mods = idx.filter { MODIFIED.indexOf(word[it]) >= 0 }
        if (mods.isNotEmpty()) return mods.last()
        if (idx.size >= 3) return idx[1]
        if (idx.size == 2) {
            val hasFinal = idx[1] < word.size - 1
            if (hasFinal) return idx[1]
            val pair = "" + word[idx[0]] + word[idx[1]]
            if (newToneStyle && (pair == "oa" || pair == "oe" || pair == "uy")) return idx[1]
            return idx[0]
        }
        return idx[0]
    }

    /** Ghép âm tiết thành chuỗi: đặt dấu thanh và áp chữ hoa. */
    fun spell(word: List<Char>, tone: Int, caps: List<Boolean>, newToneStyle: Boolean): String {
        val chars = word.toMutableList()
        if (tone in 1..5) {
            val i = toneIndex(chars, newToneStyle)
            if (i >= 0) TONED[chars[i]]?.let { chars[i] = it[tone] }
        }
        val sb = StringBuilder(chars.size)
        for (n in chars.indices) {
            val ch = chars[n]
            sb.append(if (caps.getOrElse(n) { false }) ch.uppercaseChar() else ch)
        }
        return sb.toString()
    }
}
