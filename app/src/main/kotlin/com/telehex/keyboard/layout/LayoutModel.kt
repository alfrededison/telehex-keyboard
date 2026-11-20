package com.telehex.keyboard.layout

import org.json.JSONArray
import org.json.JSONObject

/**
 * Mã phím trong lưới. Chữ thường `a`..`z`, số `0`..`9` (hàng số), dấu câu ([PUNCT]: `,` `.`, và `?` `!` `'` của tổ ong 8 cột),
 * hoặc mã chức năng bắt đầu bằng `#`.
 */
object KeyCode {
    const val EMOJI = "#emoji"
    const val NUMBERS = "#123"
    const val SETTINGS = "#set"
    const val ENTER = "#enter"
    const val PAGE = "#page"
    const val ABC = "#abc"
    const val BACKSPACE = "#back"
    const val SPACE = "#space"
    const val DIAL = "#dial"
    const val SHIFT = "#shift"

    fun isLetter(code: String): Boolean = code.length == 1 && code[0] in 'a'..'z'
    fun isDigit(code: String): Boolean = code.length == 1 && code[0] in '0'..'9'

    /** Dấu câu có phím riêng ở lớp chữ: chạm gõ đúng dấu, vuốt xuống gõ ký tự vuốt xuống, giữ được giá trị lớp 123. */
    val PUNCT: Set<String> = setOf(",", ".", "?", "!", "'")

    fun isPunct(code: String): Boolean = code in PUNCT
    fun isFunction(code: String): Boolean = code.startsWith("#")
}

/**
 * Lưới lớp chữ theo một [GridSpec] (nhận ra từ số ô mỗi hàng). `null` = ô thuộc phím cách (các ô giữa hàng cuối).
 */
class LayoutModel(val rows: List<List<String?>>) {

    val spec: GridSpec = requireNotNull(GridSpec.of(rows.map { it.size })) { "Lưới không đúng kiểu nào" }

    operator fun get(r: Int, c: Int): String? = rows[r][c]

    /** Vị trí (hàng, cột) của một mã phím, hoặc null. */
    fun find(code: String): Pair<Int, Int>? {
        for (r in rows.indices) for (c in rows[r].indices) if (rows[r][c] == code) return r to c
        return null
    }

    fun withSwapped(a: Pair<Int, Int>, b: Pair<Int, Int>): LayoutModel {
        val m = rows.map { it.toMutableList() }
        val t = m[a.first][a.second]
        m[a.first][a.second] = m[b.first][b.second]
        m[b.first][b.second] = t
        return LayoutModel(m)
    }

    /**
     * Ràng buộc khi lưu: đủ 26 chữ, mỗi phím xuất hiện đúng một lần, có 123 và ⚙.
     * @return thông báo lỗi, hoặc null nếu hợp lệ.
     */
    fun validate(): String? {
        val codes = rows.flatten().filterNotNull()
        val letters = codes.filter { KeyCode.isLetter(it) }
        if (letters.toSet().size != 26) return "Cần đủ 26 chữ cái"
        if (codes.size != codes.toSet().size) return "Mỗi phím chỉ xuất hiện một lần"
        if (KeyCode.NUMBERS !in codes) return "Thiếu phím 123"
        if (KeyCode.SETTINGS !in codes) return "Thiếu phím ⚙"
        if (KeyCode.ENTER !in codes) return "Thiếu phím ↵"
        val n = spec.spaceCells
        if (rows.flatten().count { it == null } != n) return "Chỉ có $n ô thuộc phím cách"
        val first = rows[spec.spaceRow].indexOf(null)
        if (first < 0 || (0 until n).any { rows[spec.spaceRow].getOrNull(first + it) != null }) return "Phím cách phải chiếm $n ô liền nhau ở hàng cuối"
        return null
    }

    /** Các ô kề (tối đa 6) của ô (r, c), xem [GridSpec.neighborCells]. */
    fun neighborCells(r: Int, c: Int): List<Pair<Int, Int>> = spec.neighborCells(r, c)

    /** Chữ cái → các chữ cái kề trên lưới (bỏ phím chức năng và ô phím cách). */
    fun letterNeighbors(): Map<Char, List<Char>> {
        val out = HashMap<Char, List<Char>>()
        for (r in rows.indices) for (c in rows[r].indices) {
            val code = rows[r][c] ?: continue
            if (!KeyCode.isLetter(code)) continue
            out[code[0]] = neighborCells(r, c).mapNotNull { (rr, cc) -> rows[rr][cc]?.takeIf { KeyCode.isLetter(it) }?.get(0) }
        }
        return out
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (row in rows) {
            val jr = JSONArray()
            for (c in row) jr.put(c ?: JSONObject.NULL)
            arr.put(jr)
        }
        return arr.toString()
    }

