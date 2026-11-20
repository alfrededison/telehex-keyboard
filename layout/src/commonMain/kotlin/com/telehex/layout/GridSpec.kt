package com.telehex.layout

import kotlin.math.abs

/**
 * Kiểu lưới của một profile: vị trí từng ô (mép trái và bề rộng, theo bề rộng ô), hàng và số ô của phím cách, hình phím. Nhận ra
 * từ số ô mỗi hàng ([of]), nên JSON bố cục lưu từ trước (5 hàng × 7 ô) không cần thêm trường. Phím cách nằm ở đâu
 * trong hàng cuối là do lớp chữ (các ô `null`), để profile riêng lưu từ trước khi bố cục có sẵn dời phím cách vẫn dùng
 * được.
 */
enum class GridSpec(
    /** Mép trái của từng ô, từng hàng, tính theo bề rộng ô. Mỗi ô rộng đúng 1. */
    val lefts: List<List<Float>>,
    /** Bề rộng lưới tính theo ô. */
    val span: Float,
    val spaceRow: Int,
    val spaceCells: Int,
    val shape: Shape,
    /** Bề rộng ô của từng hàng, theo bề rộng ô thường (hàng số tổ ong 0,75). Mặc định mọi hàng 1. */
    val widths: List<Float> = List(lefts.size) { 1f },
    /** Độ giãn dọc của lục giác từng hàng: chiều cao = bề rộng · 2/√3 · độ giãn. 1 = lục giác đều. */
    val stretches: List<Float> = List(lefts.size) { 1f },
) {
    /** Tổ ong 7 cột: 5 hàng lục giác đều lệch xen kẽ ½ ô; phím cách 3 ô giữa hàng cuối. */
    HEX7(HEX_ROWS, 7.5f, 4, 3, Shape.HEX),

    /**
     * Tổ ong có hàng số: hàng 1…0 gồm 10 lục giác đều nhỏ (rộng 0,75 ô, vừa đúng bề ngang 7,5 ô) trên [HEX7]. Lục giác
     * nhỏ không lồng được vào hàng dưới nên hai hàng xếp chồng, không gối 1/4 như các hàng tổ ong.
     */
    HEX7_NUM(listOf(row(10, 0f, .75f)) + HEX_ROWS, 7.5f, 5, 3, Shape.HEX, listOf(.75f) + List(5) { 1f }),

    /**
     * Tổ ong 8 cột: 5 hàng 8 ô lệch xen kẽ ½ ô. Ô hẹp hơn [HEX7] (bề ngang 8,5 ô thay 7,5) nên lục giác giãn dọc 8,5/7,5
     * để lưới cao bằng [HEX7] và phím cao bằng phím tổ ong 7 cột.
     */
    HEX8(HEX8_ROWS, 8.5f, 4, 3, Shape.HEX, stretches = List(5) { HEX8_STRETCH }),

    /**
     * Tổ ong 8 cột có hàng số: hàng 1…0 gồm 10 lục giác đều (rộng 0,85 ô, vừa bề ngang 8,5 ô) xếp chồng trên [HEX8].
     * Hàng số không giãn, hàng chữ giãn như [HEX8], nên lưới cao bằng [HEX7_NUM].
     */
    HEX8_NUM(
        listOf(row(10, 0f, .85f)) + HEX8_ROWS, 8.5f, 5, 3, Shape.HEX,
        listOf(.85f) + List(5) { 1f }, listOf(1f) + List(5) { HEX8_STRETCH },
    ),

    /**
     * Truyền thống: 4 hàng phím chữ nhật. Q…P đủ 10 ô, A…L lệch ½ ô; hàng Z có ⇧ sát mép trái, Z…M căn giữa (Z ngay
     * dưới S) chừa ½ ô hai bên, ⌫ sát mép phải; hàng cuối 10 ô, phím cách 4 ô.
     */
    QWERTY(listOf(row(10, 0f), row(9, .5f), ZROW, row(10, 0f)), 10f, 3, 4, Shape.RECT),

    /** Truyền thống có hàng số: thêm hàng 1…0 (10 ô) trên [QWERTY]. */
    QWERTY_NUM(listOf(row(10, 0f), row(10, 0f), row(9, .5f), ZROW, row(10, 0f)), 10f, 4, 4, Shape.RECT);

    enum class Shape { HEX, RECT }

    /** Tên kiểu lưới hiện dưới tên profile (cả hai app). */
    val title: String
        get() = when (this) {
            HEX7 -> "Tổ ong 7 cột"
            HEX7_NUM -> "Tổ ong 7 cột, có hàng số"
            HEX8 -> "Tổ ong 8 cột, có ⇧ ⌫"
            HEX8_NUM -> "Tổ ong 8 cột, có ⇧ ⌫ và hàng số"
            QWERTY -> "QWERTY 10 cột"
            QWERTY_NUM -> "QWERTY 10 cột, có hàng số"
        }

    val rows: Int get() = lefts.size
    val rowSizes: List<Int> get() = lefts.map { it.size }

    /** Mép trái của ô (r, c), theo bề rộng ô. */
    fun left(r: Int, c: Int): Float = lefts[r][c]

    /** Tâm ngang của ô (r, c), theo bề rộng ô. */
    fun centerX(r: Int, c: Int): Float = lefts[r][c] + widths[r] / 2

    fun contains(r: Int, c: Int): Boolean = r in lefts.indices && c in lefts[r].indices

    /**
     * Các ô kề của ô (r, c): hai ô cùng hàng, và các ô hàng trên/dưới có tâm lệch ngang không quá nửa tổng bề rộng hai ô
     * (1 ô khi hai hàng cùng cỡ). Lưới lệch ½ ô (tổ ong) cho đúng 2 ô mỗi hàng; hàng thẳng cột (Z…M dưới A…L) cho 3 ô.
     */
    fun neighborCells(r: Int, c: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>(8)
        out += r to c - 1
        out += r to c + 1
        val x = centerX(r, c)
        for (rr in listOf(r - 1, r + 1)) {
            if (rr !in lefts.indices) continue
            val reach = (widths[r] + widths[rr]) / 2 + 0.01f
            for (cc in lefts[rr].indices) if (abs(centerX(rr, cc) - x) <= reach) out += rr to cc
        }
        return out.filter { (rr, cc) -> contains(rr, cc) }
    }

    companion object {
        /** Kiểu lưới có đúng số ô mỗi hàng [sizes], hoặc null. */
        fun of(sizes: List<Int>): GridSpec? = entries.firstOrNull { it.rowSizes == sizes }
    }
}

