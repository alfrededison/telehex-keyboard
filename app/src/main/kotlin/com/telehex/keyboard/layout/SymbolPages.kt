package com.telehex.keyboard.layout

/**
 * Lớp 123: hai trang [CellGrid] gắn theo ô, sắp xếp riêng ở màn Sắp xếp phím. Phím chức năng, dấu phẩy/chấm
 * và ô phím cách lấy từ lớp chữ nên chuyển trang không làm phím 123 / ⚙ nhảy chỗ. Phím ☺ thành phím 1234
 * (dialpad), như Gboard. Phím chuyển trang là một ô của lưới (mặc định ở chỗ chữ Z, mép trái hàng 4).
 */
object SymbolPages {
    private val PAGE1_OF_LETTER: Map<String, String> = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        "h" to "-",
        "a" to "@", "s" to "#", "d" to "$", "f" to "%", "g" to "&", "j" to "+",
        "z" to KeyCode.PAGE, "c" to "(", "b" to ")", "n" to "=", "m" to "/", "k" to "!",
        "x" to "*", "v" to "?", "l" to ":",
    )

    private val PAGE2_OF_LETTER: Map<String, String> = mapOf(
        "w" to "~", "r" to "`", "y" to "|", "o" to "^", "p" to "°",
        "q" to "[", "e" to "]", "t" to "{", "u" to "}", "i" to "<", "h" to ">",
        "a" to "_", "s" to "\"", "d" to "'", "f" to ";", "g" to "\\", "j" to "•",
        "z" to KeyCode.PAGE, "c" to "£", "b" to "¥", "n" to "₫", "m" to "×", "k" to "÷",
        "x" to "€", "v" to "…", "l" to "©",
    )

    /** Cách cũ: mỗi ký tự đi theo chữ của nó trên [layout]. Dùng để chuyển dữ liệu cũ và dựng mặc định. */
    fun fromLetters(layout: LayoutModel): List<CellGrid> = listOf(PAGE1_OF_LETTER, PAGE2_OF_LETTER).map { ofLetter ->
        CellGrid.fromCodes(layout) { code -> if (KeyCode.isLetter(code)) ofLetter[code] else null }
    }

    /** Mặc định: theo chữ trên bố cục mặc định, rồi đưa số (chỉ có ở trang 1) về chỗ mặc định. */
    val DEFAULT: List<CellGrid> = fromLetters(LayoutModel.DEFAULT).let { listOf(KeyLayers.pinDigits(it[0]), it[1]) }

    /** Ghép một trang với phím chức năng, `,` `.` và phím cách (`null`) của lớp chữ. */
    fun render(layout: LayoutModel, page: CellGrid): List<List<String?>> =
        layout.rows.mapIndexed { r, row ->
            row.mapIndexed { c, code ->
                when {
                    code == null -> null
                    code == KeyCode.EMOJI -> KeyCode.DIAL
                    KeyCode.isFunction(code) || code == "," || code == "." -> code
                    else -> page[r, c]
                }
            }
        }
}
