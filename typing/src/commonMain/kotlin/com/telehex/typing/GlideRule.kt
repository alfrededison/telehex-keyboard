package com.telehex.typing

/** Khi nào một đường vuốt trên phím chữ là gõ vuốt, dùng chung cho hai app. */
object GlideRule {
    const val MIN_LETTERS = 3
    /** Độ dài đường vuốt tối thiểu, theo bề rộng phím. */
    const val MIN_LENGTH = 1.5f

    /**
     * Đi qua ít nhất [MIN_LETTERS] phím chữ khác nhau và dài ít nhất [MIN_LENGTH] bề rộng phím (không phân biệt hướng):
     * vuốt ngắn qua ba phím sát nhau ở mép vẫn là chạm/vuốt thường.
     */
    fun isGlide(distinctLetters: Int, lengthInKeyWidths: Float): Boolean =
        distinctLetters >= MIN_LETTERS && lengthInKeyWidths >= MIN_LENGTH
}
