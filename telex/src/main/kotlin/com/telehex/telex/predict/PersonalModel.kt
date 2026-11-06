package com.telehex.telex.predict

/**
 * Mô hình cá nhân: đếm n-gram (1–3) từ chính người dùng, lưu dạng text đơn giản.
 * Ưu tiên các cụm người dùng lặp lại (tên người, địa chỉ, câu chào) nhờ bigram/trigram có trọng số cao.
 *
 * Cũng ghi nhớ chữ hoa: âm tiết được gõ hoa nhiều lần (tên riêng) sẽ được gợi ý hoa.
 */
class PersonalModel : NgramModel {
    private val uni = HashMap<String, Int>()
    private val bi = HashMap<String, HashMap<String, Int>>()
    private val tri = HashMap<String, HashMap<String, Int>>()
    private val capCount = HashMap<String, Int>()
    private var total = 0L

    override val size: Long get() = total

    /** Ghi nhận một âm tiết đã gõ sau [context] (2 âm tiết trước, có thể ít hơn). */
    fun learn(context: List<String>, syllable: String) {
        val s = syllable.lowercase()
        if (s.isEmpty()) return
        total++
        uni[s] = (uni[s] ?: 0) + 1
        if (syllable.first().isUpperCase()) capCount[s] = (capCount[s] ?: 0) + 1
        val c1 = context.lastOrNull()?.lowercase()
        if (c1 != null) bi.getOrPut(c1) { HashMap() }.merge(s, 1, Int::plus)
        if (context.size >= 2) {
            val key = context[context.size - 2].lowercase() + " " + c1
            tri.getOrPut(key) { HashMap() }.merge(s, 1, Int::plus)
        }
    }

    /** Xoá một mục khỏi từ điển cá nhân (giữ một gợi ý rồi xoá). */
    fun forget(syllable: String) {
        val s = syllable.lowercase()
        uni.remove(s); capCount.remove(s)
        for (m in bi.values) m.remove(s)
        for (m in tri.values) m.remove(s)
    }

    fun clear() { uni.clear(); bi.clear(); tri.clear(); capCount.clear(); total = 0 }

    /** Âm tiết này thường được viết hoa (≥ 3 lần và > 60%). */
    fun usuallyCapitalized(syllable: String): Boolean {
        val s = syllable.lowercase()
        val c = capCount[s] ?: return false
        val n = uni[s] ?: return false
        return c >= 3 && c * 10 > n * 6
    }

    // ---- Số đếm cho Predictor (khoá đều là chữ thường) ----

    /** Số loại âm tiết khác nhau đã học. */
    val unigramTypes: Int get() = uni.size

    fun unigramCount(w: String): Int = uni[w] ?: 0

    /** Các âm tiết đứng ngay sau [prev], kèm số lần. */
    fun bigramFollowers(prev: String): Map<String, Int> = bi[prev] ?: emptyMap()

    /** Các âm tiết đứng ngay sau cặp ([prev2], [prev1]), kèm số lần. */
    fun trigramFollowers(prev2: String, prev1: String): Map<String, Int> = tri["$prev2 $prev1"] ?: emptyMap()

    /** Các âm tiết hay gõ nhất, bất kể ngữ cảnh. */
    fun topUnigrams(n: Int): List<String> = uni.entries.sortedByDescending { it.value }.take(n).map { it.key }

    override fun probability(context: List<String>, next: String): Double {
        val s = next.lowercase()
        val c1 = context.lastOrNull()?.lowercase()
        if (context.size >= 2) {
            val m = tri[context[context.size - 2].lowercase() + " " + c1]
            if (m != null) { val n = m[s]; if (n != null) return n.toDouble() / m.values.sum() }
        }
        if (c1 != null) {
            val m = bi[c1]
            if (m != null) { val n = m[s]; if (n != null) return 0.6 * n / m.values.sum() }
        }
        val u = uni[s] ?: return 0.0
        return 0.3 * u / total.coerceAtLeast(1)
    }

    override fun predict(context: List<String>, max: Int): List<NgramModel.Scored> {
        val cands = HashSet<String>()
        val c1 = context.lastOrNull()?.lowercase()
        if (context.size >= 2) tri[context[context.size - 2].lowercase() + " " + c1]?.keys?.let { cands.addAll(it) }
        if (c1 != null) bi[c1]?.keys?.let { cands.addAll(it) }
        if (cands.size < max) cands.addAll(uni.entries.sortedByDescending { it.value }.take(max * 3).map { it.key })
        return cands.map { NgramModel.Scored(it, probability(context, it)) }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(max)
    }

    /** Dạng text: mỗi dòng `kind<TAB>key<TAB>value<TAB>count`. */
    fun serialize(): String {
        val sb = StringBuilder()
        sb.append("TELEHEX-PERSONAL-1\n")
        for ((k, v) in uni) sb.append("u\t").append(k).append('\t').append(v).append('\n')
        for ((k, v) in capCount) sb.append("c\t").append(k).append('\t').append(v).append('\n')
        for ((k, m) in bi) for ((w, v) in m) sb.append("b\t").append(k).append('\t').append(w).append('\t').append(v).append('\n')
        for ((k, m) in tri) for ((w, v) in m) sb.append("t\t").append(k).append('\t').append(w).append('\t').append(v).append('\n')
        return sb.toString()
    }

    companion object {
        fun deserialize(text: String): PersonalModel {
            val m = PersonalModel()
            for (line in text.lineSequence()) {
                val p = line.split('\t')
                when (p.getOrNull(0)) {
                    "u" -> if (p.size >= 3) { val n = p[2].toIntOrNull() ?: 0; m.uni[p[1]] = n; m.total += n }
                    "c" -> if (p.size >= 3) m.capCount[p[1]] = p[2].toIntOrNull() ?: 0
                    "b" -> if (p.size >= 4) m.bi.getOrPut(p[1]) { HashMap() }[p[2]] = p[3].toIntOrNull() ?: 0
                    "t" -> if (p.size >= 4) m.tri.getOrPut(p[1]) { HashMap() }[p[2]] = p[3].toIntOrNull() ?: 0
                }
            }
            return m
        }
    }
}
