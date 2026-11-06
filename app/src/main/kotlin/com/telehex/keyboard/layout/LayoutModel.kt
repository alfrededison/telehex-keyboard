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

    fun isLetter(code: String): Boolean = code.length == 1 && code[0] in 'a'..'z'
    fun isFunction(code: String): Boolean = code.startsWith("#")
}

/**
 * Lưới 5 hàng × 7 cột của lớp chữ. `null` = ô thuộc phím cách (3 ô giữa hàng cuối).
 */
class LayoutModel(val rows: List<List<String?>>) {

    init {
        require(rows.size == ROWS) { "Cần $ROWS hàng" }
        require(rows.all { it.size == COLS }) { "Mỗi hàng cần $COLS cột" }
    }

    operator fun get(r: Int, c: Int): String? = rows[r][c]

    /** Vị trí (hàng, cột) của một mã phím, hoặc null. */
    fun find(code: String): Pair<Int, Int>? {
        for (r in 0 until ROWS) for (c in 0 until COLS) if (rows[r][c] == code) return r to c
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
        if (rows[4][2] != null || rows[4][3] != null || rows[4][4] != null) return "Phím cách phải chiếm 3 ô giữa hàng cuối"
        if (rows.flatten().count { it == null } != 3) return "Chỉ có 3 ô thuộc phím cách"
        return null
    }

    /** Các ô kề (tối đa 6) của ô (r, c) trên lưới lục giác lệch hàng [0.5, 0, 0.5, 0, 0.5]. */
    fun neighborCells(r: Int, c: Int): List<Pair<Int, Int>> {
        val shifted = r % 2 == 0 // hàng lệch ½ sang phải
        val cand = if (shifted) listOf(r to c - 1, r to c + 1, r - 1 to c, r - 1 to c + 1, r + 1 to c, r + 1 to c + 1)
        else listOf(r to c - 1, r to c + 1, r - 1 to c - 1, r - 1 to c, r + 1 to c - 1, r + 1 to c)
        return cand.filter { (rr, cc) -> rr in 0 until ROWS && cc in 0 until COLS }
    }

    /** Chữ cái → các chữ cái kề trên lưới (bỏ phím chức năng và ô phím cách). */
    fun letterNeighbors(): Map<Char, List<Char>> {
        val out = HashMap<Char, List<Char>>()
        for (r in 0 until ROWS) for (c in 0 until COLS) {
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
        const val ROWS = 5
        const val COLS = 7

        val DEFAULT: LayoutModel = LayoutModel(
            listOf(
                listOf("w", "r", "y", KeyCode.SETTINGS, "u", "o", KeyCode.NUMBERS),
                listOf("q", "e", "t", "f", "h", "i", "p"),
                listOf("a", "s", "d", "g", "j", "k", "l"),
                listOf("z", "x", "c", "v", "b", "n", "m"),
                listOf(KeyCode.EMOJI, ",", null, null, null, ".", KeyCode.ENTER),
            )
        )

        /**
         * Chia đều tải cho hai ngón cái (~50/50) và tăng gõ luân phiên (~62% → ~68%), tối ưu bằng
         * mô hình Fitts hai ngón trên tần suất phím Telex. Giữ 12 chữ ở chỗ cũ (W R Y Q E F P S L Z X C);
         * A (phím nhiều nhất) ở cột giữa cho ngón nào rảnh gõ; E bên trái, U I O bên phải.
         */
        val BALANCED: LayoutModel = LayoutModel(
            listOf(
                listOf("w", "r", "y", KeyCode.SETTINGS, "b", "k", KeyCode.NUMBERS),
                listOf("q", "e", "g", "f", "j", "u", "p"),
                listOf("d", "s", "h", "v", "i", "o", "l"),
                listOf("z", "x", "c", "a", "t", "n", "m"),
                listOf(KeyCode.EMOJI, ",", null, null, null, ".", KeyCode.ENTER),
            )
        )

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
