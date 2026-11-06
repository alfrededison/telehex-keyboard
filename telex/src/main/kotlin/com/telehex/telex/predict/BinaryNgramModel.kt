package com.telehex.telex.predict

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mô hình n-gram gốc đọc từ file nhị phân do `tools/build_ngram.py` sinh ra (có thể mmap).
 *
 * Định dạng (little-endian):
 * ```
 * magic "THNG" + u8 version(1)
 * u32 N          vocab: N × (u8 len, utf8 bytes), sắp xếp theo thứ tự byte
 * N × u8         unigram q (−log2 p × 8, clamp 255)
 * u32 M          bigram: M × u32 w1, M × u32 w2, M × u8 q   (sắp xếp theo (w1, w2))
 * u32 K          trigram: K × u32 w1, K × u32 w2, K × u32 w3, K × u8 q  (sắp xếp theo (w1, w2, w3))
 * ```
 * Xác suất đã được làm trơn Kneser–Ney ở phía Python; runtime tra và nội suy giữa các bậc để sinh ứng viên.
 */
class BinaryNgramModel(buffer: ByteBuffer) : NgramModel {
    private val buf: ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    private val vocab: Array<String>
    private val index: HashMap<String, Int>
    private val uniQ: Int
    private val biN: Int
    private val biW1: Int; private val biW2: Int; private val biQ: Int
    private val triN: Int
    private val triW1: Int; private val triW2: Int; private val triW3: Int; private val triQ: Int

    override val size: Long

    init {
        require(buf.get(0) == 'T'.code.toByte() && buf.get(1) == 'H'.code.toByte() && buf.get(2) == 'N'.code.toByte() && buf.get(3) == 'G'.code.toByte()) { "Không phải file n-gram TeleHex" }
        require(buf.get(4).toInt() == 1) { "Phiên bản mô hình không hỗ trợ" }
        var pos = 5
        val n = buf.getInt(pos); pos += 4
        vocab = Array(n) { "" }
        index = HashMap(n * 2)
        for (i in 0 until n) {
            val len = buf.get(pos).toInt() and 0xFF; pos += 1
            val bytes = ByteArray(len)
            for (j in 0 until len) bytes[j] = buf.get(pos + j)
            pos += len
            vocab[i] = String(bytes, Charsets.UTF_8)
            index[vocab[i]] = i
        }
        uniQ = pos; pos += n
        biN = buf.getInt(pos); pos += 4
        biW1 = pos; pos += 4 * biN
        biW2 = pos; pos += 4 * biN
        biQ = pos; pos += biN
        triN = buf.getInt(pos); pos += 4
        triW1 = pos; pos += 4 * triN
        triW2 = pos; pos += 4 * triN
        triW3 = pos; pos += 4 * triN
        triQ = pos
        pos += triN
        require(biN >= 0 && triN >= 0 && pos <= buf.limit()) { "File mô hình bị cắt ngắn" }
        // Mọi id phải nằm trong vocab, nếu không predict() sẽ ném lỗi giữa lúc gõ
        for (i in 0 until biN) require(buf.getInt(biW1 + 4 * i) in 0 until n && buf.getInt(biW2 + 4 * i) in 0 until n) { "Mô hình hỏng (bigram)" }
        for (i in 0 until triN) require(
            buf.getInt(triW1 + 4 * i) in 0 until n && buf.getInt(triW2 + 4 * i) in 0 until n && buf.getInt(triW3 + 4 * i) in 0 until n
        ) { "Mô hình hỏng (trigram)" }
        size = 1_000_000L // mô hình gốc coi như "rất nhiều dữ liệu"
    }

    private fun q2p(q: Int): Double = Math.pow(2.0, -q / 8.0)
    private fun uniP(w: Int): Double = q2p(buf.get(uniQ + w).toInt() and 0xFF)

    /** Khoảng [lo, hi) các bigram có w1 = [w]. */
    private fun biRange(w: Int): IntRange {
        val lo = lowerBound(biN) { i -> buf.getInt(biW1 + 4 * i) < w }
        val hi = lowerBound(biN) { i -> buf.getInt(biW1 + 4 * i) <= w }
        return lo until hi
    }

