package com.telehex.telex.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PredictTest {
    @Test
    fun personalModelLearnsBigrams() {
        val m = PersonalModel()
        repeat(3) { m.learn(listOf("xin"), "chào"); m.learn(listOf("xin", "chào"), "Hiếu") }
        m.learn(listOf("xin"), "lỗi")
        assertEquals("chào", m.predict(listOf("xin"), 3).first().text)
        assertTrue(m.usuallyCapitalized("hiếu"))
        val copy = PersonalModel.deserialize(m.serialize())
        assertEquals("chào", copy.predict(listOf("xin"), 3).first().text)
        assertTrue(copy.usuallyCapitalized("hiếu"))
    }

    @Test
    fun predictorFiltersByPrefixAndUsesPhrases() {
        val p = Predictor(personal = PersonalModel())
        assertEquals("thoại", p.next(listOf("điện"), "", 3).first())
        assertEquals(listOf("thoại"), p.next(listOf("điện"), "th", 3))
        assertTrue(p.next(listOf("điện"), "x", 3).isEmpty())
    }

    @Test
    fun binaryModelRoundTrip() {
        // vocab: chào(0) hiếu(1) xin(2); bigram xin→chào; trigram xin chào → hiếu
        val vocab = listOf("chào", "hiếu", "xin")
        val out = ByteArrayOutputStream()
        fun u32(v: Int) { out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()) }
        out.write("THNG".toByteArray()); out.write(1)
        u32(vocab.size)
        for (v in vocab) { val b = v.toByteArray(); out.write(b.size); out.write(b) }
        out.write(byteArrayOf(8, 16, 8)) // unigram q
        u32(1); u32(2); u32(0); out.write(2) // bigram xin→chào, q=2
        u32(1); u32(2); u32(0); u32(1); out.write(1) // trigram xin chào → hiếu
        val m = BinaryNgramModel(ByteBuffer.wrap(out.toByteArray()))
        assertEquals("chào", m.predict(listOf("xin"), 1).first().text)
        assertEquals("hiếu", m.predict(listOf("xin", "chào"), 1).first().text)
        assertTrue(m.probability(listOf("xin"), "chào") > m.probability(listOf("xin"), "hiếu"))
    }
}

/** Mô hình gốc giả: xác suất cố định theo âm tiết trước. */
private class FakeBase(private val table: Map<String, Map<String, Double>>) : NgramModel {
    override val size: Long = 1_000_000
    override fun probability(context: List<String>, next: String): Double =
        table[context.lastOrNull() ?: ""]?.get(next) ?: 0.0001
    override fun predict(context: List<String>, max: Int): List<NgramModel.Scored> =
        (table[context.lastOrNull() ?: ""] ?: emptyMap()).entries.sortedByDescending { it.value }
            .take(max).map { NgramModel.Scored(it.key, it.value) }
}

class MixingTest {
    // Điểm cùng cỡ với gói Leipzig thật (đo ngày 2026-11-05): sau "chính" khoảng 0,04 / 0,03 / 0,02,
    // sau "chào" khoảng 0,05, đầu câu dưới 0,001.
    private val base = FakeBase(
        mapOf(
            "" to mapOf("và" to 0.0004, "đã" to 0.0003),
            "chính" to mapOf("phủ" to 0.041, "sách" to 0.033, "trị" to 0.018),
            "chào" to mapOf("bạn" to 0.05, "anh" to 0.03),
        )
    )

    private fun typed(p: PersonalModel, vararg words: String) {
        val ctx = ArrayList<String>()
        for (w in words) { p.learn(ctx.takeLast(2), w); ctx.add(w) }
    }

    @Test
    fun justTypedSyllablesDoNotCrowdOutContextPredictions() {
        // Lỗi thấy trên máy: gõ "ngân chính" rồi gợi ý sau "chính" lại là "chính", "ngân".
        val personal = PersonalModel()
        typed(personal, "ngân", "chính")
        val next = Predictor(base, personal).next(listOf("ngân", "chính"))
        assertEquals(listOf("phủ", "sách", "trị"), next)
    }

    @Test
    fun repeatedPersonalHabitBeatsTheBaseModel() {
        val personal = PersonalModel()
        repeat(3) { typed(personal, "xin", "chào", "hiếu") }
        val next = Predictor(base, personal).next(listOf("xin", "chào"))
        assertEquals("hiếu", next.first())
        assertTrue("vẫn còn gợi ý của mô hình gốc: $next", "bạn" in next)
    }

    @Test
    fun aSingleOneOffDoesNotOverrideAStrongBasePrediction() {
        val personal = PersonalModel()
        typed(personal, "chính", "xác")
        val next = Predictor(base, personal).next(listOf("chính"))
        assertTrue("$next", next.indexOf("phủ") in 0..1)
        assertTrue("$next", "xác" in next)
    }

    @Test
    fun sentenceStartMixesPersonalFrequency() {
        val personal = PersonalModel()
        repeat(20) { typed(personal, "anh") }
        assertEquals("anh", Predictor(base, personal).next(emptyList()).first())
    }

    @Test
    fun withoutABaseModelPersonalCountsStillPredict() {
        val personal = PersonalModel()
        repeat(2) { typed(personal, "xin", "chào", "hiếu") }
        assertEquals("hiếu", Predictor(null, personal, emptyMap()).next(listOf("xin", "chào")).first())
    }
}

class CandidateSelectionTest {
    /** Mô hình nhị phân: 260 âm tiết w000..w259 đều theo sau "x"; w250 có xác suất cao nhất. */
    private fun manyFollowers(): BinaryNgramModel {
        val vocab = (0 until 260).map { "w%03d".format(it) } + "x"
        val out = ByteArrayOutputStream()
        fun u32(v: Int) { out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()) }
        out.write("THNG".toByteArray()); out.write(1)
        u32(vocab.size)
        for (v in vocab) { val b = v.toByteArray(); out.write(b.size); out.write(b) }
        out.write(ByteArray(vocab.size) { 80 })
        val x = vocab.size - 1
        u32(260)
        repeat(260) { u32(x) }
        repeat(260) { u32(it) }
        out.write(ByteArray(260) { if (it == 250) 2 else 40 })
        u32(0)
        return BinaryNgramModel(ByteBuffer.wrap(out.toByteArray()))
    }

    @Test
    fun bestFollowerIsFoundEvenFarDownTheAlphabet() {
        assertEquals("w250", manyFollowers().predict(listOf("x"), 1).first().text)
    }
}

class PythonModelTest {
    @Test
    fun readsModelBuiltByPythonTool() {
        val bytes = javaClass.getResourceAsStream("/tiny.ngram")!!.readBytes()
        val m = BinaryNgramModel(ByteBuffer.wrap(bytes))
        assertEquals("chào", m.predict(listOf("xin"), 1).first().text)
        assertEquals("thoại", m.predict(listOf("điện"), 1).first().text)
        assertEquals("việt", m.predict(listOf("tôi", "yêu"), 1).first().text)
    }
}
