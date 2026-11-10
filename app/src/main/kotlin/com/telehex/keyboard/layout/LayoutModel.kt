package com.telehex.keyboard.layout

import org.json.JSONArray
import org.json.JSONObject

/** Mã phím trong lưới. Chữ thường `a`..`z`, dấu `,` `.`, hoặc mã chức năng bắt đầu bằng `#`. */
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
        if ((0 until n).any { rows[spec.spaceRow][spec.spaceFirstCol + it] != null }) return "Phím cách phải chiếm $n ô giữa hàng cuối"
        if (rows.flatten().count { it == null } != n) return "Chỉ có $n ô thuộc phím cách"
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

        /**
         * Truyền thống: đúng thứ tự QWERTY. Hàng Z: ⇧ sát mép trái, Z…M căn giữa, ⌫ sát mép phải. Hàng cuối:
         * 123 ☺ ⚙ `,` bên trái phím cách 4 ô, `.` ↵ bên phải.
         */
        val QWERTY: LayoutModel = LayoutModel(
            listOf(
                "qwertyuiop".map { "$it" },
                "asdfghjkl".map { "$it" },
                listOf(KeyCode.SHIFT) + "zxcvbnm".map { "$it" } + listOf(KeyCode.BACKSPACE),
                listOf(KeyCode.NUMBERS, KeyCode.EMOJI, KeyCode.SETTINGS, ",", null, null, null, null, ".", KeyCode.ENTER),
            )
        )

        /** Lớp chữ có sẵn của kiểu lưới [spec]. */
        fun defaultFor(spec: GridSpec): LayoutModel = when (spec) {
            GridSpec.HEX7 -> DEFAULT
            GridSpec.QWERTY -> QWERTY
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
