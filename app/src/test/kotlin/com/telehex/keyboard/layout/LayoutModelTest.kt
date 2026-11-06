package com.telehex.keyboard.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LayoutModelTest {
    @Test
    fun builtInPresetsAreValid() {
        for ((name, l) in LayoutModel.BUILT_IN) assertNull(name, l.validate())
    }

    @Test
    fun builtInPresetsKeepFunctionKeysAndPageKeyInPlace() {
        val fixed = listOf(KeyCode.SETTINGS, KeyCode.NUMBERS, KeyCode.EMOJI, KeyCode.ENTER, ",", ".", "z")
        for ((name, l) in LayoutModel.BUILT_IN) for (code in fixed)
            assertEquals("$name: $code", LayoutModel.DEFAULT.find(code), l.find(code))
    }
}
