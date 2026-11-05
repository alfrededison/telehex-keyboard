package com.telehex.telex

/** Tách một âm tiết tiếng Việt thành chuỗi phím Telex đã gõ ("người" → "nguowif"). */
object TelexDecomposer {
    private val BASE: Map<Char, String> = mapOf(
        'ă' to "aw", 'â' to "aa", 'ê' to "ee", 'ô' to "oo", 'ơ' to "ow", 'ư' to "uw", 'đ' to "dd",
    )

    /**
     * @return cặp (chuỗi phím, danh sách chữ hoa theo từng phím), hoặc null nếu có ký tự không phải chữ cái Latin/Việt.
     */
    fun keysOf(word: String, config: TelexConfig = TelexConfig()): Pair<String, List<Boolean>>? {
        if (word.isEmpty()) return null
        val keys = StringBuilder()
        val caps = ArrayList<Boolean>()
        var tone = Tone.NONE
        var prevBase = ' '
        for (ch in word) {
            val upper = ch.isUpperCase()
            val lower = ch.lowercaseChar()
            val t = ToneRules.toneOfChar(lower)
            if (t != 0) tone = t
            val base = ToneRules.stripTone(lower)
            var seq = BASE[base] ?: if (base in 'a'..'z') base.toString() else return null
            // "oo" thật (xoong): gõ o lần ba để huỷ ô và in thêm o
            if (base == prevBase && (base == 'a' || base == 'e' || base == 'o')) seq = seq + seq
            prevBase = base
            for (k in seq) { keys.append(k); caps.add(upper) }
        }
        if (tone != Tone.NONE) {
            val key = config.effectiveToneKeys.entries.firstOrNull { it.value == tone }?.key ?: return null
            keys.append(key); caps.add(false)
        }
        return keys.toString() to caps
    }
}
