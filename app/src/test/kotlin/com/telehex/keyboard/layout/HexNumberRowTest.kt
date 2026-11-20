package com.telehex.keyboard.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexNumberRowTest {
    private val n = LayoutModel.HEX7_NUM

    private fun row(s: String) = s.split(' ')

    @Test
    fun numberRowSitsAboveTheDefaultHoneycomb() {
        assertEquals(GridSpec.HEX7_NUM, n.spec)
        assertEquals(GridSpec.HEX7_NUM, CellGrid(KeyLayers.HEX7_NUM_SWIPE.rows).spec)
        assertEquals("1234567890".map { "$it" }, n.rows[0])
        assertEquals(LayoutModel.DEFAULT.rows, n.rows.drop(1))
        assertNull(n.validate())
        assertEquals(5, GridSpec.HEX7_NUM.spaceRow)
        // Số không kề chữ khi sửa gõ sai; chữ vẫn kề nhau như lưới mặc định
        assertEquals(LayoutModel.DEFAULT.letterNeighbors(), n.letterNeighbors())
    }

    @Test
    fun tenSmallCellsSpanTheHoneycombWidth() {
        val s = GridSpec.HEX7_NUM
        assertEquals(.75f, s.widths[0])
        assertEquals(0f, s.left(0, 0))
        assertEquals(s.span, s.left(0, 9) + s.widths[0], 1e-4f)
        assertEquals(.375f, s.centerX(0, 0), 1e-4f)
        // Hàng chữ giữ nguyên toạ độ của lưới mặc định
        for (r in 0 until 5) for (c in 0 until 7) assertEquals(GridSpec.HEX7.centerX(r, c), s.centerX(r + 1, c))
    }

    @Test
    fun numberRowHasNoSwipeCharacters() {
        val s = KeyLayers.HEX7_NUM_SWIPE
        // Lục giác nhỏ không đủ chỗ cho ký tự phụ
        assertEquals(List(10) { "" }, s.rows[0])
        assertEquals("+", s[1, 0]) // q
        assertEquals("?", s[5, 1]) // ,
        assertTrue(n.rows[0].all { KeyLayers.swipeUsable(it!!) && KeyLayers.symbolUsable(it) })
    }

    @Test
    fun symbolPagesFillEveryCharCellWithoutDuplicates() {
        val p = KeyCode.PAGE
        val page1 = SymbolPages.render(n, SymbolPages.HEX7_NUM[0])
        assertEquals(row("1 2 3 4 5 6 7 8 9 0"), page1[0])
        assertEquals(p, page1[2][0]) // phím 1/2 ở ô ⚙
        for (g in SymbolPages.HEX7_NUM) for (r in n.rows.indices) for (c in n.rows[r].indices)
            if (n[r, c]?.let(KeyLayers::isCharCell) == true) assertTrue("ô $r,$c", g[r, c].isNotEmpty())
        val values = SymbolPages.HEX7_NUM.flatMap { g -> g.rows.flatten() }.filter { it.isNotEmpty() && it != p }
        assertEquals(values.size, values.toSet().size)
        // Cùng bộ ký tự với bố cục truyền thống có hàng số
        val qwerty = SymbolPages.QWERTY_NUM.flatMap { g -> g.rows.flatten() }.filter { it.isNotEmpty() && it != p }
        assertEquals(qwerty.toSet(), values.toSet())
        assertEquals(SymbolPages.HEX7_NUM, KeyLayers.reconcileSymbols(n, SymbolPages.HEX7_NUM))
    }

    @Test
    fun builtInProfileIsConsistent() {
        assertEquals(Profile.HEX7_NUM, Profile.builtInFor(GridSpec.HEX7_NUM))
        assertTrue(Profile.HEX7_NUM.sameLayers(Profile.HEX7_NUM.reconciled()))
        assertEquals(Profile.BUILT_IN.size, Profile.BUILT_IN.map { it.name }.toSet().size)
    }
}
