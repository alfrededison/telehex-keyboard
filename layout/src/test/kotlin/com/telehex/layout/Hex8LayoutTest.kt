package com.telehex.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Hex8LayoutTest {
    private val l = LayoutModel.HEX8

    @Test
    fun keepsTheDefaultLettersAndAddsShiftBackspaceAndPunctuation() {
        assertEquals(GridSpec.HEX8, l.spec)
        assertEquals(GridSpec.HEX8, CellGrid(KeyLayers.HEX8_SWIPE.rows).spec)
        assertNull(l.validate())
        // Hàng Q, W, A giữ đúng ô của bố cục mặc định
        for (r in 0..2) for (c in 0 until 7) {
            val d = LayoutModel.DEFAULT[r, c]
            if (d != null && KeyCode.isLetter(d)) assertEquals(d, l[r, c])
        }
        assertEquals(KeyCode.SHIFT, l[3, 0])
        assertEquals("zxcvbnm".map { "$it" }, l.rows[3].drop(1))
        assertEquals(KeyCode.BACKSPACE, l[2, 7])
        for (p in listOf("?", "!", "'")) assertTrue(p, l.find(p) != null)
    }

    @Test
    fun hexagonsStretchSoTheGridIsAsTallAsSevenColumns() {
        val h7 = HexGeometry(1080f, 21f, 0f, 8f, spec = GridSpec.HEX7)
        val h8 = HexGeometry(1080f, 21f, 0f, 8f, spec = GridSpec.HEX8)
        assertEquals(h7.gridHeight, h8.gridHeight, 0.01f)
        assertEquals(h7.h, h8.h, 0.01f)
        assertTrue(h8.w < h7.w)
    }

    @Test
    fun punctuationKeysHoldSwipeAndSymbolValues() {
        for (p in KeyCode.PUNCT) assertTrue(KeyLayers.swipeUsable(p) && KeyLayers.symbolUsable(p))
        val s = KeyLayers.HEX8_SWIPE
        assertEquals("1", s[0, 0]) // q
        assertEquals("\"", s[0, 6]) // '
        assertEquals("@", s[3, 1]) // z
        assertEquals("", s[1, 7]) // !
    }

    @Test
    fun symbolPagesFillEveryLetterCellWithoutDuplicates() {
        val p = KeyCode.PAGE
        for (g in SymbolPages.HEX8) for (r in l.rows.indices) for (c in l.rows[r].indices)
            if (l[r, c]?.let(KeyLayers::isCharCell) == true) assertTrue("ô $r,$c", g[r, c].isNotEmpty())
        val values = SymbolPages.HEX8.flatMap { g -> g.rows.flatten() }.filter { it.isNotEmpty() && it != p }
        assertEquals(values.size, values.toSet().size)
        assertEquals(p, SymbolPages.render(l, SymbolPages.HEX8[0])[1][0]) // phím 1/2 ở ô ⚙
        assertEquals("…", SymbolPages.render(l, SymbolPages.HEX8[0])[4][6])
        assertEquals("?", SymbolPages.render(l, SymbolPages.HEX8[1])[4][6]) // ô `?` trống giữ dấu của lớp chữ
        assertEquals(SymbolPages.HEX8, KeyLayers.reconcileSymbols(l, SymbolPages.HEX8))
    }

    @Test
    fun noSymbolPageShowsTheSameCharacterTwice() {
        // Kể cả ô trống hiện lại dấu của lớp chữ (`,` `.` `?`…)
        for (p in Profile.BUILT_IN) for ((i, page) in p.symbols.withIndex()) {
            val shown = SymbolPages.render(p.letters, page).flatten().filterNotNull().filter { !KeyCode.isFunction(it) }
            assertEquals("${p.name} trang ${i + 1}", shown.size, shown.toSet().size)
        }
    }

    @Test
    fun builtInProfileIsConsistent() {
        assertEquals(Profile.HEX8, Profile.builtInFor(GridSpec.HEX8))
        assertTrue(Profile.HEX8.sameLayers(Profile.HEX8.reconciled()))
    }
}
