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
        assertEquals("chào", m.bigram("xin")!!.top(1).first())
        assertTrue(m.usuallyCapitalized("hiếu"))
        val copy = PersonalModel.deserialize(m.serialize())
        assertEquals("chào", copy.bigram("xin")!!.top(1).first())
        assertEquals("hiếu", copy.trigram("xin", "chào")!!.top(1).first())
        assertTrue(copy.usuallyCapitalized("hiếu"))
        assertEquals(m.serialize().lines().sorted(), copy.serialize().lines().sorted())
    }

    @Test
    fun personalModelKeepsCountsAndTotals() {
        val m = PersonalModel()
        repeat(4) { m.learn(listOf("ngân"), "hàng") }
        m.learn(listOf("ngân"), "sách")
        val f = m.bigram("ngân")!!
        assertEquals(5, f.total); assertEquals(2, f.types); assertEquals(4, f.count("hàng"))
        assertEquals(listOf("hàng", "sách"), f.top(5))
        assertEquals(5L, m.size)
        assertEquals(4L to 1, m.frequent) // chỉ "hàng" đạt ngưỡng 3 lần
        assertEquals(listOf("hàng"), m.topFrequent(5))
    }

    @Test
    fun forgetRemovesTheSyllableEverywhereAndFixesTotals() {
        val m = PersonalModel()
        repeat(3) { m.learn(listOf("a", "b"), "hiếu") }
        m.learn(listOf("a", "b"), "c")
        m.forget("Hiếu")
        assertEquals(0, m.unigramCount("hiếu"))
        assertEquals(1L, m.size)
        assertEquals(1, m.bigram("b")!!.total)
        assertEquals(1, m.trigram("a", "b")!!.types)
        assertEquals(0L to 0, m.frequent)
    }

    @Test
    fun prefixLookupIgnoresTones() {
        val m = PersonalModel()
        repeat(3) { m.learn(emptyList(), "Hiếu") }
        m.learn(emptyList(), "hôm")
        m.learn(emptyList(), "tôi")
        assertEquals(listOf("hiếu", "hôm"), m.wordsWithPrefix("h", 5))
        assertEquals(listOf("hiếu"), m.wordsWithPrefix("hiê", 5)) // bỏ dấu thanh, giữ dấu mũ như Predictor
        assertTrue(m.wordsWithPrefix("x", 5).isEmpty())
    }

    @Test
    fun copyIsIndependent() {
        val m = PersonalModel()
        m.learn(listOf("xin"), "chào")
        val c = m.copy()
        m.learn(listOf("xin"), "chào")
        assertEquals(1, c.bigram("xin")!!.total)
        assertEquals(2, m.bigram("xin")!!.total)
    }

    @Test
    fun decayHalvesCountsAndDropsRareEntries() {
        val m = PersonalModel()
        repeat(4) { m.learn(listOf("ngân"), "hàng") }
        m.learn(listOf("ngân"), "sách")
        m.learn(listOf("x", "y"), "z")
        assertTrue(m.decayIfLargerThan(1))
        assertEquals(2, m.bigram("ngân")!!.count("hàng"))
        assertEquals(0, m.bigram("ngân")!!.count("sách"))
        assertEquals(null, m.trigram("x", "y"))
        assertEquals(2L, m.size)
        assertTrue(!m.decayIfLargerThan(100))
    }

    @Test
    fun capsLineBeforeItsCountLineLoads() {
        // 64 âm tiết đầy mảng ban đầu; dòng "c" của âm tiết thứ 65 tới trước dòng "u" của nó.
        val text = buildString {
            append("TELEHEX-PERSONAL-1\n")
            for (i in 0 until 64) append("u\tw$i\t1\n")
            append("c\thiếu\t5\nu\thiếu\t6\n")
        }
        val m = PersonalModel.deserialize(text)
        assertEquals(6, m.unigramCount("hiếu"))
        assertTrue(m.usuallyCapitalized("hiếu"))
    }

    @Test
    fun topFrequentRefreshesAfterOneWordSentences() {
        val m = PersonalModel()
        repeat(3) { m.learn(emptyList(), "anh") }
        assertEquals(listOf("anh"), m.topFrequent(5))
        repeat(5) { m.learn(emptyList(), "em") } // đầu câu, không có ngữ cảnh
        assertEquals(listOf("em", "anh"), m.topFrequent(5))
    }

    @Test
    fun updatingAnExistingKeyDoesNotGrowTheTable() {
        val c = IntCounts()
        c.add(5, 1); c.add(5, 1); c.add(5, 1)
        assertEquals(2, c.capacity)
        assertEquals(3, c.total)
    }

    @Test
    fun entryCountMatchesStoredPairsAndTriples() {
        val m = PersonalModel()
        val words = "hôm nay tôi đi học và gặp bạn của tôi ở trường hôm nay trời đẹp".split(' ')
        repeat(3) { for (i in words.indices) m.learn(words.subList(maxOf(0, i - 2), i), words[i]) }
        fun stored() = m.serialize().lines().count { it.startsWith("b\t") || it.startsWith("t\t") }
        assertEquals(stored(), m.entries)
        m.forget("tôi")
        assertEquals(stored(), m.entries)
        m.learn(listOf("x", "y"), "z")
        assertTrue(m.decayIfLargerThan(1))
        assertEquals(stored(), m.entries)
        assertEquals(m.entries, m.copy().entries)
    }

    @Test
    fun prefixIndexIsRightAfterBulkAndIncrementalUpdates() {
        val m = PersonalModel()
        for (i in 0 until 200) m.learn(emptyList(), "t%03d".format(i))
        repeat(2) { m.learn(emptyList(), "hôm") }
        assertEquals(listOf("hôm"), m.wordsWithPrefix("h", 5)) // sắp lại một lần (> 64 âm tiết mới)
        repeat(3) { m.learn(emptyList(), "hiếu") }
        m.learn(emptyList(), "hà")
        assertEquals(listOf("hiếu", "hôm", "hà"), m.wordsWithPrefix("h", 5)) // chèn dần
        assertEquals(listOf("hôm"), m.wordsWithPrefix("hô", 5))
        val loaded = PersonalModel.deserialize(m.serialize())
        assertEquals(listOf("hiếu", "hôm", "hà"), loaded.wordsWithPrefix("h", 5))
    }

    @Test
    fun intCountsSurvivesManyAddsAndRemoves() {
        val c = IntCounts()
        val ref = HashMap<Int, Int>()
        val rnd = java.util.Random(7)
        repeat(20_000) {
            val k = rnd.nextInt(300)
            if (rnd.nextInt(4) == 0) { c.remove(k); ref.remove(k) } else { c.add(k, 1); ref.merge(k, 1, Int::plus) }
        }
        assertEquals(ref.size, c.size)
        assertEquals(ref.values.sum(), c.total)
        for ((k, v) in ref) assertEquals(v, c.get(k))
        for (k in 0 until 300) if (k !in ref) assertEquals(0, c.get(k))
    }

    @Test
    fun predictorFiltersByPrefixAndUsesPhrases() {
        val phrases = javaClass.getResourceAsStream("/vi-phrases.tsv")!!.bufferedReader().use { Phrases.parse(it.readText()) }
        val p = Predictor(personal = PersonalModel(), phrases = phrases)
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
    override fun probability(context: List<String>, next: String): Double =
        table[context.lastOrNull() ?: ""]?.get(next) ?: 0.0 // ngoài từ vựng của gói
    override fun predict(context: List<String>, max: Int): List<NgramModel.Scored> =
        (table[context.lastOrNull() ?: ""] ?: emptyMap()).entries.sortedByDescending { it.value }
            .take(max).map { NgramModel.Scored(it.key, it.value) }
}

