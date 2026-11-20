package com.telehex.typing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlideRuleTest {
    @Test
    fun needsThreeLettersAndALongEnoughTrace() {
        assertTrue(GlideRule.isGlide(3, 1.5f))
        assertFalse(GlideRule.isGlide(2, 5f))
        assertFalse(GlideRule.isGlide(3, 1.2f))
    }
}
