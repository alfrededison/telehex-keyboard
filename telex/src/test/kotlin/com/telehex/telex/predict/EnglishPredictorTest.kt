package com.telehex.telex.predict

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class EnglishPredictorTest {
    private val predictor = EnglishPredictor(words)

    companion object {
        private val words = EnglishPredictorTest::class.java.getResourceAsStream("/en-frequency.txt")!!
            .bufferedReader().use { it.readLines() }
        private val contextualFixture by lazy {
            val bytes = EnglishPredictorTest::class.java.getResourceAsStream("/tiny-en.ngram")!!.use { it.readBytes() }
            EnglishPredictor(words, BinaryNgramModel(ByteBuffer.wrap(bytes)))
        }
    }

    @Test fun completesCommonWordsOfflineAndKeepsExactWordsUntouched() {
        assertEquals(listOf("help", "held", "hell", "helped", "helping"), predictor.complete("hel", max = 5))
        assertTrue(predictor.complete("hel").contains("hello"))
        assertFalse(predictor.complete("hello").contains("hello"))
        assertTrue(predictor.complete("xyzzy").isEmpty())
    }

    @Test fun preservesTypedCaseAndHonorsCapsLock() {
        assertEquals("Help", predictor.complete("Hel").first())
        assertEquals("HELP", predictor.complete("HEL").first())
        assertEquals("HELP", predictor.complete("hel", capsLock = true).first())
        assertEquals("hElp", predictor.complete("hEl").first())
        assertEquals("don't", predictor.complete("don").first())
        assertEquals("don't", predictor.complete("don'").first())
        assertEquals("don’t", predictor.complete("don’").first())
        assertEquals("don't", EnglishPredictor(listOf("don’t")).complete("don'").first())
    }

    @Test fun completesVocabularyBeyondTheOldCuratedList() {
        for ((prefix, expected) in mapOf("archit" to "architecture", "algor" to "algorithm",
            "accomm" to "accommodation", "opport" to "opportunity", "resched" to "rescheduled")) {
            assertEquals(prefix, expected, predictor.complete(prefix).first())
        }
    }

    @Test fun indexedSearchKeepsFrequencyOrderAndLimitsAcrossTheAlphabet() {
        val words = EnglishPredictor::class.java.getResourceAsStream("/en-frequency.txt")!!
            .bufferedReader().use { it.readLines() }
        assertEquals(50000, words.size)
        for (prefix in ('a'..'z').map { it.toString() } + listOf("app", "writ", "zz", "can'")) {
            // "I" luôn viết hoa trong từ rút gọn: i'm → I'm
            val expected = words.filter { it.startsWith(prefix) && it.length > prefix.length }
                .map { if (it.startsWith("i'")) "I" + it.drop(1) else it }
            for (limit in listOf(1, 3, 24)) assertEquals(prefix, expected.take(limit), predictor.complete(prefix, max = limit))
        }
    }

    @Test fun contextCanPromoteACompletionBeyondTheFirstPageAndUnknownContextKeepsFrequencyOrder() {
        val words = (1..100).map { "app" + "a".repeat(it) }.filter { it.length <= 32 } + "appointment"
        val model = object : NgramModel {
            override fun predict(context: List<String>, max: Int) = emptyList<NgramModel.Scored>()
            override fun probability(context: List<String>, next: String): Double =
                if (context == listOf("book", "an") && next == "appointment") 0.9 else 0.0
        }
        val contextual = EnglishPredictor(words, model)
        assertFalse(contextual.complete("app", max = 3).contains("appointment"))
        assertEquals("appointment", contextual.complete("app", max = 3, context = listOf("book", "an")).first())
        assertEquals(contextual.complete("app"), contextual.complete("app", context = listOf("unseenword")))
    }

    @Test fun completesContractionsTypedWithoutTheApostrophe() {
        assertEquals("I'll", predictor.complete("ill").first())
        assertEquals("I'll", predictor.complete("Ill").first())
        assertEquals("I'LL", predictor.complete("ill", capsLock = true).first())
        assertEquals("don't", predictor.complete("dont").first())
        assertEquals("Don't", predictor.complete("Dont").first())
        assertTrue(predictor.complete("well").contains("we'll"))
        assertTrue(predictor.complete("i").contains("I'm"))
        assertEquals("I'm", predictor.complete("i'").first())
    }

    @Test fun glideDecodesEnglishWordsOnQwertyIncludingContractions() {
        val qwerty = listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).flatMapIndexed { row, (keys, inset) ->
            keys.mapIndexed { i, c -> c to GlideDecoder.Point(inset + i, row.toFloat()) }
        }.toMap()
        val decoder = GlideDecoder()
        fun glide(keys: String, context: List<String> = emptyList()) = decoder.suggest(
            keys.map { qwerty.getValue(it) }, qwerty, contextualFixture.glideWords(context), max = 5, english = true)
        assertEquals("hello", glide("hello").first())
        assertEquals("world", glide("world").first())
        assertTrue(glide("dont").contains("don't"))
        // Từ cùng hình dạng: ngữ cảnh n-gram chọn từ hợp câu
        assertEquals("you", glide("you", listOf("thank")).first())
    }

    @Test fun importedModelPredictsNextWordsAndUsesContextWhileCompleting() {
        val bundled = contextualFixture
        assertEquals("you", bundled.next(listOf("thank")).first())
        assertEquals("to", bundled.next(listOf("looking", "forward")).first())
        assertEquals("you", bundled.next(listOf("how", "are")).first())
        assertEquals("you", bundled.complete("y", context = listOf("thank")).first())
        assertEquals("YOU", bundled.next(listOf("thank"), capsLock = true).first())
        assertEquals("You", bundled.next(listOf("thank"), capitalize = true).first())
        assertTrue(bundled.next(emptyList(), max = 0).isEmpty())
        assertTrue(bundled.next(emptyList(), max = 2).size <= 2)
        assertTrue(predictor.next(listOf("thank")).isEmpty())
    }

    @Test fun contextAndQueriesKeepContractionsAndStopAtSentenceAndNonEnglishBoundaries() {
        assertEquals(listOf("i'm", "very"), EnglishPredictor.contextBefore("Earlier. I’m very "))
        assertEquals(listOf("we", "don't"), EnglishPredictor.contextBefore("we don't "))
        assertEquals(emptyList<String>(), EnglishPredictor.contextBefore("hello! "))
        assertEquals(listOf("again"), EnglishPredictor.contextBefore("see café again "))
        assertEquals(listOf("now"), EnglishPredictor.contextBefore("read foo123 now "))
        assertEquals(EnglishPredictor.Query(EnglishPredictor.Token("hel", "lo"), listOf("say")),
            EnglishPredictor.queryAtCursor("say hel", "lo!"))
        assertEquals(EnglishPredictor.Query(EnglishPredictor.Token("", ""), listOf("thank")),
            EnglishPredictor.queryAtCursor("thank ", ""))
        assertNotEquals(EnglishPredictor.queryAtCursor("thank ", ""), EnglishPredictor.queryAtCursor("see ", ""))
        assertNotNull(EnglishPredictor.queryAtCursor("", ""))
        for ((before, after) in listOf("" to "word", "hello " to "word", "user@hel" to "", "foo_hel" to "", "hello!" to "")) {
            assertNull("$before|$after", EnglishPredictor.queryAtCursor(before, after))
        }
    }

    @Test fun interactiveLookupStaysBoundedWithTheFullDictionary() {
        val bundled = contextualFixture
        repeat(3) { for (prefix in listOf("a", "s", "t", "app")) bundled.complete(prefix, context = listOf("i", "am")) }
        val durations = (1..100).map { i ->
            val start = System.nanoTime()
            assertTrue(bundled.complete(('a' + i % 26).toString(), context = listOf("i", "am")).size <= 24)
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        println("EN completion p95: ${durations[94]} ms; max: ${durations.last()} ms")
        assertTrue("p95 too slow: ${durations[94]} ms", durations[94] < 100)
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
