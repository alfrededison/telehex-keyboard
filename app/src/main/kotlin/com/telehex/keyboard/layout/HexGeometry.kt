package com.telehex.keyboard.layout

import android.graphics.Path
import android.graphics.PointF
import kotlin.math.sqrt

/**
 * Hình học lưới lục giác đều 7 cột (pointy-top).
 *
 * `W = Wu / 7.5`, `H = W·2/√3`, `P = 0.75·H`, lệch hàng `[0.5, 0, 0.5, 0, 0.5]·W`.
 * Mọi đơn vị là pixel.
 */
class HexGeometry(
    val viewWidth: Float,
    padding: Float,
    val top: Float,
    val gap: Float,
    /** Chế độ một tay: lưới thu về [scale] bề rộng và dồn sang trái/phải; lục giác vẫn đều. */
    scale: Float = 1f,
    alignRight: Boolean = false,
) {
    /** Lề trái thực tế (gồm phần dồn khi một tay). */
    val pad: Float = padding + (if (alignRight) viewWidth * (1 - scale) else 0f)
    val usable: Float = viewWidth * scale - 2 * padding
    val w: Float = usable / 7.5f
    val h: Float = w * 2f / SQRT3
    val p: Float = 0.75f * h
    val gridHeight: Float = 4 * p + h
    val offsets = floatArrayOf(0.5f, 0f, 0.5f, 0f, 0.5f)

    fun centerX(r: Int, c: Int): Float = pad + (offsets[r] + c + 0.5f) * w
    fun centerY(r: Int): Float = top + r * p + h / 2f
    fun center(r: Int, c: Int): PointF = PointF(centerX(r, c), centerY(r))

    /** Lục giác của ô (r, c), thu nhỏ để tạo khe hở, giữ nguyên tâm. */
    fun hexPath(r: Int, c: Int): Path {
        val cx = centerX(r, c); val cy = centerY(r)
        val iw = w - gap
        val ih = iw * 2f / SQRT3
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
     * Phím cách: đa giác ghép từ 3 ô giữa hàng 4, gốc ở góc trái-trên của ô đầu.
     * Cạnh trên răng cưa, hai cạnh bên thẳng, hai góc dưới vát. Đỉnh co vào [gap]/2.
     */
    fun spacePath(row: Int = 4, firstCol: Int = 2): Path {
        val ox = pad + (offsets[row] + firstCol) * w
        val oy = top + row * p
        val g = gap / 2f
        val pts = listOf(
            g to h / 4 + g, w / 2 to g, w to h / 4 + g, 1.5f * w to g, 2 * w to h / 4 + g, 2.5f * w to g, 3 * w - g to h / 4 + g,
            3 * w - g to h * 3 / 4, 2.5f * w to h - g, w / 2 to h - g, g to h * 3 / 4,
        )
        return Path().apply {
            pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(ox + x, oy + y) else lineTo(ox + x, oy + y) }
            close()
        }
    }

    /** Tâm phím cách (để đặt nhãn). */
    fun spaceCenter(row: Int = 4, firstCol: Int = 2): PointF =
        PointF(pad + (offsets[row] + firstCol + 1.5f) * w, top + row * p + h / 2f)

    /**
     * Hit-test: ô có tâm gần nhất (Voronoi của lưới lục giác chính là các lục giác).
     * Trả về (hàng, cột) hoặc null nếu ngoài lưới theo chiều dọc.
     */
    fun cellAt(x: Float, y: Float): Pair<Int, Int>? = nearestCells(x, y).firstOrNull()?.first

    /** Hai ô gần nhất kèm bình phương khoảng cách, gần trước. Rỗng nếu ngoài lưới theo chiều dọc. */
    fun nearestCells(x: Float, y: Float): List<Pair<Pair<Int, Int>, Float>> {
        if (y < top - h / 2 || y > top + gridHeight + h / 2) return emptyList()
        if (x < pad - w / 2 || x > pad + 7.5f * w + w / 2) return emptyList() // ngoài lưới (chế độ một tay)
        val found = ArrayList<Pair<Pair<Int, Int>, Float>>(9)
        val rGuess = ((y - top - h / 2) / p).toInt().coerceIn(0, 4)
        for (r in (rGuess - 1).coerceAtLeast(0)..(rGuess + 1).coerceAtMost(4)) {
            val cGuess = ((x - pad) / w - offsets[r] - 0.5f).toInt().coerceIn(0, 6)
            for (c in (cGuess - 1).coerceAtLeast(0)..(cGuess + 1).coerceAtMost(6)) {
                val dx = x - centerX(r, c); val dy = y - centerY(r)
                found.add((r to c) to dx * dx + dy * dy)
            }
        }
        return found.sortedBy { it.second }.take(2)
    }

    @Suppress("unused")
    private fun cellAtLegacy(x: Float, y: Float): Pair<Int, Int>? {
        if (y < top - h / 2 || y > top + gridHeight + h / 2) return null
        var best: Pair<Int, Int>? = null
        var bestD = Float.MAX_VALUE
        val rGuess = ((y - top - h / 2) / p).toInt().coerceIn(0, 4)
        for (r in (rGuess - 1).coerceAtLeast(0)..(rGuess + 1).coerceAtMost(4)) {
            val cGuess = ((x - pad) / w - offsets[r] - 0.5f).toInt().coerceIn(0, 6)
            for (c in (cGuess - 1).coerceAtLeast(0)..(cGuess + 1).coerceAtMost(6)) {
                val dx = x - centerX(r, c); val dy = y - centerY(r)
                val d = dx * dx + dy * dy
                if (d < bestD) { bestD = d; best = r to c }
            }
        }
        return best
    }

    companion object {
        val SQRT3 = sqrt(3f)
    }
}
