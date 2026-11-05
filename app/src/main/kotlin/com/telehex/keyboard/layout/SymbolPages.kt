package com.telehex.keyboard.layout

/**
 * Lớp ký tự 2 trang, cả hai sinh từ lớp chữ: mỗi ký tự gắn với một chữ cái nên đi theo chữ đó
 * khi đổi bố cục (Q→1, W→2 … P→0). Phím chức năng, dấu phẩy/chấm và ô phím cách giữ nguyên chỗ,
 * nên chuyển trang không làm phím 123 / ⚙ / ☺ nhảy chỗ.
 */
object SymbolPages {
    private val PAGE1_OF_LETTER: Map<String, String> = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        "h" to "-",
        "a" to "@", "s" to "#", "d" to "$", "f" to "%", "g" to "&", "j" to "+",
        "z" to "*", "c" to "(", "b" to ")", "n" to "=", "m" to "/", "k" to "!",
        "x" to KeyCode.PAGE, "v" to "?", "l" to ":",
    )

    private val PAGE2_OF_LETTER: Map<String, String> = mapOf(
        "w" to "~", "r" to "`", "y" to "|", "o" to "^", "p" to "°",
        "q" to "[", "e" to "]", "t" to "{", "u" to "}", "i" to "<", "h" to ">",
        "a" to "_", "s" to "\"", "d" to "'", "f" to ";", "g" to "\\", "j" to "•",
        "z" to "€", "c" to "£", "b" to "¥", "n" to "₫", "m" to "×", "k" to "÷",
        "x" to KeyCode.PAGE, "v" to "…", "l" to "©",
    )

    fun page1(layout: LayoutModel): List<List<String?>> = page(layout, PAGE1_OF_LETTER)

    fun page2(layout: LayoutModel): List<List<String?>> = page(layout, PAGE2_OF_LETTER)

    private fun page(layout: LayoutModel, ofLetter: Map<String, String>): List<List<String?>> =
        layout.rows.map { row ->
            row.map { code ->
                when {
                    code == null -> null
                    KeyCode.isFunction(code) -> code
                    code == "," || code == "." -> code
                    else -> ofLetter[code] ?: code
                }
            }
        }
}
