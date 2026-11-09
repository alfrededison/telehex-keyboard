package com.telehex.keyboard.layout

/**
 * Lưới 5×7 giá trị theo ô; "" = trống. Ký tự vuốt xuống và hai trang lớp 123 gắn theo ô,
 * không theo chữ: đổi chỗ chữ ở lớp chữ không kéo theo hai lớp này.
 */
data class CellGrid(val rows: List<List<String>>) {
    init {
        require(rows.size == LayoutModel.ROWS && rows.all { it.size == LayoutModel.COLS }) { "Cần lưới 5×7" }
    }

    operator fun get(r: Int, c: Int): String = rows[r][c]

    fun find(v: String): Pair<Int, Int>? {
        for (r in rows.indices) { val c = rows[r].indexOf(v); if (c >= 0) return r to c }
        return null
    }

    fun with(r: Int, c: Int, v: String): CellGrid =
        CellGrid(rows.mapIndexed { rr, row -> if (rr != r) row else row.mapIndexed { cc, x -> if (cc == c) v else x } })

    companion object {
        /** Mỗi ô lấy giá trị theo mã phím đang ở ô đó trong [layout] (cách cũ: lớp đi theo chữ). */
        fun fromCodes(layout: LayoutModel, valueOf: (String) -> String?): CellGrid =
            CellGrid(layout.rows.map { row -> row.map { code -> code?.let(valueOf) ?: "" } })
    }
}

/** Một ô trong một nhóm lưới: vuốt xuống có 1 trang, lớp 123 có 2 trang (page tính từ 0). */
data class GridCell(val page: Int, val r: Int, val c: Int)

object KeyLayers {
    /**
     * Chỗ số 1…0 khi lớp vuốt xuống / 123 còn đi theo chữ: ô của Q W R Y F H U O P L (Q=1 … L=0) trên bố cục mặc định
     * đến 0.7.0. Chỉ dùng để chuyển dữ liệu cũ.
     */
    val DIGIT_CELLS: List<Pair<String, Pair<Int, Int>>> = listOf(
        "1" to (1 to 0), "2" to (0 to 0), "3" to (0 to 1), "4" to (0 to 2), "5" to (1 to 3),
        "6" to (1 to 4), "7" to (0 to 4), "8" to (0 to 5), "9" to (1 to 6), "0" to (2 to 6),
    )

    /**
     * Ô giữ được giá trị của lớp 123: ô chữ, cùng ô ⚙ và `,` `.` — giá trị ở ba ô này (nếu có) thay phím của lớp chữ
     * khi hiện lớp 123 (phím 1/2 ở ô ⚙, `<` `>` ở trang 2), ô trống thì vẫn là phím của lớp chữ. Các phím chức năng
     * khác và phím cách lấy từ lớp chữ.
     */
    fun symbolUsable(code: String): Boolean = KeyCode.isLetter(code) || code == KeyCode.SETTINGS || code == "," || code == "."

    /** [reconcile] lớp 123: giá trị bị che dời sang ô chữ trước, chỉ khi hết ô chữ trống mới chiếm ô ⚙ / `,` `.` trống. */
    fun reconcileSymbols(layout: LayoutModel, pages: List<CellGrid>): List<CellGrid> =
        reconcile(layout, pages, ::symbolUsable, KeyCode::isLetter)

    /** Ô dùng được của lớp vuốt xuống: ô chữ và `,` `.`. */
    fun swipeUsable(code: String): Boolean = KeyCode.isLetter(code) || code == "," || code == "."

    fun swap(pages: List<CellGrid>, a: GridCell, b: GridCell): List<CellGrid> {
        val va = pages[a.page][a.r, a.c]
        val vb = pages[b.page][b.r, b.c]
        val m = pages.toMutableList()
        m[a.page] = m[a.page].with(a.r, a.c, vb)
        m[b.page] = m[b.page].with(b.r, b.c, va)
        return m
    }

    /**
     * Đưa mỗi số về [DIGIT_CELLS] trong [grid] (lớp vuốt xuống hoặc trang 1 lớp 123), đổi chỗ với giá trị đang
     * ở đó nên không giá trị nào mất hay lặp. Bỏ qua số không còn ở đâu (người dùng đã thay bằng ký tự khác:
     * giữ ký tự của họ) và ô có phím chuyển trang. Chỉ dùng để chuyển dữ liệu cũ (lớp đi theo chữ), nơi số luôn ở
     * cùng một lưới.
     */
    fun pinDigits(grid: CellGrid): CellGrid {
        var g = grid
        for ((digit, home) in DIGIT_CELLS) {
            val there = g[home.first, home.second]
            if (there == digit || there == KeyCode.PAGE) continue
            val (r, c) = g.find(digit) ?: continue
            g = g.with(home.first, home.second, digit).with(r, c, there)
        }
        return g
    }

    /**
     * Giá trị nằm dưới ô không dùng được (do kéo phím chức năng ở lớp chữ hoặc chọn profile có phím chức năng
     * ở chỗ khác) chuyển sang ô còn trống mà [target] nhận (mặc định: ô dùng được), theo thứ tự đọc, ưu tiên cùng
     * trang; hết ô như vậy thì sang ô dùng được bất kỳ còn trống. Lớp chữ chỉ đổi chữ với chữ thì không có gì thay đổi.
     */
    fun reconcile(
        layout: LayoutModel,
        pages: List<CellGrid>,
        usable: (String) -> Boolean,
        target: (String) -> Boolean = usable,
    ): List<CellGrid> {
        fun ok(r: Int, c: Int) = layout[r, c]?.let(usable) ?: false
        fun free(r: Int, c: Int) = layout[r, c]?.let(target) ?: false
        val m = pages.map { g -> g.rows.map { it.toMutableList() } }
        val hidden = ArrayList<Pair<Int, String>>()
        for (p in m.indices) for (r in 0 until LayoutModel.ROWS) for (c in 0 until LayoutModel.COLS)
            if (!ok(r, c) && m[p][r][c].isNotEmpty()) { hidden += p to m[p][r][c]; m[p][r][c] = "" }
        for ((p0, v) in hidden) {
            run place@{
                for (accepts in listOf(::free, ::ok)) for (p in listOf(p0) + m.indices.filter { it != p0 })
                    for (r in 0 until LayoutModel.ROWS) for (c in 0 until LayoutModel.COLS)
                        if (accepts(r, c) && m[p][r][c].isEmpty()) { m[p][r][c] = v; return@place }
            }
        }
        return m.map { CellGrid(it) }
    }

    /** Ký tự vuốt xuống mặc định theo phím (dữ liệu cũ cũng lưu theo phím): số dưới Q…P như phím giữ của Gboard. */
    val DEFAULT_SWIPE_BY_CODE: Map<String, String> = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5", "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        "a" to "😄", "s" to "😂", "d" to "😍", "f" to "👍", "g" to "🙏", "h" to "❤️", "j" to "😭", "k" to "🔥", "l" to "🎉",
        "z" to "@", "x" to "#", "c" to "&", "v" to "-", "b" to "/", "n" to "(", "m" to ")",
        "," to "?", "." to "!",
    )

    /** Lớp vuốt xuống mặc định: theo phím trên bố cục mặc định (số 1…0 dưới Q…P). */
    val DEFAULT_SWIPE: CellGrid = CellGrid.fromCodes(LayoutModel.DEFAULT) { DEFAULT_SWIPE_BY_CODE[it] }
}
