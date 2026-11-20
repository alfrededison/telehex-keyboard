package com.telehex.keyboard.layout

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Hình học một lưới phím theo [GridSpec]: vị trí, hình vẽ và hit-test của từng ô. Mọi đơn vị là pixel.
 * Một phím có thể chiếm nhiều ô liền nhau trên một hàng (phím cách).
 */
interface KeyGeometry {
    val spec: GridSpec
    /** Bề rộng một ô. */
    val w: Float
    /** Lề trái thực tế (gồm phần dồn khi một tay). */
    val pad: Float
    val gridHeight: Float

    fun center(r: Int, c: Int): PointF

    /** Tâm của phím chiếm [n] ô từ ô (r, c) sang phải. */
    fun keyCenter(r: Int, c: Int, n: Int): PointF

    /** Hình vẽ của phím chiếm [n] ô từ ô (r, c) sang phải, đã chừa khe hở. */
    fun keyPath(r: Int, c: Int, n: Int): Path

    /** Ô chứa điểm (x, y), hoặc null nếu ngoài lưới. */
    fun cellAt(x: Float, y: Float): Pair<Int, Int>? = nearestCells(x, y).firstOrNull()?.first

    /** Hai ô gần nhất kèm khoảng cách (bình phương, chỉ để so sánh), gần trước. Rỗng nếu ngoài lưới. */
    fun nearestCells(x: Float, y: Float): List<Pair<Pair<Int, Int>, Float>>

    companion object {
        val SQRT3 = sqrt(3f)

        /**
         * Hình học cho [spec]. [scale] < 1: chế độ một tay, lưới thu về [scale] bề rộng và dồn sang trái/phải.
         * [maxRowPitch]: trần bước hàng của phím chữ nhật (lục giác đều thì bước hàng theo bề rộng).
         */
        fun of(
            spec: GridSpec, viewWidth: Float, padding: Float, top: Float, gap: Float, maxRowPitch: Float,
            scale: Float = 1f, alignRight: Boolean = false,
        ): KeyGeometry = when (spec.shape) {
            GridSpec.Shape.HEX -> HexGeometry(viewWidth, padding, top, gap, scale, alignRight, spec)
            GridSpec.Shape.RECT -> RectGeometry(viewWidth, padding, top, gap, maxRowPitch, scale, alignRight, spec)
        }
    }
}

/**
 * Hình học lưới lục giác đều (pointy-top). Với lưới 7 cột: `W = Wu / 7.5`, `H = W·2/√3`, `P = 0.75·H`,
 * lệch hàng `[0.5, 0, 0.5, 0, 0.5]·W`. Hàng có ô nhỏ hơn ([GridSpec.widths], hàng số) vẫn là lục giác đều, cỡ theo
 * bề rộng ô của hàng; hai hàng khác cỡ xếp chồng không gối nhau.
 */