class MixingTest {
    // Điểm cùng cỡ với gói Leipzig thật (đo ngày 2026-11-05): sau "chính" khoảng 0,04 / 0,03 / 0,02,
    // sau "chào" khoảng 0,05; đầu câu khoảng 0,004 sau khi chuẩn hoá theo ngữ cảnh.
    private val base = FakeBase(
        mapOf(
            "" to mapOf("và" to 0.004, "đã" to 0.003, "tôi" to 0.002),
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
    fun aSingleSightingInThisContextTakesTheTopSlot() {
        // Gõ "chính xác" một lần: lần sau, sau "chính", "xác" đứng đầu (như Gboard), gợi ý gốc theo sau.
        val personal = PersonalModel()
        typed(personal, "chính", "xác")
        assertEquals(listOf("xác", "phủ", "sách"), Predictor(base, personal).next(listOf("chính")))
    }

    @Test
    fun justTypedSyllablesDoNotCrowdOutSentenceStart() {
        // Gõ "ngân chính" rồi mở tin nhắn mới: đầu câu không được gợi ý "ngân", "chính".
        val personal = PersonalModel()
        typed(personal, "ngân", "chính")
        assertEquals(listOf("và", "đã", "tôi"), Predictor(base, personal).next(emptyList()))
    }

    @Test
    fun personalOnlyWordsCompleteInNewContexts() {
        // "hiếu" không có trong gói; đang gõ "h" sau một từ mới vẫn phải gợi ý được.
        val personal = PersonalModel()
        repeat(3) { typed(personal, "xin", "chào", "hiếu") }
        val next = Predictor(base, personal).next(listOf("tôi"), "h", 2)
        assertEquals("hiếu", next.first())
        assertTrue("không gợi ý từ không khớp tiền tố: $next", next.all { it.startsWith("h") })
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
    /** Mô hình nhị phân tự dựng: [bigrams] là (w1, w2, q), [trigrams] là (w1, w2, w3, q), id theo [vocab]. */
    private fun model(vocab: List<String>, bigrams: List<IntArray>, trigrams: List<IntArray>): BinaryNgramModel {
        val out = ByteArrayOutputStream()
        fun u32(v: Int) { out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()) }
        out.write("THNG".toByteArray()); out.write(1)
        u32(vocab.size)
        for (v in vocab) { val b = v.toByteArray(); out.write(b.size); out.write(b) }
        out.write(ByteArray(vocab.size) { 80 })
        u32(bigrams.size); bigrams.forEach { u32(it[0]) }; bigrams.forEach { u32(it[1]) }; bigrams.forEach { out.write(it[2]) }
        u32(trigrams.size); trigrams.forEach { u32(it[0]) }; trigrams.forEach { u32(it[1]) }; trigrams.forEach { u32(it[2]) }
        trigrams.forEach { out.write(it[3]) }
        return BinaryNgramModel(ByteBuffer.wrap(out.toByteArray()))
    }

    private val words = (0 until 260).map { "w%03d".format(it) } + "x" + "y"
    private val x = 260
    private val y = 261

    @Test
    fun bestBigramFollowerIsFoundEvenFarDownTheAlphabet() {
        // 260 âm tiết đều theo sau "x"; w250 có xác suất cao nhất (q nhỏ nhất)
        val m = model(words, (0 until 260).map { intArrayOf(x, it, if (it == 250) 2 else 40) }, emptyList())
        assertEquals("w250", m.predict(listOf("x"), 1).first().text)
    }

    @Test
    fun bestTrigramFollowerIsFoundEvenFarDownTheAlphabet() {
        // "có thể" có tới 867 âm tiết theo sau: bộ ba cũng phải chọn theo xác suất
        val m = model(words, emptyList(), (0 until 260).map { intArrayOf(x, y, it, if (it == 250) 2 else 40) })
        assertEquals("w250", m.predict(listOf("x", "y"), 1).first().text)
    }
}

class PythonModelTest {
    private fun tiny() = BinaryNgramModel(ByteBuffer.wrap(javaClass.getResourceAsStream("/tiny.ngram")!!.readBytes()))

    @Test
    fun scoresAreNormalizedPerContext() {
        // Không chia theo các bậc có dữ liệu thì tổng ở đầu câu chỉ khoảng 0,1 và lép vế khi trộn.
        val m = tiny()
        for (ctx in listOf(emptyList(), listOf("xin"), listOf("tôi", "yêu"))) {
            val s = m.scorer(ctx)
            val total = m.vocabulary.sumOf { s.probability(it) }
            assertTrue("tổng sau $ctx = $total", total in 0.7..1.3)
        }
    }

    @Test
    fun readsModelBuiltByPythonTool() {
        val bytes = javaClass.getResourceAsStream("/tiny.ngram")!!.readBytes()
        val m = BinaryNgramModel(ByteBuffer.wrap(bytes))
        assertEquals("chào", m.predict(listOf("xin"), 1).first().text)
        assertEquals("thoại", m.predict(listOf("điện"), 1).first().text)
        assertEquals("việt", m.predict(listOf("tôi", "yêu"), 1).first().text)
    }
}
