package com.telehex.keyboard.layout

/**
 * Lớp ký tự 2 trang. Trang 1 sinh từ lớp chữ: số luôn nằm ở vị trí của chữ tương ứng
 * (Q→1, W→2 … P→0); trang 2 cố định.
 */
object SymbolPages {
    private val DIGIT_OF_LETTER: Map<String, String> = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
    )

    /** Ký tự trang 1 đặt vào vị trí của các chữ còn lại (theo thứ tự quét hàng/cột của layout mặc định). */
    private val PAGE1_FILL: Map<String, String> = mapOf(
        "h" to "-",
        "a" to "@", "s" to "#", "d" to "$", "f" to "%", "g" to "&", "j" to "+",
        "z" to "*", "c" to "(", "b" to ")", "n" to "=", "m" to "/", "k" to "!",
        "x" to KeyCode.PAGE, "v" to "?", "l" to ":",
    )

    val PAGE2: List<List<String?>> = listOf(
        listOf("~", "`", "|", KeyCode.EMOJI, KeyCode.NUMBERS, "^", "°"),
        listOf("[", "]", "{", KeyCode.SETTINGS, "}", "<", ">"),
        listOf("_", "\"", "'", ",", ";", "\\", "•"),
        listOf("€", "£", "¥", ".", "₫", "×", "÷"),
        listOf(KeyCode.PAGE, "…", null, null, null, "©", KeyCode.ENTER),
    )

    /** Sinh trang 1 từ lớp chữ hiện tại. Phím chức năng, dấu phẩy/chấm và ô phím cách giữ nguyên chỗ. */
    fun page1(layout: LayoutModel): List<List<String?>> =
        layout.rows.map { row ->
            row.map { code ->
                when {
                    code == null -> null
                    KeyCode.isFunction(code) -> code
                    code == "," || code == "." -> code
                    else -> DIGIT_OF_LETTER[code] ?: PAGE1_FILL[code] ?: code
                }
            }
        }
}
