package com.telehex.telex

/**
 * Cấu hình engine. Engine nhận danh sách phím dấu từ đây, không hard-code.
 *
 * @property toneKeys phím → mã dấu (1 sắc, 2 huyền, 3 hỏi, 4 ngã, 5 nặng, 6 bỏ dấu).
 * @property newToneStyle `true` = kiểu mới ("hoà", "thuý"), `false` = kiểu cũ ("hòa", "thúy").
 * @property lAsGrave "L cũng là dấu huyền" (lal → là). Mặc định tắt.
 * @property englishFallback khi chuỗi phím không thể là âm tiết tiếng Việt thì hiện đúng
 *   các phím đã gõ (windows không thành ưindows).
 */
data class TelexConfig(
    val toneKeys: Map<Char, Int> = DEFAULT_TONE_KEYS,
    val newToneStyle: Boolean = false,
    val lAsGrave: Boolean = false,
    val englishFallback: Boolean = true,
) {
    /** Bảng phím dấu thực tế, đã gộp tuỳ chọn [lAsGrave]. */
    val effectiveToneKeys: Map<Char, Int> =
        if (lAsGrave) toneKeys + ('l' to Tone.GRAVE) else toneKeys

    fun toneOf(key: Char): Int = effectiveToneKeys[key] ?: 0

    companion object {
        val DEFAULT_TONE_KEYS: Map<Char, Int> = mapOf(
            's' to Tone.ACUTE,
            'f' to Tone.GRAVE,
            'r' to Tone.HOOK,
            'x' to Tone.TILDE,
            'j' to Tone.DOT,
            'z' to Tone.REMOVE,
        )
    }
}

object Tone {
    const val NONE = 0
    const val ACUTE = 1  // sắc
    const val GRAVE = 2  // huyền
    const val HOOK = 3   // hỏi
    const val TILDE = 4  // ngã
    const val DOT = 5    // nặng
    const val REMOVE = 6 // bỏ dấu (z)

    /** Thứ tự hiện trên thanh gợi ý: sắc, hỏi, huyền, ngã, nặng, không dấu. */
    val CANDIDATE_ORDER: List<Int> = listOf(ACUTE, HOOK, GRAVE, TILDE, DOT, NONE)
}
