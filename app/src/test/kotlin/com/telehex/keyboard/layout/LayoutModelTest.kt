package com.telehex.keyboard.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutModelTest {
    @Test
    fun builtInProfilesAreValid() {
        for (p in Profile.BUILT_IN) {
            assertNull(p.name, p.letters.validate())
            // Lớp vuốt xuống / 123 không có giá trị nằm dưới phím chức năng
            assertTrue(p.name, p.sameLayers(p.reconciled()))
        }
    }

    @Test
    fun builtInProfilesKeepFunctionKeysAndPageKeyInPlace() {
        val fixed = listOf(KeyCode.SETTINGS, KeyCode.NUMBERS, KeyCode.EMOJI, KeyCode.ENTER, ",", ".", "z")
        for (p in Profile.BUILT_IN) for (code in fixed)
            assertEquals("${p.name}: $code", LayoutModel.DEFAULT.find(code), p.letters.find(code))
    }
}