class HexGeometry(
    val viewWidth: Float,
    padding: Float,
    val top: Float,
    val gap: Float,
    /** Chế độ một tay: lưới thu về [scale] bề rộng và dồn sang trái/phải; lục giác vẫn đều. */
    scale: Float = 1f,
    alignRight: Boolean = false,
    override val spec: GridSpec = GridSpec.HEX7,
) : KeyGeometry {
    override val pad: Float = padding + (if (alignRight) viewWidth * (1 - scale) else 0f)
    val usable: Float = viewWidth * scale - 2 * padding
    override val w: Float = usable / spec.span
    /** Chiều cao lục giác cỡ thường. */
    val h: Float = w * 2f / KeyGeometry.SQRT3
    /** Bước hàng giữa hai hàng cùng cỡ thường. */
    val p: Float = 0.75f * h

    /** Bề rộng ô của hàng [r]. */
    fun cellW(r: Int): Float = w * spec.widths[r]

    /** Chiều cao lục giác của hàng [r]. */
    fun cellH(r: Int): Float = cellW(r) * 2f / KeyGeometry.SQRT3

    /** Mép trên (đỉnh lục giác) của từng hàng: hàng cùng cỡ gối 1/4 chiều cao, khác cỡ thì xếp chồng. */
    private val rowTops: FloatArray = FloatArray(spec.rows).also { t ->
        t[0] = top
        for (r in 1 until spec.rows) {
            val prev = cellH(r - 1)
            t[r] = t[r - 1] + if (spec.widths[r] == spec.widths[r - 1]) 0.75f * prev else prev
        }
    }

    override val gridHeight: Float = rowTops.last() + cellH(spec.rows - 1) - top

    fun centerX(r: Int, c: Int): Float = pad + spec.centerX(r, c) * w
    fun centerY(r: Int): Float = rowTops[r] + cellH(r) / 2f
    override fun center(r: Int, c: Int): PointF = PointF(centerX(r, c), centerY(r))

    override fun keyCenter(r: Int, c: Int, n: Int): PointF =
        PointF(pad + spec.left(r, c) * w + n * cellW(r) / 2f, centerY(r))

    /** Lục giác của ô (r, c), thu nhỏ để tạo khe hở, giữ nguyên tâm. */
    fun hexPath(r: Int, c: Int): Path {
        val cx = centerX(r, c); val cy = centerY(r)
        val iw = cellW(r) - gap
        val ih = iw * 2f / KeyGeometry.SQRT3
        return Path().apply {
            moveTo(cx, cy - ih / 2)
            lineTo(cx + iw / 2, cy - ih / 4)
            lineTo(cx + iw / 2, cy + ih / 4)
            lineTo(cx, cy + ih / 2)
            lineTo(cx - iw / 2, cy + ih / 4)
            lineTo(cx - iw / 2, cy - ih / 4)
            close()
        }
    }

    /**
     * Một ô là lục giác. Nhiều ô (phím cách): đa giác ghép, cạnh trên răng cưa, hai cạnh bên thẳng, hai góc dưới vát.
     * Đỉnh co vào [gap]/2.
     */
    override fun keyPath(r: Int, c: Int, n: Int): Path {
        if (n == 1) return hexPath(r, c)
        val ox = pad + spec.left(r, c) * w
        val oy = rowTops[r]
        val w = cellW(r)
        val h = cellH(r)
        val g = gap / 2f
        val pts = ArrayList<Pair<Float, Float>>(2 * n + 5)
        pts += g to h / 4 + g
        for (i in 0 until n) {
            pts += (i + 0.5f) * w to g
            pts += (if (i == n - 1) n * w - g else (i + 1) * w) to h / 4 + g
        }
        pts += n * w - g to h * 3 / 4
        pts += n * w - w / 2 to h - g
        pts += w / 2 to h - g
        pts += g to h * 3 / 4
        return Path().apply {
            pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(ox + x, oy + y) else lineTo(ox + x, oy + y) }
            close()
        }
    }

    /**
     * Ô có tâm gần nhất, khoảng cách chia theo bề rộng ô của hàng (rồi nhân lại [w] để lưới cùng cỡ giữ số đo cũ):
     * lưới cùng cỡ thì Voronoi chính là các lục giác; giữa hàng số nhỏ và hàng chữ, ranh giới rơi đúng chỗ hai hàng
     * xếp chồng.
     */
    override fun nearestCells(x: Float, y: Float): List<Pair<Pair<Int, Int>, Float>> {
        if (y < top - h / 2 || y > top + gridHeight + h / 2) return emptyList()
        if (x < pad - w / 2 || x > pad + spec.span * w + w / 2) return emptyList() // ngoài lưới (chế độ một tay)
        val found = ArrayList<Pair<Pair<Int, Int>, Float>>(9)
        for (r in 0 until spec.rows) {
            if (abs(y - centerY(r)) > 1.5f * h) continue
            val cols = spec.rowSizes[r]
            val k = w / cellW(r)
            // Hàng tổ ong lệch đều: đoán cột từ mép trái của ô đầu hàng
            val cGuess = ((x - pad) / cellW(r) - spec.left(r, 0) / spec.widths[r] - 0.5f).toInt().coerceIn(0, cols - 1)
            for (c in (cGuess - 1).coerceAtLeast(0)..(cGuess + 1).coerceAtMost(cols - 1)) {
                val dx = (x - centerX(r, c)) * k; val dy = (y - centerY(r)) * k
                found.add((r to c) to dx * dx + dy * dy)
            }
        }
        return found.sortedBy { it.second }.take(2)
    }
}

