package com.telehex.typing

/**
 * Nội dung và hoa/thường của thanh gợi ý, dùng chung cho hai app. Mỗi app tự vẽ (Android có ô biến thể dấu riêng, iOS là
 * danh sách chữ) nhưng lấy cùng các chữ theo cùng thứ tự.
 */
object Suggestions {
    /** Số gợi ý chữ hiện trước emoji và biến thể dấu (thanh gợi ý Android). */
    const val LEADING_WORDS = 2

    /**
     * Âm tiết đang soạn là tiếng Việt hợp lệ, theo thứ tự thanh gợi ý Android: hai gợi ý chữ đầu (hoàn thành, rồi phương án
     * sửa gõ sai: "ban" có thể là gõ nhầm "bạn"), emoji, các biến thể dấu, rồi các gợi ý chữ còn lại. Mỗi chữ một lần;
     * chữ trùng biến thể dấu chỉ hiện ở biến thể dấu.
     */
    fun forSyllable(tones: List<String>, completions: List<String>, corrections: List<String>, emoji: List<String>): List<String> {
        val words = wordsBesideTones(tones, completions, corrections)
        return (words.take(LEADING_WORDS) + emoji + tones + words.drop(LEADING_WORDS)).distinct()
    }

    /** Gợi ý chữ (hoàn thành, sửa lỗi) đi kèm các biến thể dấu: bỏ chữ trùng một biến thể, giữ thứ tự. */
    fun wordsBesideTones(tones: List<String>, completions: List<String>, corrections: List<String>): List<String> =
        (tones + completions + corrections).distinct().drop(tones.distinct().size)

    /** Âm tiết có nguyên âm mà chưa hợp lệ: biến thể dấu và hoàn thành vô nghĩa, chỉ còn phương án sửa. */
    fun forInvalidSyllable(corrections: List<String>): List<String> = corrections.distinct()

    /**
     * Hoa/thường của gợi ý hoàn thành theo phần đã gõ [typed], như biến thể dấu: khoá hoa hay gõ hoa từ hai chữ trở lên
     * ("KH") thì viết hoa cả từ ("KHÔNG"); chữ đầu hoa ("Yêu") hay tên riêng đã học thì viết hoa chữ đầu.
     */
    fun casedCompletion(word: String, typed: String, capsLock: Boolean, usuallyCapitalized: Boolean): String = when {
        capsLock -> word.uppercase()
        typed.length > 1 && typed.none { it.isLowerCase() } -> word.uppercase()
        typed.firstOrNull()?.isUpperCase() == true || usuallyCapitalized -> capitalized(word)
        else -> word
    }

    /**
     * Hoa/thường của gợi ý từ kế tiếp: khoá hoa thì hoa cả từ; chữ sắp gõ đang hoa (đầu câu, ⇧) hay tên riêng đã học thì
     * hoa chữ đầu.
     */
    fun casedPrediction(word: String, capsLock: Boolean, capitalizeNext: Boolean, usuallyCapitalized: Boolean): String = when {
        capsLock -> word.uppercase()
        capitalizeNext || usuallyCapitalized -> capitalized(word)
        else -> word
    }

    /**
     * Hoa/thường của từ gõ vuốt. EN: khoá hoa thì hoa cả từ; chữ sắp gõ đang hoa, "I" và "I'm"… thì hoa chữ đầu. VI: như
     * gợi ý từ kế tiếp khi chưa gõ gì ([typed] rỗng), như gợi ý hoàn thành khi đang soạn.
     */
    fun casedGlide(
        word: String, english: Boolean, typed: String, capsLock: Boolean, capitalizeNext: Boolean, usuallyCapitalized: Boolean,
    ): String = when {
        english -> when {
            capsLock -> word.uppercase()
            capitalizeNext || word == "i" || word.startsWith("i'") -> capitalized(word)
            else -> word
        }
        typed.isEmpty() -> casedPrediction(word, capsLock, capitalizeNext, usuallyCapitalized)
        else -> casedCompletion(word, typed, capsLock, usuallyCapitalized)
    }

    /** Hiện gợi ý từ kế tiếp khi con trỏ ở đầu văn bản hoặc ngay sau khoảng trắng (chữ sau con trỏ không ảnh hưởng). */
    fun offersNextWords(textBeforeCursor: String?): Boolean =
        textBeforeCursor != null && (textBeforeCursor.isEmpty() || textBeforeCursor.last().isWhitespace())

    private fun capitalized(word: String) = word.replaceFirstChar { it.titlecase() }
}
