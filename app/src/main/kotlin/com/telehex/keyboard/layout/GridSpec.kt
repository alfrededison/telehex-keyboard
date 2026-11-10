package com.telehex.keyboard.layout

import kotlin.math.abs

/**
 * Kiểu lưới của một profile: vị trí từng ô (mép trái, theo bề rộng ô), chỗ phím cách và hình phím. Nhận ra từ số ô
 * mỗi hàng ([of]), nên JSON bố cục lưu từ trước (5 hàng × 7 ô) không cần thêm trường.
 */
enum class GridSpec(
    /** Mép trái của từng ô, từng hàng, tính theo bề rộng ô. Mỗi ô rộng đúng 1. */
    val lefts: List<List<Float>>,
    /** Bề rộng lưới tính theo ô. */
    val span: Float,
    val spaceRow: Int,
    val spaceFirstCol: Int,
    val spaceCells: Int,
    val shape: Shape,
) {
    /** Tổ ong 7 cột: 5 hàng lục giác đều lệch xen kẽ ½ ô; phím cách 3 ô giữa hàng cuối. */
    HEX7(listOf(row(7, .5f), row(7, 0f), row(7, .5f), row(7, 0f), row(7, .5f)), 7.5f, 4, 2, 3, Shape.HEX),

    /**
     * Truyền thống: 4 hàng phím chữ nhật. Q…P đủ 10 ô, A…L lệch ½ ô; hàng Z có ⇧ sát mép trái, Z…M căn giữa (Z ngay
     * dưới S) chừa ½ ô hai bên, ⌫ sát mép phải; hàng cuối 10 ô, phím cách 4 ô.
     */
    QWERTY(
        listOf(row(10, 0f), row(9, .5f), listOf(0f) + row(7, 1.5f) + listOf(9f), row(10, 0f)),
        10f, 3, 4, 4, Shape.RECT,
    );

    enum class Shape { HEX, RECT }

    val rows: Int get() = lefts.size
    val rowSizes: List<Int> get() = lefts.map { it.size }

    /** Mép trái của ô (r, c), theo bề rộng ô. */
    fun left(r: Int, c: Int): Float = lefts[r][c]

    /** Tâm ngang của ô (r, c), theo bề rộng ô. */
    fun centerX(r: Int, c: Int): Float = lefts[r][c] + 0.5f

    fun contains(r: Int, c: Int): Boolean = r in lefts.indices && c in lefts[r].indices

    fun isSpace(r: Int, c: Int): Boolean = r == spaceRow && c in spaceFirstCol until spaceFirstCol + spaceCells

    /**
     * Các ô kề của ô (r, c): hai ô cùng hàng, và các ô hàng trên/dưới có tâm lệch ngang không quá 1 ô. Lưới lệch ½ ô
     * (tổ ong) cho đúng 2 ô mỗi hàng; hàng thẳng cột (Z…M dưới A…L) cho 3 ô.
     */
    fun neighborCells(r: Int, c: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(8)
        out += r to c - 1
        out += r to c + 1
        val x = centerX(r, c)
        for (rr in listOf(r - 1, r + 1)) {
            if (rr !in lefts.indices) continue
            for (cc in lefts[rr].indices) if (abs(centerX(rr, cc) - x) <= 1.01f) out += rr to cc
        }
        return out.filter { (rr, cc) -> contains(rr, cc) }
    }

    companion object {
        /** Kiểu lưới có đúng số ô mỗi hàng [sizes], hoặc null. */
        fun of(sizes: List<Int>): GridSpec? = entries.firstOrNull { it.rowSizes == sizes }
    }
}

/** [n] ô liền nhau, ô đầu có mép trái ở [left]. */
private fun row(n: Int, left: Float): List<Float> = List(n) { left + it }
