package com.telehex.telex.predict

import com.telehex.telex.predict.GlideDecoder.Point
import org.junit.Assert.*
import org.junit.Test

class GlideDecoderTest {
    private val centers = mapOf(
        't' to Point(0f, 0f), 'i' to Point(4f, 0f), 'e' to Point(1f, 0f),
        'n' to Point(3.5f, 2f), 'g' to Point(2.5f, 1f), 's' to Point(1.5f, 1f),
        'a' to Point(0.5f, 1f), 'o' to Point(4.5f, -1f), 'd' to Point(2f, 1f),
    )
    private val decoder = GlideDecoder()
    private fun path(keys: String) = keys.map { centers.getValue(it) }
    private fun words(vararg words: String) = words.map { NgramModel.Scored(it, 0.01) }

    @Test fun unaccentedPathSuggestsVietnameseWithIncidentalKeys() {
        // t → i đi ngang qua e, nhưng vẫn phải nhận ra t-i-e-n-g khi quay lại e.
        val trace = path("tieng")
        assertEquals("tiếng", decoder.suggest(trace, centers, words("toàn", "tiếng", "tang")).first())
        val dense = trace.zipWithNext().flatMap { (a, b) ->
            (0..12).map { i -> Point(a.x + (b.x - a.x) * i / 12, a.y + (b.y - a.y) * i / 12) }
        }
        assertEquals("tiếng", decoder.suggest(dense, centers, words("toàn", "tiếng", "tang")).first())
    }

    @Test fun fullTelexAndRepeatedLettersAreSupported() {
        assertEquals("tiếng", decoder.suggest(path("tieengs"), centers, words("tiếng", "tang")).first())
        assertEquals("đang", decoder.suggest(path("dang"), centers, words("đang", "tiếng")).first())
    }

    @Test fun singleCharacterSyllableCanUseAThreeKeyTelexPath() {
        val keys = mapOf('o' to Point(0f, 0f), 'w' to Point(1f, 1f), 'r' to Point(2f, 0f))
        assertEquals(listOf("ở"), decoder.suggest("owr".map { keys.getValue(it) }, keys, words("ở")))
    }

    @Test fun contextDisambiguatesWordsWithTheSameShape() {
        val words = listOf(NgramModel.Scored("tiếng", 0.01), NgramModel.Scored("tiền", 0.01), NgramModel.Scored("tiêng", 0.8))
        assertEquals("tiêng", decoder.suggest(path("tieng"), centers, words).first())
    }

    @Test fun rejectsTapsAndPathsFarFromKnownWords() {
        assertTrue(decoder.suggest(listOf(Point(0f, 0f)), centers, words("tiếng")).isEmpty())
        assertTrue(decoder.suggest(listOf(Point(10f, 10f), Point(12f, 10f)), centers, words("tiếng")).isEmpty())
        assertTrue(decoder.suggest(path("tieng"), centers - 'g', words("tiếng")).isEmpty())
    }

    @Test fun translatedLayoutAndTouchNoiseKeepTheResult() {
        val moved = centers.mapValues { (_, p) -> Point(p.x + 5, p.y + 3) }
        val trace = path("tieng").map { Point(it.x + 5.12f, it.y + 2.9f) }
        assertEquals("tiếng", decoder.suggest(trace, moved, words("tiếng", "tang")).first())
    }

    @Test fun vocabularyWorksWithoutAPackAndIncludesLearnedWords() {
        val predictor = Predictor()
        val initial = predictor.glideWords(emptyList())
        assertTrue(initial.any { it.text == "tiếng" })
        predictor.personal.learn(listOf("xin"), "telehex")
        assertTrue(predictor.glideWords(listOf("xin")).any { it.text == "telehex" })
        predictor.personal.forget("telehex")
        assertFalse(predictor.glideWords(listOf("xin")).any { it.text == "telehex" })
        val context = predictor.glideWords(listOf("tiếng")).associate { it.text to it.score }
        assertTrue(context.getValue("việt") > context.getValue("và"))
    }
}
