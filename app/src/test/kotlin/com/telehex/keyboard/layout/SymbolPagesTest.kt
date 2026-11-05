package com.telehex.keyboard.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SymbolPagesTest {
    @Test
    fun defaultLayoutIsValid() = assertNull(LayoutModel.DEFAULT.validate())

    @Test
    fun functionKeysKeepTheirCellsOnBothPages() {
        val abc = LayoutModel.DEFAULT.rows
        for (page in listOf(SymbolPages.page1(LayoutModel.DEFAULT), SymbolPages.page2(LayoutModel.DEFAULT))) {
            for (r in abc.indices) for (c in abc[r].indices) {
                val code = abc[r][c]
                val expected = if (code == KeyCode.EMOJI) KeyCode.DIAL else code
                if (code == null || KeyCode.isFunction(code) || code == "," || code == ".") assertEquals(expected, page[r][c])
            }
        }
    }

    @Test
    fun symbolsFollowTheirLetter() {
        val (r, c) = LayoutModel.DEFAULT.find("p")!!
        assertEquals("0", SymbolPages.page1(LayoutModel.DEFAULT)[r][c])
        assertEquals("°", SymbolPages.page2(LayoutModel.DEFAULT)[r][c])
        val all = SymbolPages.page2(LayoutModel.DEFAULT).flatten().filterNotNull()
        assertEquals("mỗi ký tự trang 2 xuất hiện một lần", all.size, all.toSet().size)
    }

    @Test
    fun pageKeySitsOnTheLeftEdge() {
        for (page in listOf(SymbolPages.page1(LayoutModel.DEFAULT), SymbolPages.page2(LayoutModel.DEFAULT)))
            assertEquals(KeyCode.PAGE, page[3][0])
    }
}