/**
 * Hình học phím chữ nhật bo góc kiểu Gboard trên lưới lệch ½ ô ([GridSpec.QWERTY]). Bước hàng cao hơn bề rộng ô
 * (khoảng 1,45 lần, như Gboard), có trần [maxRowPitch] để màn ngang không quá cao.
 */
class RectGeometry(
    val viewWidth: Float,
    padding: Float,
    val top: Float,
    val gap: Float,
    maxRowPitch: Float,
    scale: Float = 1f,
    alignRight: Boolean = false,
    override val spec: GridSpec = GridSpec.QWERTY,
) : KeyGeometry {
    override val pad: Float = padding + (if (alignRight) viewWidth * (1 - scale) else 0f)
    val usable: Float = viewWidth * scale - 2 * padding
    override val w: Float = usable / spec.span
    /** Bước hàng. */
    val p: Float = min(w * ROW_RATIO, maxRowPitch)
    override val gridHeight: Float = spec.rows * p

    /** Khe giữa hai phím: ngang ~5,5dp, dọc ~11dp như Gboard (với [gap] 3dp). */
    private val insetX = gap * 0.9f
    private val insetY = gap * 1.8f
    private val radius = gap * 2f

    fun centerX(r: Int, c: Int): Float = pad + spec.centerX(r, c) * w
    fun centerY(r: Int): Float = top + r * p + p / 2f
    override fun center(r: Int, c: Int): PointF = PointF(centerX(r, c), centerY(r))

    override fun keyCenter(r: Int, c: Int, n: Int): PointF = PointF(pad + (spec.left(r, c) + n / 2f) * w, centerY(r))

    override fun keyPath(r: Int, c: Int, n: Int): Path {
        val left = pad + spec.left(r, c) * w + insetX
        val rect = RectF(left, top + r * p + insetY, left + n * w - 2 * insetX, top + (r + 1) * p - insetY)
        return Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
    }

    /**
     * Ô chứa điểm đứng đầu: khoảng cách đo theo nửa bề rộng / nửa bước hàng (Chebyshev), nên ô chứa điểm luôn < 1
     * còn mọi ô khác ≥ 1. Chạm vào khoảng trống cạnh ⇧ / ⌫ thì nhận phím gần nhất.
     */
    override fun nearestCells(x: Float, y: Float): List<Pair<Pair<Int, Int>, Float>> {
        val last = spec.rows - 1
        if (y < top - p / 2 || y > top + gridHeight + p / 2) return emptyList()
        if (x < pad - w / 2 || x > pad + spec.span * w + w / 2) return emptyList()
        val found = ArrayList<Pair<Pair<Int, Int>, Float>>(9)
        val rGuess = floor((y - top) / p).toInt().coerceIn(0, last)
        for (r in (rGuess - 1).coerceAtLeast(0)..(rGuess + 1).coerceAtMost(last)) {
            // Hàng không lệch đều (hàng Z có khoảng trống hai bên): xét mọi ô của hàng, tối đa 10
            for (c in 0 until spec.rowSizes[r]) {
                val d = max(abs(x - centerX(r, c)) / (w / 2), abs(y - centerY(r)) / (p / 2))
                found.add((r to c) to d * d)
            }
        }
        return found.sortedBy { it.second }.take(2)
    }

    companion object {
        /** Bước hàng / bước phím của Gboard đo trên máy 411dp: 58,7dp / 40,8dp. */
        const val ROW_RATIO = 1.44f
    }
}
