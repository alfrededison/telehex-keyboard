package com.telehex.telex.predict

import java.text.Normalizer
import org.junit.Assert.*
import org.junit.Test

class VietnameseVocabularyTest {
    private val raw = VietnameseVocabularyTest::class.java.getResourceAsStream("/vi-frequency.txt")!!
        .bufferedReader().use { it.readLines() }

    @Test fun normalizesDeduplicatesAndRejectsNonSyllables() {
        val vocabulary = VietnameseVocabulary(listOf("NGHỀ", "nghề",
            Normalizer.normalize("nghiệp", Normalizer.Form.NFD), "facebook", "123", "", "hóà"))
        assertEquals(listOf("nghề", "nghiệp"), vocabulary.words)
        assertEquals(listOf("nghề", "nghiệp"), vocabulary.complete("NGH", 3))
        assertTrue(vocabulary.complete("ngh", 0).isEmpty())
        assertEquals(Int.MAX_VALUE, vocabulary.rankOf("facebook"))
        assertEquals(0.0, vocabulary.prior("unknown"), 0.0)
    }

    @Test fun realAssetProvidesCompletionsAndGlideVocabularyWithoutAPack() {
        assertEquals(10052, raw.size)
        val vocabulary = VietnameseVocabulary(raw)
        println("VI frequency vocabulary: ${vocabulary.words.size} valid syllables")
        assertTrue(vocabulary.words.size > 1000)
        assertFalse(vocabulary.words.contains("facebook"))
        val predictor = Predictor(vocabulary = vocabulary)
        assertTrue(Predictor().next(emptyList(), "nghi", 24).isEmpty())
        assertTrue(predictor.next(emptyList(), "nghi", 24).contains("nghiệp"))
        assertTrue(predictor.glideWords(emptyList()).any { it.text == "nghiệp" && it.score > 0.0 })
        assertTrue(predictor.next(emptyList(), "nghi", 2).size <= 2)
    }

    @Test fun personalContextAndPhrasesTakePriorityOverAssetFrequency() {
        val vocabulary = VietnameseVocabulary(listOf("là", "nghiệp", "nghĩa", "việc"))
        val personal = PersonalModel()
        repeat(4) { personal.learn(listOf("tôi"), "nghĩa") }
        val predictor = Predictor(personal = personal, vocabulary = vocabulary)
        assertEquals("nghĩa", predictor.next(listOf("tôi"), "nghi").first())
        val phrases = javaClass.getResourceAsStream("/vi-phrases.tsv")!!.bufferedReader().use { Phrases.parse(it.readText()) }
        assertEquals("việc", Predictor(phrases = phrases, vocabulary = vocabulary).next(listOf("công")).first())
    }

    @Test fun loadedNgramKeepsItsCompletionRanking() {
        val model = object : NgramModel {
            override fun predict(context: List<String>, max: Int) =
                listOf(NgramModel.Scored("nghiệp", 0.9), NgramModel.Scored("nghĩa", 0.1)).take(max)
            override fun probability(context: List<String>, next: String) = if (next == "nghiệp") 0.9 else 0.1
        }
        val plain = Predictor(base = model)
        val withVocabulary = Predictor(base = model, vocabulary = VietnameseVocabulary(listOf("nghĩa", "nghiệp")))
        assertEquals(plain.next(listOf("công"), "nghi"), withVocabulary.next(listOf("công"), "nghi"))
    }

    @Test fun phraseAssetKeepsLegacyFollowersAndOrder() {
        val phrases = javaClass.getResourceAsStream("/vi-phrases.tsv")!!.bufferedReader().use { Phrases.parse(it.readText()) }
        assertEquals(35, phrases.size)
        assertEquals(listOf("thoại", "tử", "lực"), phrases["điện"])
        assertEquals(listOf("ơn", "giác", "thấy"), phrases["cảm"])
        assertTrue(Predictor().next(listOf("điện")).isEmpty())
        assertEquals("thoại", Predictor(phrases = phrases).next(listOf("điện")).first())
    }
}
