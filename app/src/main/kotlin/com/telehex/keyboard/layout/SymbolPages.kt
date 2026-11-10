package com.telehex.keyboard.layout

/**
 * Lớp 123: hai trang [CellGrid] gắn theo ô, sắp xếp riêng ở màn Sắp xếp phím. Phím chức năng và ô phím cách lấy
 * từ lớp chữ nên chuyển trang không làm phím 123 nhảy chỗ. Phím ☺ thành phím 1234 (dialpad), như Gboard. Ô ⚙, ⇧ và
 * `,` `.` giữ được giá trị của trang ([KeyLayers.symbolUsable]): có giá trị thì thay phím của lớp chữ, trống thì
 * giữ phím đó. Phím chuyển trang là một giá trị của lưới (mặc định ở ô ⚙, ở bố cục truyền thống là ô ⇧; dữ liệu
 * trước 0.7.1 ở ô chữ Z).
 */
object SymbolPages {
    private const val P = KeyCode.PAGE

    /**
     * Mặc định theo hai trang ký hiệu của Gboard, xếp trên bố cục [LayoutModel.DEFAULT]: số ở hai hàng trên,
     * `@ # $ _ & - +` ở hàng chữ giữa, `* " ' : / ! ?` ở hàng Z…M; phím 1/2 ở ô ⚙; trang 2 có `<` `>` thay `,` `.`.
     * Lưới có 26 ô cho 27 ký tự trang 1 của Gboard nên `;` sang trang 2 (bỏ ™, ∆; ₫ thay ¢, § thay ¶).
     */
    val DEFAULT: List<CellGrid> = listOf(
        CellGrid(
            listOf(
                listOf("1", "3", "5", "7", "9", "0", ""),
                listOf(P, "2", "4", "6", "8", "(", ")"),
                listOf("@", "#", "$", "_", "&", "-", "+"),
                listOf("*", "\"", "'", ":", "/", "!", "?"),
                listOf("", "", "", "", "", "", ""),
            )
        ),
        CellGrid(
            listOf(
                listOf("~", "|", "√", "÷", "§", "\\", ""),
                listOf(P, "`", "•", "π", "×", "{", "}"),
                listOf("£", "₫", "€", "¥", "^", "°", "="),
                listOf("%", "©", "®", ";", "✓", "[", "]"),
                listOf("", "<", "", "", "", ">", ""),
            )
        ),
    )

    /**
     * Hai trang của bố cục truyền thống: số 1…0 ở hàng trên, phím 1/2 ở ô ⇧ (☺ thành 1234 như mọi bố cục). Hàng giữa
     * có 9 ô nên `/` sang ô ⚙; dấu phẩy giữ chỗ cạnh phím cách. Trang 2 có `\` ở ô ⚙, `<` `>` hai bên phím cách
     * (₫ thay ¢).
     */
    val QWERTY: List<CellGrid> = listOf(
        CellGrid(
            listOf(
                "1 2 3 4 5 6 7 8 9 0".split(' '),
                "@ # \$ _ & - + ( )".split(' '),
                listOf(P) + "* \" ' : ; ! ?".split(' ') + listOf(""),
                listOf("", "", "/", "", "", "", "", "", "", ""),
            )
        ),
        CellGrid(
            listOf(
                "~ ` | • √ π ÷ × § ∆".split(' '),
                "£ ₫ € ¥ ^ ° = { }".split(' '),
                listOf(P) + "% © ® ™ ✓ [ ]".split(' ') + listOf(""),
                listOf("", "", "\\", "<", "", "", "", "", ">", ""),
            )
        ),
    )

    /** Hai trang có sẵn của kiểu lưới [spec]. */
    fun defaultFor(spec: GridSpec): List<CellGrid> = when (spec) {
        GridSpec.HEX7 -> DEFAULT
        GridSpec.QWERTY -> QWERTY
    }

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

    /** Cách cũ: mỗi ký tự đi theo chữ của nó trên [layout]. Chỉ dùng để chuyển dữ liệu cũ. */
    fun fromLetters(layout: LayoutModel): List<CellGrid> = listOf(PAGE1_OF_LETTER, PAGE2_OF_LETTER).map { ofLetter ->
        CellGrid.fromCodes(layout) { code -> if (KeyCode.isLetter(code)) ofLetter[code] else null }
    }

    /** Ghép một trang với phím chức năng, `,` `.` và phím cách (`null`) của lớp chữ. */
    fun render(layout: LayoutModel, page: CellGrid): List<List<String?>> =
        layout.rows.mapIndexed { r, row ->
            row.mapIndexed { c, code ->
                when {
                    code == null -> null
                    code == KeyCode.EMOJI -> KeyCode.DIAL
                    code == KeyCode.SETTINGS || code == KeyCode.SHIFT || code == "," || code == "." -> page[r, c].ifEmpty { code }
                    KeyCode.isFunction(code) -> code
                    else -> page[r, c]
                }
            }
        }
}