/** [n] ô liền nhau rộng [width], ô đầu có mép trái ở [left]. */
private fun row(n: Int, left: Float, width: Float = 1f): List<Float> = List(n) { left + it * width }

/** 5 hàng tổ ong 8 cột lệch xen kẽ ½ ô, hàng đầu lệch phải. */
private val HEX8_ROWS: List<List<Float>> = List(5) { row(8, if (it % 2 == 0) .5f else 0f) }

/** Độ giãn dọc của hàng chữ tổ ong 8 cột: bề ngang 8,5 ô thay 7,5 nên giãn 8,5/7,5 để phím cao bằng tổ ong 7 cột. */
private const val HEX8_STRETCH = 8.5f / 7.5f

/** 5 hàng tổ ong 7 cột lệch xen kẽ ½ ô, hàng đầu lệch phải. */
private val HEX_ROWS: List<List<Float>> = listOf(row(7, .5f), row(7, 0f), row(7, .5f), row(7, 0f), row(7, .5f))

/** Hàng Z của lưới truyền thống: ⇧ sát mép trái, Z…M căn giữa (Z ngay dưới S) chừa ½ ô hai bên, ⌫ sát mép phải. */
private val ZROW: List<Float> = listOf(0f) + row(7, 1.5f) + listOf(9f)
