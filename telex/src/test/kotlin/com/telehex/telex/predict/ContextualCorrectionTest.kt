package com.telehex.telex.predict

import com.telehex.telex.Corrector
import com.telehex.telex.TelexConfig
import org.junit.Assert.*
import org.junit.Test

class ContextualCorrectionTest {
    private val corrector = Corrector(TelexConfig(), emptyMap())

    @Test fun sameTypoChangesRankWithPersonalContext() {
        val predictor = Predictor()
        repeat(20) {
            predictor.personal.learn(listOf("tìm"), "cách")
            predictor.personal.learn(listOf("lạch"), "cạch")
        }
        assertEquals("cách", corrector.suggest("cach", contextProbability = predictor.correctionScorer(listOf("tìm"))).first())
        assertEquals("cạch", corrector.suggest("cach", contextProbability = predictor.correctionScorer(listOf("lạch"))).first())
    }

    @Test fun invalidComposingSyllableUsesTheSameContextRanking() {
        val predictor = Predictor()
        repeat(20) {
            predictor.personal.learn(listOf("ưu"), "tiên")
            predictor.personal.learn(listOf("kiếm"), "tiền")
        }
        assertEquals("tiên", corrector.suggest("tien", contextProbability = predictor.correctionScorer(listOf("ưu"))).first())
        assertEquals("tiền", corrector.suggest("tien", contextProbability = predictor.correctionScorer(listOf("kiếm"))).first())
    }

    @Test fun trigramContextSeparatesSameLastWordAndPreservesCase() {
        val predictor = Predictor()
        repeat(20) {
            predictor.personal.learn(listOf("tôi", "muốn"), "bạn")
            predictor.personal.learn(listOf("bạn", "muốn"), "bán")
        }
        assertEquals("Bạn", corrector.suggest("ban", listOf(true), contextProbability = predictor.correctionScorer(listOf("Tôi", "Muốn"))).first())
        assertEquals("bán", corrector.suggest("ban", contextProbability = predictor.correctionScorer(listOf("bạn", "muốn"))).first())
    }

    @Test fun usesBaseModelAndScoresBeforeTruncating() {
        val base = object : NgramModel {
            override fun predict(context: List<String>, max: Int) = emptyList<NgramModel.Scored>()
            override fun probability(context: List<String>, next: String): Double = when {
                context == listOf("tìm") && next == "cách" -> 0.8
                context == listOf("lạch") && next == "cạch" -> 0.9
                else -> 0.0
            }
        }
        val predictor = Predictor(base)
        assertEquals(listOf("cách"), corrector.suggest("cach", max = 1, contextProbability = predictor.correctionScorer(listOf("tìm"))))
        assertEquals(listOf("cạch"), corrector.suggest("cach", max = 1, contextProbability = predictor.correctionScorer(listOf("lạch"))))
    }

    @Test fun noContextOrNoEvidenceKeepsLegacyRanking() {
        val predictor = Predictor()
        repeat(10) { predictor.personal.learn(listOf("tìm"), "cách") }
        val expected = corrector.suggest("cach", max = 24)
        for (context in listOf(emptyList(), listOf("không", "biết"))) {
            assertEquals(expected, corrector.suggest("cach", max = 24, contextProbability = predictor.correctionScorer(context)))
        }
        assertEquals(expected, corrector.suggest("cach", max = 24, contextProbability = { Double.NaN }))
        assertEquals(expected, corrector.suggest("cach", max = 24, contextProbability = { Double.POSITIVE_INFINITY }))
        assertEquals(expected, corrector.suggest("cach", max = 24, contextProbability = { -1.0 }))
        predictor.personal.forget("cách")
        predictor.personal.learn(listOf("lạch"), "cạch")
        assertEquals(expected, corrector.suggest("cach", max = 24, contextProbability = predictor.correctionScorer(listOf("tìm"))))
    }

    @Test fun contextDoesNotOverrideExplicitDiacriticsOrPromoteRemoteTypos() {
        val boosted: (String) -> Double = { if (it == "trước" || it == "choa") 1.0 else 0.0 }
        assertFalse("trước" in corrector.suggest("truocx", contextProbability = boosted))
        assertNotEquals("choa", corrector.suggest("hoa", contextProbability = boosted).first())
        val seen = HashSet<String>()
        corrector.suggest("ban", max = 24, contextProbability = { assertTrue("scored twice: $it", seen.add(it)); 0.5 })
        assertTrue(seen.isNotEmpty())
    }
}
