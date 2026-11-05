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
