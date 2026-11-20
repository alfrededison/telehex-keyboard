package com.telehex.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchModelTest {
    // Hàng 1×3 phím chữ nhật a b c, ô rộng 10, cao 14
    private val row = TouchCells(listOf("a", "b", "c").mapIndexed { i, l -> TouchCell(i, l, 5f + 10 * i, 7f, 10f, 14f, hex = false) })

    @Test
    fun centralTouchesAndFunctionKeysFollowTheDrawing() {
        assertEquals(1, TouchModel.keyAt(row, 15f, 7f, 10f, mapOf("b" to GridPoint(9f, 0f)), null))
        val withFn = TouchCells(listOf(TouchCell(0, null, 5f, 7f, 10f, 14f, false), TouchCell(1, "b", 15f, 7f, 10f, 14f, false)))
        assertEquals(0, TouchModel.keyAt(withFn, 8f, 7f, 10f, mapOf("b" to GridPoint(-5f, 0f)), null))
    }

    @Test
    fun learnedOffsetsShiftTheBoundary() {
        // Người dùng hay chạm lệch phải 3: chạm ở 12 (ngoài vùng giữa của b) được hiểu là a
        val offsets = mapOf("a" to GridPoint(3f, 0f), "b" to GridPoint(3f, 0f), "c" to GridPoint(3f, 0f))
        assertEquals(0, TouchModel.keyAt(row, 12f, 7f, 10f, offsets, null))
        assertEquals(1, TouchModel.keyAt(row, 12f, 7f, 10f, emptyMap(), null))
    }

    @Test
    fun dynamicZonePrefersTheLetterThatKeepsTheSyllableValid() {
        // Sát ranh giới a|b: hai phím gần bằng nhau
        assertEquals(1, TouchModel.keyAt(row, 10.2f, 7f, 10f, emptyMap(), null))
        assertEquals(0, TouchModel.keyAt(row, 10.2f, 7f, 10f, emptyMap()) { it != "b" })
        // Rõ về phía b (chênh quá 1,8 lần): giữ b
        assertEquals(1, TouchModel.keyAt(row, 11f, 7f, 10f, emptyMap()) { it != "b" })
    }

    @Test
    fun outsideTheGridIsNothingAndLearningIsClamped() {
        assertEquals(null, TouchModel.keyAt(row, 5f, 60f, 10f, emptyMap(), null))
        val o = TouchModel.learn(null, 100f, 7f, GridPoint(5f, 7f), 10f)
        assertEquals(0.08f * 4f, o.x, 1e-4f)
        assertTrue(TouchModel.learn(o, 5f, 7f, GridPoint(5f, 7f), 10f).x < o.x)
    }

    @Test
    fun hexCellsMeasureLikeTheGeometry() {
        val g = HexGeometry(1080f, 21f, 0f, 8f)
        val cells = (0 until 5).flatMap { r -> (0 until 7).map { c ->
            val p = g.center(r, c); TouchCell(r * 10 + c, "x", p.x, p.y, g.cellW(r), g.cellH(r), true)
        } }
        val grid = TouchCells(cells)
        for ((x, y) in listOf(300f to 200f, 520f to 133f, 700f to 400f)) {
            val expected = g.nearestCells(x, y).first().first
            assertEquals(expected.first * 10 + expected.second, grid.nearest(x, y).first().id)
        }
    }
}
