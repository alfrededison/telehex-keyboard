package com.telehex.keyboard.ime

import com.telehex.keyboard.ime.SpaceGestures.Companion.lineStart
import com.telehex.keyboard.ime.SpaceGestures.Companion.wordStart
import org.junit.Assert.assertEquals
import org.junit.Test

class SpaceGesturesTest {
    @Test
    fun wordStepsCrossNewlines() {
        val t = "một hai\nba "
        assertEquals("ba ", t.substring(wordStart(t, 1)))
        assertEquals("\nba ", t.substring(wordStart(t, 2)))
        assertEquals("hai\nba ", t.substring(wordStart(t, 3)))
        assertEquals(t, t.substring(wordStart(t, 4)))
        assertEquals(0, wordStart(t, 9))
    }

    @Test
    fun wordStepAtLineStartTakesTheNewline() {
        val t = "một hai\n"
        assertEquals("\n", t.substring(wordStart(t, 1)))
        assertEquals("hai\n", t.substring(wordStart(t, 2)))
    }

    @Test
    fun pastLeftEdgeGoesToLineStartThenLineAbove() {
        val t = "một\nhai ba"
        assertEquals("hai ba", t.substring(lineStart(t)))
        val atStart = "một\nhai\n"
        assertEquals("hai\n", atStart.substring(lineStart(atStart)))
        assertEquals("\n", "\n".substring(lineStart("\n")))
        assertEquals(0, lineStart(""))
    }
}
