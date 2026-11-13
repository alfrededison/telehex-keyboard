package com.telehex.portable

import com.telehex.platform.Normalizer
import com.telehex.telex.*
import com.telehex.telex.predict.*
import kotlin.test.*

class PortableTest {
    @Test fun originalGoldenCorpus() {
        var count = 0
        for (line in goldenFixture.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }) {
            val parts = line.trimEnd().split('\t')
            val options = parts.getOrNull(2).orEmpty().split(',').map { it.trim() }
            val engine = TelexEngine(TelexConfig(newToneStyle = "new" in options, lAsGrave = "l" in options))
            parts[0].forEach { engine.press(it.lowercaseChar(), it.isUpperCase()) }
            assertEquals(parts[1], engine.text(), parts[0])
            count++
        }
        assertEquals(431, count)
    }

    @Test fun unicodePredictionAndPersonalDataOnNative() {
        val nfd = Normalizer.normalize("tôi tìm ", Normalizer.Form.NFD)
        assertEquals(listOf("tôi", "tìm"), PredictionContext.before(nfd))
        assertEquals("được", Normalizer.normalize(Normalizer.normalize("được", Normalizer.Form.NFD), Normalizer.Form.NFC))
        val vocabulary = VietnameseVocabulary(listOf("người", "nghiệp", "có", "123"))
        assertEquals(listOf("người", "nghiệp"), vocabulary.complete("ng", 5))
        val personal = PersonalModel()
        personal.learn(listOf("tôi"), "yêu")
        assertEquals(1, PersonalModel.deserialize(personal.serialize()).unigramCount("yêu"))
        val english = EnglishPredictor(listOf("help", "held", "hello"))
        assertEquals(listOf("Help", "Held", "Hello"), english.complete("Hel"))
        assertEquals(listOf("we", "don't"), EnglishPredictor.contextBefore("Earlier. we don’t "))
    }

    @Test fun rejectsTruncatedLanguagePacksOnNative() {
        assertFailsWith<IllegalArgumentException> { BinaryNgramModel(byteArrayOf(1, 2, 3)) }
    }
}
