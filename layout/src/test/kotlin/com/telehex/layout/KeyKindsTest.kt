package com.telehex.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyKindsTest {
    private fun kind(code: String?) = KeyKinds.ofLetterLayer(code, "sfrxj")

    @Test
    fun letterLayerGroups() {
        assertEquals(KeyKind.TONE, kind("s"))
        assertEquals(KeyKind.VOWEL, kind("w"))
        assertEquals(KeyKind.CONSONANT, kind("b"))
        assertEquals(KeyKind.CONSONANT, kind("7"))
        assertEquals(KeyKind.SHIFT_BACK, kind(KeyCode.SHIFT))
        assertEquals(KeyKind.SHIFT_BACK, kind(KeyCode.BACKSPACE))
        assertEquals(KeyKind.ENTER, kind(KeyCode.ENTER))
        assertEquals(KeyKind.SPACE, kind(null))
        for (code in listOf(KeyCode.NUMBERS, KeyCode.EMOJI, KeyCode.SETTINGS, ",", "?")) assertEquals(code, KeyKind.FUNCTION, kind(code))
        // "l" chỉ là phím dấu khi bật L làm dấu huyền
        assertEquals(KeyKind.CONSONANT, kind("l"))
        assertEquals(KeyKind.TONE, KeyKinds.ofLetterLayer("l", "sfrxjl"))
    }

    @Test
    fun symbolAndDialpadGroups() {
        assertEquals(KeyKind.FUNCTION, KeyKinds.ofSymbolValue(KeyCode.PAGE))
        assertEquals(KeyKind.SYMBOL, KeyKinds.ofSymbolValue("?"))
        assertEquals(KeyKind.SHIFT_BACK, KeyKinds.ofDialpad(KeyCode.BACKSPACE))
        assertEquals(KeyKind.FUNCTION, KeyKinds.ofDialpad("-"))
        assertEquals(KeyKind.CONSONANT, KeyKinds.ofDialpad("."))
        assertEquals(KeyKind.CONSONANT, KeyKinds.ofDialpad("5"))
    }
}
