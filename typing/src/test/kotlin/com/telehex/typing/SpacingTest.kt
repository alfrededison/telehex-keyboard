package com.telehex.typing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacingTest {
    @Test
    fun pickAddsASpaceUnlessWhitespaceOrClosingPunctuationFollows() {
        assertTrue(Spacing.spaceAfterPick(null))
        assertTrue(Spacing.spaceAfterPick('a'))
        assertTrue(Spacing.spaceAfterPick('('))
        for (c in " \n,.!?:;…)]}") assertFalse("$c", Spacing.spaceAfterPick(c))
    }

    @Test
    fun closingPunctuationEatsTheAutomaticSpace() {
        assertTrue(Spacing.removesAutoSpace(","))
        assertTrue(Spacing.removesAutoSpace(")"))
        assertFalse(Spacing.removesAutoSpace("("))
        assertFalse(Spacing.removesAutoSpace("a"))
        assertFalse(Spacing.removesAutoSpace(".."))
    }

    @Test
    fun englishGlideSpacesAfterWordsButNotAfterOpeners() {
        assertTrue(Spacing.spaceBeforeGlide('o'))
        assertTrue(Spacing.spaceBeforeGlide(','))
        assertFalse(Spacing.spaceBeforeGlide(null))
        assertFalse(Spacing.spaceBeforeGlide(' '))
        assertFalse(Spacing.spaceBeforeGlide('('))
        assertFalse(Spacing.spaceBeforeGlide('“'))
    }

    @Test
    fun stringVariantsForSwiftMatchTheCharRules() {
        assertTrue(Spacing.pickAddsSpace(null))
        assertTrue(Spacing.pickAddsSpace(""))
        assertFalse(Spacing.pickAddsSpace(", rồi"))
        assertTrue(Spacing.pickAddsSpace("abc"))
        assertTrue(Spacing.glideNeedsLeadingSpace("hello,"))
        assertFalse(Spacing.glideNeedsLeadingSpace("say “"))
        assertTrue(Spacing.shortcutCanFollow(""))
        assertTrue(Spacing.shortcutCanFollow("xin ("))
        assertFalse(Spacing.shortcutCanFollow("x."))
    }
}
