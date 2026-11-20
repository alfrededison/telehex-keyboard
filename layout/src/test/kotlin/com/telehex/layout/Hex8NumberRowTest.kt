package com.telehex.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Hex8NumberRowTest {
    private val l = LayoutModel.HEX8_NUM

    private fun row(s: String) = s.split(' ')

    @Test
    fun numberRowSitsAboveTheEightColumnHoneycomb() {
        assertEquals(GridSpec.HEX8_NUM, l.spec)
        assertEquals(GridSpec.HEX8_NUM, CellGrid(KeyLayers.HEX8_NUM_SWIPE.rows).spec)
        assertEquals("1234567890".map { "$it" }, l.rows[0])
        assertEquals(LayoutModel.HEX8.rows, l.rows.drop(1))
        assertNull(l.validate())
        assertEquals(LayoutModel.HEX8.letterNeighbors(), l.letterNeighbors())
    }

    @Test
    fun digitsAreRegularHexagonsAndTheGridIsAsTallAsTheSevenColumnNumberRow() {
        val s = GridSpec.HEX8_NUM
        assertEquals(s.span, s.left(0, 9) + s.widths[0], 1e-4f)
        assertEquals(1f, s.stretches[0])
        assertEquals(GridSpec.HEX8.stretches[0], s.stretches[1])
        val g = HexGeometry(1080f, 21f, 0f, 8f, spec = GridSpec.HEX8_NUM)
        val g7 = HexGeometry(1080f, 21f, 0f, 8f, spec = GridSpec.HEX7_NUM)
        assertEquals(g7.gridHeight, g.gridHeight, 0.01f)
        assertEquals(g.cellW(0) * 2f / KeyGeometry.SQRT3, g.cellH(0), 0.01f)
        // Chạm gần đỉnh dưới phím số vẫn ra số, gần đỉnh trên phím chữ hàng dưới vẫn ra chữ (hai hàng khác cỡ và độ giãn)
        for (c in 0 until 10) assertEquals(0 to c, g.cellAt(g.centerX(0, c), g.centerY(0) + 0.4f * g.cellH(0)))
        for (c in 0 until 8) assertEquals(1 to c, g.cellAt(g.centerX(1, c), g.centerY(1) - 0.4f * g.cellH(1)))
    }

    @Test
    fun swipeLayerBracketsOnQToPAndNothingOnDigits() {
        val s = KeyLayers.HEX8_NUM_SWIPE
        assertEquals(List(10) { "" }, s.rows[0])
        assertEquals(row("+ ~ [ ] < { \""), s.rows[1].take(7)) // q e t y u o '
        assertEquals("=", s[2, 1]) // w
        assertEquals("}", s[2, 6]) // p
        assertEquals("@", s[4, 1]) // z: hàng số không vuốt ra @ # & ( ) nên Z…M giữ như Tổ ong
        assertEquals(")", s[4, 7]) // m
        assertEquals("?", s[5, 1]) // ,
    }

    @Test
    fun symbolPagesFillEveryCharCellWithoutDuplicates() {
        val p = KeyCode.PAGE
        for (g in SymbolPages.HEX8_NUM) for (r in l.rows.indices) for (c in l.rows[r].indices)
            if (l[r, c]?.let(KeyLayers::isCharCell) == true) assertTrue("ô $r,$c", g[r, c].isNotEmpty())
        val values = SymbolPages.HEX8_NUM.flatMap { g -> g.rows.flatten() }.filter { it.isNotEmpty() && it != p }
        assertEquals(values.size, values.toSet().size)
        val page1 = SymbolPages.render(l, SymbolPages.HEX8_NUM[0])
        assertEquals(row("1 2 3 4 5 6 7 8 9 0"), page1[0])
        assertEquals(p, page1[2][0]) // phím 1/2 ở ô ⚙
        assertEquals(SymbolPages.HEX8_NUM, KeyLayers.reconcileSymbols(l, SymbolPages.HEX8_NUM))
    }

    @Test
    fun builtInProfileIsConsistent() {
        assertEquals(Profile.HEX8_NUM, Profile.builtInFor(GridSpec.HEX8_NUM))
        assertTrue(Profile.HEX8_NUM.sameLayers(Profile.HEX8_NUM.reconciled()))
    }
}
