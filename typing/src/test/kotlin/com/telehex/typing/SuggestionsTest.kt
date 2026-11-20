package com.telehex.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    @Test
    fun validSyllableShowsTonesThenCompletionsThenCorrectionsThenEmoji() {
        assertEquals(
            listOf("bạn", "bang", "🎉", "bán", "bàn", "ban"),
            Suggestions.forSyllable(listOf("bán", "bàn"), listOf("bạn", "bang"), listOf("bàn", "ban"), listOf("🎉")),
        )
        assertEquals(listOf("bạn", "bang", "ban"), Suggestions.wordsBesideTones(listOf("bán", "bàn"), listOf("bạn", "bang"), listOf("bàn", "ban")))
        assertEquals(listOf("bạn"), Suggestions.forInvalidSyllable(listOf("bạn", "bạn")))
    }

    @Test
    fun completionsFollowTheTypedCase() {
        assertEquals("KHÔNG", Suggestions.casedCompletion("không", "KH", capsLock = false, usuallyCapitalized = false))
        assertEquals("KHÔNG", Suggestions.casedCompletion("không", "k", capsLock = true, usuallyCapitalized = false))
        assertEquals("Không", Suggestions.casedCompletion("không", "Kh", capsLock = false, usuallyCapitalized = false))
        assertEquals("Không", Suggestions.casedCompletion("không", "K", capsLock = false, usuallyCapitalized = false))
        assertEquals("Hà", Suggestions.casedCompletion("hà", "h", capsLock = false, usuallyCapitalized = true))
        assertEquals("không", Suggestions.casedCompletion("không", "kh", capsLock = false, usuallyCapitalized = false))
    }

    @Test
    fun predictionsFollowTheNextLetterCase() {
        assertEquals("BẠN", Suggestions.casedPrediction("bạn", capsLock = true, capitalizeNext = false, usuallyCapitalized = false))
        assertEquals("Bạn", Suggestions.casedPrediction("bạn", capsLock = false, capitalizeNext = true, usuallyCapitalized = false))
        assertEquals("bạn", Suggestions.casedPrediction("bạn", capsLock = false, capitalizeNext = false, usuallyCapitalized = false))
    }

    @Test
    fun glideWordsFollowTheSameCaseRules() {
        assertEquals("I'll", Suggestions.casedGlide("i'll", english = true, typed = "", capsLock = false, capitalizeNext = false, usuallyCapitalized = false))
        assertEquals("HELLO", Suggestions.casedGlide("hello", english = true, typed = "", capsLock = true, capitalizeNext = false, usuallyCapitalized = false))
        assertEquals("Chào", Suggestions.casedGlide("chào", english = false, typed = "", capsLock = false, capitalizeNext = true, usuallyCapitalized = false))
        assertEquals("CHÀO", Suggestions.casedGlide("chào", english = false, typed = "CH", capsLock = false, capitalizeNext = false, usuallyCapitalized = false))
    }

    @Test
    fun nextWordsAfterASpaceWhateverFollows() {
        assertTrue(Suggestions.offersNextWords(""))
        assertTrue(Suggestions.offersNextWords("xin "))
        assertTrue(Suggestions.offersNextWords("a\n"))
        assertFalse(Suggestions.offersNextWords("xin"))
        assertFalse(Suggestions.offersNextWords(null))
    }
}
