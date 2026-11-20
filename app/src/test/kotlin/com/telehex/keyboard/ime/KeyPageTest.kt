package com.telehex.keyboard.ime

import com.telehex.layout.Profile
import com.telehex.telex.TelexConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyPageTest {
    private fun keys(p: Profile, mode: Mode, page: Int = 1) =
        KeyPage.build(p.letters, mode, page, TelexConfig(), p.swipe, p.symbols, "↵").associateBy { it.cells.first() }

    @Test
    fun symbolPagePunctuationStaysSymbolKeys() {
        // Trang 123 của mọi bố cục có ' ! ? ở ô chữ: vẫn là phím ký hiệu (kéo đổi chỗ được), không thành phím dấu câu
        for (p in Profile.BUILT_IN) {
            val symbols = keys(p, Mode.SYMBOLS)
            for ((cell, k) in symbols) if (k.code in setOf("'", "!", "?") && p.letters[cell.first, cell.second] != k.code)
                assertEquals("${p.name} $cell", KeyKind.SYMBOL, k.kind)
        }
    }

    @Test
    fun letterLayerPunctuationKeysAreFunctionKeysWithSwipeHints() {
        val abc = keys(Profile.HEX8, Mode.ABC)
        for (code in listOf(",", ".", "?", "!", "'")) {
            val k = abc.values.single { it.code == code }
            assertEquals(code, KeyKind.FUNCTION, k.kind)
        }
        assertEquals("\"", abc.values.single { it.code == "'" }.hint)
        // Ô `?` trống ở trang 2 của lớp 123 hiện lại dấu của lớp chữ, giữ kiểu phím dấu câu
        val q = Profile.HEX8.letters.find("?")!!
        assertEquals(KeyKind.FUNCTION, keys(Profile.HEX8, Mode.SYMBOLS, page = 2)[q]!!.kind)
        assertEquals(KeyKind.SYMBOL, keys(Profile.HEX8, Mode.SYMBOLS)[q]!!.kind) // trang 1: `…`
    }
}
