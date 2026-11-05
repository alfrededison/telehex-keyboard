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
                if (code == null || KeyCode.isFunction(code) || code == "," || code == ".") assertEquals(code, page[r][c])
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
    fun oldDefaultLayoutKeepsItsOldPage2() {
        val oldDefault = LayoutModel(listOf(
            listOf("w", "r", "y", KeyCode.EMOJI, KeyCode.NUMBERS, "o", "p"),
            listOf("q", "e", "t", KeyCode.SETTINGS, "u", "i", "h"),
            listOf("a", "s", "d", ",", "f", "g", "j"),
            listOf("z", "c", "b", ".", "n", "m", "k"),
            listOf("x", "v", null, null, null, "l", KeyCode.ENTER),
        ))
        val oldPage2 = listOf(
            listOf("~", "`", "|", KeyCode.EMOJI, KeyCode.NUMBERS, "^", "°"),
            listOf("[", "]", "{", KeyCode.SETTINGS, "}", "<", ">"),
            listOf("_", "\"", "'", ",", ";", "\\", "•"),
            listOf("€", "£", "¥", ".", "₫", "×", "÷"),
            listOf(KeyCode.PAGE, "…", null, null, null, "©", KeyCode.ENTER),
        )
        assertEquals(oldPage2, SymbolPages.page2(oldDefault))
    }
}