    companion object {
        /**
         * Gần QWERTY nhất trên lưới 7 cột: hai hàng trên đọc zigzag `⚙ Q W E R T G Y H U I O P 123` (⚙ sát mép trái,
         * U I O P zigzag ở góc phải), hàng giữa `A S D F` bên trái và `J K L` thẳng hàng bên phải; G H lên hai hàng
         * trên vì hàng giữa chỉ có 7 ô.
         */
        val DEFAULT: LayoutModel = LayoutModel(
            listOf(
                listOf("q", "e", "t", "y", "u", "o", KeyCode.NUMBERS),
                listOf(KeyCode.SETTINGS, "w", "r", "g", "h", "i", "p"),
                listOf("a", "s", "d", "f", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m"),
                listOf(KeyCode.EMOJI, ",", null, null, null, ".", KeyCode.ENTER),
            )
        )

        /** Hàng số 1…0, dùng chung cho các bố cục có hàng số. */
        private val DIGIT_ROW: List<String?> = "1234567890".map { "$it" }

        /** Tổ ong có hàng số: hàng 1…0 (10 lục giác nhỏ) trên [DEFAULT]. */
        val HEX7_NUM: LayoutModel = LayoutModel(listOf(DIGIT_ROW) + DEFAULT.rows)

        /** Ba hàng chữ QWERTY của bố cục truyền thống: hàng Z có ⇧ sát mép trái, Z…M căn giữa, ⌫ sát mép phải. */
        private val QWERTY_LETTER_ROWS: List<List<String?>> = listOf(
            "qwertyuiop".map { "$it" },
            "asdfghjkl".map { "$it" },
            listOf(KeyCode.SHIFT) + "zxcvbnm".map { "$it" } + listOf(KeyCode.BACKSPACE),
        )

        /**
         * Hàng cuối của bố cục truyền thống: ba phím nhỏ 123 ☺ ⚙ bên trái phím cách 4 ô, ba phím `,` `.` ↵ bên phải
         * (đến 0.8.3 phím cách lệch phải: 123 ☺ ⚙ `,` bên trái).
         */
        private val QWERTY_BOTTOM_ROW: List<String?> =
            listOf(KeyCode.NUMBERS, KeyCode.EMOJI, KeyCode.SETTINGS, null, null, null, null, ",", ".", KeyCode.ENTER)

        /** Truyền thống: đúng thứ tự QWERTY. */
        val QWERTY: LayoutModel = LayoutModel(QWERTY_LETTER_ROWS + listOf(QWERTY_BOTTOM_ROW))

        /** Truyền thống có hàng số: hàng 1…0 trên [QWERTY]. */
        val QWERTY_NUM: LayoutModel = LayoutModel(listOf(DIGIT_ROW) + QWERTY_LETTER_ROWS + listOf(QWERTY_BOTTOM_ROW))

        /**
         * Tổ ong 8 cột: lưới [DEFAULT] thêm cột phải. Chữ hàng Q, W, A giữ đúng chỗ; hàng Z nhường ô đầu cho ⇧ nên Z…M
         * dịch phải một ô (Z nằm giữa A và S như QWERTY). ⌫ sau L, `'` `!` ở cột thêm của hai hàng trên, `?` cạnh `.`.
         */
        val HEX8: LayoutModel = LayoutModel(
            listOf(
                listOf("q", "e", "t", "y", "u", "o", "'", KeyCode.NUMBERS),
                listOf(KeyCode.SETTINGS, "w", "r", "g", "h", "i", "p", "!"),
                listOf("a", "s", "d", "f", "j", "k", "l", KeyCode.BACKSPACE),
                listOf(KeyCode.SHIFT, "z", "x", "c", "v", "b", "n", "m"),
                listOf(KeyCode.EMOJI, ",", null, null, null, ".", "?", KeyCode.ENTER),
            )
        )

        /** Tổ ong 8 cột có hàng số: hàng 1…0 trên [HEX8]. */
        val HEX8_NUM: LayoutModel = LayoutModel(listOf(DIGIT_ROW) + HEX8.rows)

        /** Lớp chữ có sẵn của kiểu lưới [spec]. */
        fun defaultFor(spec: GridSpec): LayoutModel = when (spec) {
            GridSpec.HEX7 -> DEFAULT
            GridSpec.HEX7_NUM -> HEX7_NUM
            GridSpec.HEX8 -> HEX8
            GridSpec.HEX8_NUM -> HEX8_NUM
            GridSpec.QWERTY -> QWERTY
            GridSpec.QWERTY_NUM -> QWERTY_NUM
        }

        fun fromJson(json: String): LayoutModel? = runCatching {
            val arr = JSONArray(json)
            val rows = (0 until arr.length()).map { r ->
                val jr = arr.getJSONArray(r)
                (0 until jr.length()).map { c -> if (jr.isNull(c)) null else jr.getString(c) }
            }
            LayoutModel(rows)
        }.getOrNull()
    }
}
