package com.telehex.typing

import org.junit.Assert.assertEquals
import org.junit.Test

class WordStepTest {
    @Test
    fun stepsOverWordsAndNewlines() {
        assertEquals("nam", WordStep.deletion("việt nam", 1, false))
        assertEquals("việt nam", WordStep.deletion("việt nam", 2, false))
        assertEquals("\n  ", WordStep.deletion("abc\n  ", 1, false))
        assertEquals("abc\n  ", WordStep.deletion("abc\n  ", 2, false))
    }

    @Test
    fun pastTheLeftEdgeDeletesToTheLineStart() {
        assertEquals("xin chào bạn", WordStep.deletion("dòng một\nxin chào bạn", 1, true))
        // Đã ở đầu dòng: lên đầu dòng trên, kèm dấu xuống dòng
        assertEquals("dòng một\n", WordStep.deletion("dòng một\n", 0, true))
        assertEquals("", WordStep.deletion("", 3, true))
    }
}
