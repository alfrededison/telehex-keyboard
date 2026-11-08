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

    init {
        require(buf.limit() >= 17) { "File mô hình bị cắt ngắn" }
        require(buf.get(0) == 'T'.code.toByte() && buf.get(1) == 'H'.code.toByte() && buf.get(2) == 'N'.code.toByte() && buf.get(3) == 'G'.code.toByte()) { "Không phải file n-gram TeleHex" }
        require(buf.get(4).toInt() == 1) { "Phiên bản mô hình không hỗ trợ" }
        var pos = 5
        val n = buf.getInt(pos); pos += 4
        require(n >= 0 && n <= (buf.limit() - 17) / 2) { "Số từ vựng không hợp lệ" }
        vocab = Array(n) { "" }
        index = HashMap(n * 2)
        for (i in 0 until n) {
            val len = buf.get(pos).toInt() and 0xFF; pos += 1
            require(len > 0 && pos <= buf.limit() - len) { "Từ vựng bị cắt ngắn" }
            val bytes = ByteArray(len)
            for (j in 0 until len) bytes[j] = buf.get(pos + j)
            pos += len
            vocab[i] = String(bytes, Charsets.UTF_8)
            index[vocab[i]] = i
        }
        require(pos <= buf.limit() - n - 8) { "File mô hình bị cắt ngắn" }
        uniQ = pos; pos += n
        biN = buf.getInt(pos); pos += 4
        require(biN >= 0 && biN <= (buf.limit() - pos - 4) / 9) { "Bigram bị cắt ngắn" }
        biW1 = pos; pos += 4 * biN
        biW2 = pos; pos += 4 * biN
        biQ = pos; pos += biN
        triN = buf.getInt(pos); pos += 4
        require(triN >= 0 && triN <= (buf.limit() - pos) / 13) { "Trigram bị cắt ngắn" }
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
    }

    /** Từ vựng cho bộ hoàn thành từ và kiểm tra ngôn ngữ khi nạp gói. */
    val vocabulary: List<String> get() = vocab.asList()

    private fun uniP(w: Int): Double = Q2P[buf.get(uniQ + w).toInt() and 0xFF]

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

    /** Hàng có cột từ [wordCol] bằng [w] trong khoảng [r] (sắp theo từ), hoặc -1. */
    private fun rowOf(r: IntRange, wordCol: Int, w: Int): Int {
        if (r.isEmpty()) return -1
        var lo = r.first; var hi = r.last + 1
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (buf.getInt(wordCol + 4 * mid) < w) lo = mid + 1 else hi = mid }
        return if (lo <= r.last && buf.getInt(wordCol + 4 * lo) == w) lo else -1
    }

    /**
     * Chấm điểm cho một ngữ cảnh: tìm khoảng hàng bigram/trigram một lần, mỗi ứng viên chỉ còn một
     * phép tìm nhị phân trong khoảng đó.
     *
     * Điểm = (0,1·P₁ + 0,3·P₂ + 0,6·P₃) / tổng trọng số của các bậc có dữ liệu cho ngữ cảnh này. Chia
     * như vậy để tổng xác suất trên từ vựng xấp xỉ 1 ở mọi ngữ cảnh (đầu câu chỉ có P₁), nên trộn được
     * công bằng với mô hình cá nhân; nếu không chia, đầu câu tổng chỉ khoảng 0,1.
     */
    private inner class ContextScorer(context: List<String>) : NgramModel.Scorer {
        private val c1: Int? = context.lastOrNull()?.let { index[it.lowercase()] }
        private val c2: Int? = if (context.size >= 2) index[context[context.size - 2].lowercase()] else null
        private val bi: IntRange = if (c1 != null) biRange(c1) else IntRange.EMPTY
        private val tri: IntRange = if (c1 != null && c2 != null) triRange(c2, c1) else IntRange.EMPTY
        private val norm: Double = 0.1 + (if (bi.isEmpty()) 0.0 else 0.3) + (if (tri.isEmpty()) 0.0 else 0.6)

        fun score(w: Int): Double {
            var p = 0.1 * uniP(w)
            val b = rowOf(bi, biW2, w)
            if (b >= 0) p += 0.3 * Q2P[buf.get(biQ + b).toInt() and 0xFF]
            val t = rowOf(tri, triW3, w)
            if (t >= 0) p += 0.6 * Q2P[buf.get(triQ + t).toInt() and 0xFF]
            return p / norm
        }

        override fun probability(next: String): Double = index[next]?.let { score(it) } ?: 0.0

        override fun candidates(max: Int): List<String> {
            val ids = LinkedHashSet<Int>()
            // Theo xác suất, không theo thứ tự từ vựng: hàng sắp theo id (thứ tự chữ cái), nên lấy N
            // hàng đầu sẽ bỏ sót "phủ", "trị" sau "chính". Bộ ba cũng giới hạn ("có thể" có 867 từ theo sau).
            ids.addAll(bestFollowers(tri, triW3, triQ, FOLLOWER_CANDIDATES))
            ids.addAll(bestFollowers(bi, biW2, biQ, FOLLOWER_CANDIDATES))
            if (ids.size < max * 2) ids.addAll(topUnigrams)
            val scored = ids.map { it to score(it) }.sortedByDescending { it.second }
            return scored.take(max).map { vocab[it.first] }
        }
    }

    override fun scorer(context: List<String>): NgramModel.Scorer = ContextScorer(context)

    override fun probability(context: List<String>, next: String): Double =
        ContextScorer(context).probability(next.lowercase())

    override fun predict(context: List<String>, max: Int): List<NgramModel.Scored> {
        val s = ContextScorer(context)
        return s.candidates(max).map { NgramModel.Scored(it, s.probability(it)) }
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
        const val FOLLOWER_CANDIDATES = 50
        /** q (8 bit) → xác suất 2^(−q/8), tra bảng thay vì gọi Math.pow mỗi lần. */
        val Q2P = DoubleArray(256) { Math.pow(2.0, -it / 8.0) }
    }

    private val topUnigrams: List<Int> by lazy {
        (vocab.indices).sortedBy { buf.get(uniQ + it).toInt() and 0xFF }.take(50)
    }
}