    private fun triRange(w1: Int, w2: Int): IntRange {
        val lo = lowerBound(triN) { i -> val a = buf.getInt(triW1 + 4 * i); a < w1 || (a == w1 && buf.getInt(triW2 + 4 * i) < w2) }
        val hi = lowerBound(triN) { i -> val a = buf.getInt(triW1 + 4 * i); a < w1 || (a == w1 && buf.getInt(triW2 + 4 * i) <= w2) }
        return lo until hi
    }

    /** Chỉ số đầu tiên mà [less] sai (mảng đã sắp xếp). */
    private inline fun lowerBound(n: Int, less: (Int) -> Boolean): Int {
        var lo = 0; var hi = n
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (less(mid)) lo = mid + 1 else hi = mid }
        return lo
    }

    private fun biP(w1: Int, w2: Int): Double? {
        val r = biRange(w1)
        if (r.isEmpty()) return null
        var lo = r.first; var hi = r.last + 1
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (buf.getInt(biW2 + 4 * mid) < w2) lo = mid + 1 else hi = mid }
        return if (lo < r.last + 1 && buf.getInt(biW2 + 4 * lo) == w2) q2p(buf.get(biQ + lo).toInt() and 0xFF) else null
    }

    private fun triP(w1: Int, w2: Int, w3: Int): Double? {
        val r = triRange(w1, w2)
        if (r.isEmpty()) return null
        var lo = r.first; var hi = r.last + 1
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (buf.getInt(triW3 + 4 * mid) < w3) lo = mid + 1 else hi = mid }
        return if (lo < r.last + 1 && buf.getInt(triW3 + 4 * lo) == w3) q2p(buf.get(triQ + lo).toInt() and 0xFF) else null
    }

    override fun probability(context: List<String>, next: String): Double {
        val w = index[next.lowercase()] ?: return 0.0
        return score(context.map { index[it.lowercase()] }, w)
    }

    private fun score(ctx: List<Int?>, w: Int): Double {
        val c1 = ctx.lastOrNull()
        val c2 = if (ctx.size >= 2) ctx[ctx.size - 2] else null
        var p = 0.1 * uniP(w)
        if (c1 != null) biP(c1, w)?.let { p += 0.3 * it }
        if (c1 != null && c2 != null) triP(c2, c1, w)?.let { p += 0.6 * it }
        return p
    }

    override fun predict(context: List<String>, max: Int): List<NgramModel.Scored> {
        val ctx = context.map { index[it.lowercase()] }
        val c1 = ctx.lastOrNull()
        val c2 = if (ctx.size >= 2) ctx[ctx.size - 2] else null
        val cands = HashSet<Int>()
        if (c1 != null && c2 != null) for (i in triRange(c2, c1)) cands.add(buf.getInt(triW3 + 4 * i))
        // Ứng viên theo xác suất, không theo thứ tự từ vựng: hàng bigram sắp theo id (thứ tự chữ cái),
        // nên lấy N hàng đầu sẽ bỏ sót "phủ", "trị" sau "chính".
        if (c1 != null) cands.addAll(bestFollowers(biRange(c1), biW2, biQ, BIGRAM_CANDIDATES))
        if (cands.size < max * 2) cands.addAll(topUnigrams)
        return cands.map { NgramModel.Scored(vocab[it], score(ctx, it)) }
            .sortedByDescending { it.score }
            .take(max)
    }

    /** Tối đa [k] âm tiết có xác suất cao nhất (q nhỏ nhất) trong khoảng hàng [r]. */
    private fun bestFollowers(r: IntRange, wordCol: Int, qCol: Int, k: Int): List<Int> {
        if (r.isEmpty()) return emptyList()
        val ids = IntArray(k)
        val qs = IntArray(k)
        var n = 0
        for (i in r) {
            val q = buf.get(qCol + i).toInt() and 0xFF
            if (n == k && q >= qs[k - 1]) continue
            var j = if (n < k) n++ else k - 1
            while (j > 0 && qs[j - 1] > q) { qs[j] = qs[j - 1]; ids[j] = ids[j - 1]; j-- }
            qs[j] = q
            ids[j] = buf.getInt(wordCol + 4 * i)
        }
        return ids.asList().subList(0, n)
    }

    private companion object {
        const val BIGRAM_CANDIDATES = 50
    }

    private val topUnigrams: List<Int> by lazy {
        (vocab.indices).sortedBy { buf.get(uniQ + it).toInt() and 0xFF }.take(50)
    }
}
