package com.telehex.layout

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Một ô gần điểm chạm: [id] do app đặt, [distance] chỉ để so sánh (bình phương khoảng cách đã chuẩn hoá). */
class TouchHit(val id: Int, val distance: Float)

/** Lưới phím để nhận chạm: Android dùng hình học lưới ([KeyGeometry]), iOS dùng [TouchCells] dựng từ khung các nút. */
interface TouchGrid {
    /** Hai ô gần [x], [y] nhất, gần trước; rỗng nếu ngoài lưới. */
    fun nearest(x: Float, y: Float): List<TouchHit>
    /** Mã chữ `a`..`z` của ô, null nếu không phải phím chữ. */
    fun letterOf(id: Int): String?
    fun centerOf(id: Int): GridPoint?
}

/** Một ô để nhận chạm: tâm, bề rộng và chiều cao ô (phím nhiều ô như phím cách thì mỗi ô một [TouchCell]). */
class TouchCell(val id: Int, val letter: String?, val cx: Float, val cy: Float, val w: Float, val h: Float, val hex: Boolean)

/**
 * [TouchGrid] từ danh sách ô, đo khoảng cách như [HexGeometry.nearestCells] / [RectGeometry.nearestCells]: lục giác theo
 * khoảng cách Euclid chia bề rộng ô (chiều dọc chia thêm độ giãn), chữ nhật theo Chebyshev chia nửa ô.
 */
class TouchCells(private val cells: List<TouchCell>) : TouchGrid {
    private val byId = cells.associateBy { it.id }

    override fun nearest(x: Float, y: Float): List<TouchHit> {
        val sorted = cells.map { it to distance(it, x, y) }.sortedBy { it.second }
        val best = sorted.firstOrNull() ?: return emptyList()
        // Ngoài lưới: xa hơn nửa ô quanh mép (như giới hạn của KeyGeometry)
        if (best.second > (if (best.first.hex) HEX_LIMIT else RECT_LIMIT)) return emptyList()
        return sorted.take(2).map { TouchHit(it.first.id, it.second) }
    }

    override fun letterOf(id: Int): String? = byId[id]?.letter
    override fun centerOf(id: Int): GridPoint? = byId[id]?.let { GridPoint(it.cx, it.cy) }

    companion object {
        private const val SQRT3_2 = 0.8660254f
        private const val HEX_LIMIT = 1.44f
        private const val RECT_LIMIT = 4f

        fun distance(c: TouchCell, x: Float, y: Float): Float = if (c.hex) {
            // Chiều cao lục giác đều = w·2/√3; giãn dọc thì h·√3/2 = w·độ giãn
            val dx = (x - c.cx) / c.w; val dy = (y - c.cy) / (c.h * SQRT3_2)
            dx * dx + dy * dy
        } else {
            val d = max(abs(x - c.cx) / (c.w / 2), abs(y - c.cy) / (c.h / 2))
            d * d
        }
    }
}

/**
 * Chọn phím từ điểm chạm và học độ lệch chạm, dùng chung cho hai app (quy tắc của Android).
 */
object TouchModel {
    /** Chạm trong chừng này bề rộng phím quanh tâm thì gõ đúng phím đó, không bù độ lệch hay vùng chạm động. */
    const val CENTRAL = 0.25f
    /** Học độ lệch: mỗi lần chạm kéo độ lệch đã học về phía độ lệch lần này chừng này phần. */
    const val LEARN_RATE = 0.08f
    /** Độ lệch một lần chạm tính tối đa chừng này bề rộng phím. */
    const val MAX_OFFSET = 0.4f
    /** Vùng chạm động: phím thứ hai cách không quá chừng này lần (bình phương khoảng cách) phím gần nhất. */
    const val DYNAMIC_RATIO = 1.8f

    /**
     * Ô được chạm. Phím chức năng, dấu câu, cách luôn theo hình vẽ; chạm rõ giữa phím chữ gõ đúng phím đó. Ở phần ngoài
     * phím chữ: bù độ lệch chung (trung bình các phím đã học) rồi độ lệch của phím gần nhất, lấy ô gần nhất; vùng chạm
     * động ([validNext]) đổi sang phím gần thứ hai nếu phím gần nhất làm âm tiết không hợp lệ mà phím kia thì không.
     * @param offsets độ lệch đã học theo chữ, cùng đơn vị với toạ độ.
     */
    fun keyAt(
        grid: TouchGrid, x: Float, y: Float, keyWidth: Float,
        offsets: Map<String, GridPoint>, validNext: ((String) -> Boolean)?,
    ): Int? {
        val physical = grid.nearest(x, y).firstOrNull()?.id ?: return null
        if (grid.letterOf(physical) == null) return physical
        val center = grid.centerOf(physical) ?: return physical
        if (hypot(x - center.x, y - center.y) <= keyWidth * CENTRAL) return physical
        var px = x; var py = y
        if (offsets.isNotEmpty()) {
            px -= offsets.values.sumOf { it.x.toDouble() }.toFloat() / offsets.size
            py -= offsets.values.sumOf { it.y.toDouble() }.toFloat() / offsets.size
            val first = grid.nearest(px, py).firstOrNull()?.id
            first?.let(grid::letterOf)?.let(offsets::get)?.let { o -> px = x - o.x; py = y - o.y }
        }
        val near = grid.nearest(px, py)
        if (near.isEmpty()) return physical
        val best = near[0].id
        val bestLetter = grid.letterOf(best) ?: return physical
        if (validNext != null && near.size == 2 && near[1].distance < near[0].distance * DYNAMIC_RATIO) {
            val second = grid.letterOf(near[1].id)
            if (second != null && !validNext(bestLetter) && validNext(second)) return near[1].id
        }
        return best
    }

    /** Độ lệch đã học [old] của một phím có tâm [center] sau một lần chạm ở [x], [y]. */
    fun learn(old: GridPoint?, x: Float, y: Float, center: GridPoint, keyWidth: Float): GridPoint {
        val limit = keyWidth * MAX_OFFSET
        val dx = (x - center.x).coerceIn(-limit, limit)
        val dy = (y - center.y).coerceIn(-limit, limit)
        val ox = old?.x ?: 0f; val oy = old?.y ?: 0f
        return GridPoint(ox + LEARN_RATE * (dx - ox), oy + LEARN_RATE * (dy - oy))
    }
}
