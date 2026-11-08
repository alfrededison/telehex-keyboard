package com.telehex.telex.predict

import org.junit.Assert.*
import org.junit.Test

class EnglishPredictorTest {
    private val predictor = EnglishPredictor()

    @Test fun completesCommonWordsOfflineAndKeepsExactWordsUntouched() {
        assertEquals(listOf("hello", "help", "helpful", "helping", "helped"), predictor.complete("hel"))
        assertFalse(predictor.complete("hello").contains("hello"))
        assertTrue(predictor.complete("xyzzy").isEmpty())
    }

    @Test fun preservesTypedCaseAndHonorsCapsLock() {
        assertEquals("Hello", predictor.complete("Hel").first())
        assertEquals("HELLO", predictor.complete("HEL").first())
        assertEquals("HELLO", predictor.complete("h", capsLock = true).first { it == "HELLO" })
        assertEquals("hEllo", predictor.complete("hEl").first())
        assertEquals("don't", predictor.complete("don").first())
    }

    @Test fun boundsResultsAndRejectsNonEnglishOrIdentifiers() {
        assertEquals(2, predictor.complete("hel", max = 2).size)
        for (prefix in listOf("", "tiếng", "user@", "hel_", "a".repeat(33))) assertTrue(prefix, predictor.complete(prefix).isEmpty())
        assertTrue(predictor.complete("hel", max = 0).isEmpty())
    }

    @Test fun staleTokenDoesNotMatchTheSuffixOrPrefixOfAnotherWord() {
        val shown = EnglishPredictor.tokenAtCursor("hel", "")
        assertNotEquals(shown, EnglishPredictor.tokenAtCursor("shel", "ter"))
        assertNotEquals(shown, EnglishPredictor.tokenAtCursor("hel", "p"))
        assertNotEquals(shown, EnglishPredictor.tokenAtCursor("user@hel", ""))
        assertEquals(shown, EnglishPredictor.tokenAtCursor("say hel", ""))
    }

    @Test fun capturesWholeWordAtCursorButSkipsStructuredTokens() {
        assertEquals(EnglishPredictor.Token("hel", "lo"), EnglishPredictor.tokenAtCursor("say hel", "lo!"))
        assertEquals(EnglishPredictor.Token("don't", ""), EnglishPredictor.tokenAtCursor("don't", ""))
        for ((before, after) in listOf("a@hel" to "", "https://hel" to "", "some_hel" to "", "hel" to "_name", "hel" to "@mail.com", "hel" to ".com", "éhel" to "", "hel" to "é")) {
            assertNull("$before|$after", EnglishPredictor.tokenAtCursor(before, after))
        }
        assertNull(EnglishPredictor.tokenAtCursor("hello ", ""))
    }
}
