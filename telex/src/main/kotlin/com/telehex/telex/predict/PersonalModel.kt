package com.telehex.telex.predict

import com.telehex.telex.ToneRules
import java.io.BufferedReader
import java.io.StringReader

/**
 * Mô hình cá nhân: đếm n-gram (1–3) từ chính người dùng, hoàn toàn trên máy.
 *
 * Lưu gọn: mỗi âm tiết có một id số; số đếm nằm trong bảng băm số nguyên ([IntCounts]) theo ngữ cảnh,
 * không dùng chuỗi hay số nguyên đóng hộp cho từng mục. Tổng, số loại và các số liệu Predictor cần ở
 * mỗi phím được cập nhật dần khi học, nên truy vấn không phải đếm lại.
 *
 * Cũng ghi nhớ chữ hoa: âm tiết được gõ hoa nhiều lần (tên riêng) sẽ được gợi ý hoa.
 *
 * Định dạng lưu (giữ nguyên để đọc được dữ liệu và bản sao lưu cũ): dòng đầu `TELEHEX-PERSONAL-1`, rồi
 * `u<TAB>âm tiết<TAB>số`, `c<TAB>âm tiết<TAB>số lần hoa`, `b<TAB>trước<TAB>âm tiết<TAB>số`,
 * `t<TAB>trước2 trước1<TAB>âm tiết<TAB>số`.
 */
class PersonalModel {
    private val ids = HashMap<String, Int>()
    private val words = ArrayList<String>()
    private var uni = IntArray(64)
    private var caps = IntArray(64)
    /** Dạng bỏ dấu thanh của từng id, tính một lần (cho tra theo tiền tố). */
    private val keys = ArrayList<String>()
    private val bi = ArrayList<IntCounts?>()
    private var tri = LongMap<IntCounts>()
    private var entryCount = 0

    private var total = 0L
    private var frequentTotal = 0L
    private var frequentTypes = 0

    /** Số âm tiết đã học (tổng số lần). */
    val size: Long get() = total

    /** Số cặp và bộ ba khác nhau đang lưu (để giới hạn kích thước). */
    val entries: Int get() = entryCount

    // ---- Học ----

    private fun idOf(w: String): Int = ids.getOrPut(w) {
        val id = words.size
        words.add(w)
        keys.add(strip(w))
        if (id >= uni.size) { uni = uni.copyOf(uni.size * 2); caps = caps.copyOf(caps.size * 2) }
        bi.add(null)
        id
    }

    /** Ghi nhận một âm tiết đã gõ sau [context] (2 âm tiết trước, có thể ít hơn). */
    fun learn(context: List<String>, syllable: String) {
        val s = syllable.lowercase()
        if (s.isEmpty()) return
        val w = idOf(s)
        addUnigram(w, 1)
        topCache = null
        if (syllable.first().isUpperCase()) caps[w]++
        val c1 = context.lastOrNull()?.lowercase()?.let { idOf(it) } ?: return
        count(bi[c1] ?: IntCounts().also { bi[c1] = it }, w, 1)
        if (context.size >= 2) {
            val c2 = idOf(context[context.size - 2].lowercase())
            count(tri.getOrPut(pair(c2, c1)) { IntCounts() }, w, 1)
        }
    }

    /** Cộng vào một bảng theo sau, giữ [entryCount] đúng. */
    private fun count(counts: IntCounts, w: Int, n: Int) {
        val before = counts.size
        counts.add(w, n)
        entryCount += counts.size - before
    }

    private fun addUnigram(w: Int, delta: Int) {
        val before = uni[w]
        val after = before + delta
        uni[w] = after
        total += delta
        // Số liệu của các âm tiết gõ đủ nhiều lần (dùng ở đầu câu)
        if (before >= FREQUENT) { frequentTotal -= before; frequentTypes-- }
        if (after >= FREQUENT) { frequentTotal += after; frequentTypes++ }
    }

    /** Xoá một âm tiết khỏi từ điển cá nhân (giữ một gợi ý rồi xoá). */
    fun forget(syllable: String) {
        val w = ids[syllable.lowercase()] ?: return
        if (uni[w] > 0) addUnigram(w, -uni[w])
        caps[w] = 0
        for (m in bi) if (m != null) { val before = m.size; m.remove(w); entryCount += m.size - before }
        tri.forEach { _, m -> val before = m.size; m.remove(w); entryCount += m.size - before }
        topCache = null
    }

    fun clear() {
        ids.clear(); words.clear(); keys.clear(); bi.clear(); tri.clear()
        uni = IntArray(64); caps = IntArray(64)
        total = 0; frequentTotal = 0; frequentTypes = 0; entryCount = 0
        topCache = null; prefixCount = 0
    }

    /**
     * Giữ mô hình không lớn mãi: khi số cặp + bộ ba vượt [maxEntries], chia đôi mọi số đếm và bỏ mục về 0.
     * Thói quen lâu không dùng nhạt dần, thói quen mới giữ ưu thế. Trả về true nếu đã giảm.
     */
    fun decayIfLargerThan(maxEntries: Int): Boolean {
        if (entries <= maxEntries) return false
        entryCount = 0
        for (i in bi.indices) bi[i]?.let { it.halve(); if (it.size == 0) bi[i] = null else entryCount += it.size }
        tri.forEach { _, m -> m.halve(); entryCount += m.size }
        tri.removeIf { it.size == 0 }
        total = 0; frequentTotal = 0; frequentTypes = 0
        for (w in words.indices) {
            val c = uni[w] / 2
            uni[w] = 0; caps[w] /= 2
            if (c > 0) addUnigram(w, c)
        }
        topCache = null
        return true
    }

    // ---- Truy vấn cho Predictor (khoá đều là chữ thường) ----

    /** Âm tiết này thường được viết hoa (≥ 3 lần và > 60%). */
    fun usuallyCapitalized(syllable: String): Boolean {
        val w = ids[syllable.lowercase()] ?: return false
        val c = caps[w]; val n = uni[w]
        return c >= 3 && c * 10 > n * 6
    }

    fun unigramCount(w: String): Int = ids[w]?.let { uni[it] } ?: 0

    /** Các âm tiết đứng ngay sau [prev]; null nếu chưa từng gõ gì sau nó. */
    fun bigram(prev: String): Followers? = ids[prev]?.let { bi.getOrNull(it) }?.let { Followers(it) }

    /** Các âm tiết đứng ngay sau cặp ([prev2], [prev1]); null nếu chưa từng gõ. */
    fun trigram(prev2: String, prev1: String): Followers? {
        val a = ids[prev2] ?: return null
        val b = ids[prev1] ?: return null
        return tri[pair(a, b)]?.let { Followers(it) }
    }

    /** Số liệu các âm tiết gõ ít nhất [FREQUENT] lần: (tổng số lần, số loại). */
    val frequent: Pair<Long, Int> get() = frequentTotal to frequentTypes

    private var topCache: List<String>? = null

    /** Tối đa [k] âm tiết gõ nhiều nhất trong số các âm tiết gõ ít nhất [FREQUENT] lần. */
    fun topFrequent(k: Int): List<String> {
        topCache?.let { if (it.size >= k || it.size == frequentTypes) return it.take(k) }
        val best = TopK(k)
        for (w in words.indices) if (uni[w] >= FREQUENT) best.offer(w, uni[w])
        return best.ids().map { words[it] }.also { topCache = it }
    }

    /** Tối đa [k] âm tiết gõ nhiều nhất, bất kể số lần. */
    fun topUnigrams(k: Int): List<String> {
        val best = TopK(k)
        for (w in words.indices) if (uni[w] > 0) best.offer(w, uni[w])
        return best.ids().map { words[it] }
    }

    /** Tối đa [k] âm tiết đã học có dạng bỏ dấu bắt đầu bằng [prefixKey] (đã bỏ dấu, chữ thường), nhiều nhất trước. */
    fun wordsWithPrefix(prefixKey: String, k: Int): List<String> {
        if (prefixKey.isEmpty()) return emptyList()
        ensurePrefixIndex()
        val best = TopK(k)
        var i = lowerBound(prefixKey)
        while (i < prefixCount && keys[prefixIds[i]].startsWith(prefixKey)) {
            val w = prefixIds[i]
            if (uni[w] > 0) best.offer(w, uni[w])
            i++
        }
        return best.ids().map { words[it] }
    }

    /** Các id sắp theo dạng bỏ dấu; [prefixCount] id đầu tiên đã được xếp. */
    private var prefixIds = IntArray(64)
    private var prefixCount = 0

    private fun lowerBound(key: String): Int {
        var lo = 0; var hi = prefixCount
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (keys[prefixIds[mid]] < key) lo = mid + 1 else hi = mid }
        return lo
    }

    /**
     * Cập nhật chỉ mục tiền tố: vài âm tiết mới thì chèn từng cái (O(n) mỗi cái), nhiều thì sắp lại một
     * lần. Dạng bỏ dấu đã tính sẵn trong [keys], nên so sánh không tạo chuỗi mới.
     */
    internal fun ensurePrefixIndex() {
        val n = words.size
        if (prefixCount == n) return
        if (prefixIds.size < n) prefixIds = prefixIds.copyOf(maxOf(n, prefixIds.size * 2))
        if (n - prefixCount <= 64) {
            for (id in prefixCount until n) {
                val at = lowerBound(keys[id])
                System.arraycopy(prefixIds, at, prefixIds, at + 1, prefixCount - at)
                prefixIds[at] = id
                prefixCount++
            }
        } else {
            val sorted = (0 until n).sortedWith { a, b -> keys[a].compareTo(keys[b]) }
            for (i in 0 until n) prefixIds[i] = sorted[i]
            prefixCount = n
        }
    }

    /** Bản sao độc lập (để ghi ra file trên luồng khác trong khi vẫn học tiếp). */
    fun copy(): PersonalModel {
        val m = PersonalModel()
        m.ids.putAll(ids); m.words.addAll(words); m.keys.addAll(keys)
        m.uni = uni.copyOf(); m.caps = caps.copyOf()
        for (c in bi) m.bi.add(c?.copy())
        m.tri = tri.copy { it.copy() }
        m.total = total; m.frequentTotal = frequentTotal; m.frequentTypes = frequentTypes
        m.entryCount = entryCount
        return m
    }

    // ---- Lưu / nạp ----

    fun serialize(): String = StringBuilder(64 * 1024).also { writeTo(it) }.toString()

    /** Ghi dần ra [out] (ví dụ một BufferedWriter), không dựng cả chuỗi lớn trong bộ nhớ. */
    fun writeTo(out: Appendable) {
        out.append(HEADER).append('\n')
        for (w in words.indices) if (uni[w] > 0) out.append("u\t").append(words[w]).append('\t').append(uni[w].toString()).append('\n')
        for (w in words.indices) if (caps[w] > 0) out.append("c\t").append(words[w]).append('\t').append(caps[w].toString()).append('\n')
        for (c1 in bi.indices) bi[c1]?.forEach { w, n ->
            out.append("b\t").append(words[c1]).append('\t').append(words[w]).append('\t').append(n.toString()).append('\n')
        }
        tri.forEach { key, m ->
            val ctx = words[(key ushr 32).toInt()] + " " + words[key.toInt()]
            m.forEach { w, n -> out.append("t\t").append(ctx).append('\t').append(words[w]).append('\t').append(n.toString()).append('\n') }
        }
    }

    /** Một cặp hay bộ ba, xem như các âm tiết theo sau một ngữ cảnh. */
    inner class Followers internal constructor(private val counts: IntCounts) {
        /** Tổng số lần đã gõ sau ngữ cảnh. */
        val total: Int get() = counts.total
        /** Số âm tiết khác nhau từng theo sau. */
        val types: Int get() = counts.size
        fun count(w: String): Int = ids[w]?.let { counts.get(it) } ?: 0
        /** Tối đa [k] âm tiết theo sau nhiều nhất. */
        fun top(k: Int): List<String> {
            val best = TopK(k)
            counts.forEach { w, n -> best.offer(w, n) }
            return best.ids().map { words[it] }
        }
    }

    companion object {
        private const val HEADER = "TELEHEX-PERSONAL-1"

        /** Số lần tối thiểu để một âm tiết được trộn vào gợi ý đầu câu bất kể ngữ cảnh. */
        const val FREQUENT = 3

        private fun pair(a: Int, b: Int): Long = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)

        internal fun strip(s: String): String {
            val sb = StringBuilder(s.length)
            for (c in s) sb.append(ToneRules.stripTone(c))
            return sb.toString()
        }

        fun deserialize(text: String): PersonalModel = read(BufferedReader(StringReader(text)))

        /** Đọc dần từ [reader]; dòng hỏng hoặc lạ bị bỏ qua. */
        fun read(reader: BufferedReader): PersonalModel {
            val m = PersonalModel()
            while (true) {
                val line = reader.readLine() ?: break
                val p = line.split('\t')
                when (p[0]) {
                    "u" -> if (p.size >= 3) { val n = p[2].toIntOrNull() ?: 0; if (n > 0) m.addUnigram(m.idOf(p[1]), n) }
                    // id trước: idOf có thể nới mảng caps, nên đừng lấy m.caps trước khi gọi nó
                    "c" -> if (p.size >= 3) { val id = m.idOf(p[1]); m.caps[id] = p[2].toIntOrNull() ?: 0 }
                    "b" -> if (p.size >= 4) {
                        val n = p[3].toIntOrNull() ?: 0
                        if (n > 0) {
                            val c1 = m.idOf(p[1]); val w = m.idOf(p[2])
                            m.count(m.bi[c1] ?: IntCounts().also { m.bi[c1] = it }, w, n)
                        }
                    }
                    "t" -> if (p.size >= 4) {
                        val n = p[3].toIntOrNull() ?: 0
                        val sp = p[1].indexOf(' ')
                        if (n > 0 && sp > 0) {
                            val key = pair(m.idOf(p[1].substring(0, sp)), m.idOf(p[1].substring(sp + 1)))
                            val w = m.idOf(p[2])
                            m.count(m.tri.getOrPut(key) { IntCounts() }, w, n)
                        }
                    }
                }
            }
            m.ensurePrefixIndex() // sắp chỉ mục một lần ngay trên luồng đang nạp (luồng nền)
            return m
        }
    }
}

/** Chọn tối đa k id có số đếm lớn nhất (chèn có giới hạn, O(n·k) với k nhỏ). */
internal class TopK(private val k: Int) {
    private val ids = IntArray(k)
    private val counts = IntArray(k)
    private var n = 0

    fun offer(id: Int, count: Int) {
        if (k == 0 || (n == k && count <= counts[k - 1])) return
        var j = if (n < k) n++ else k - 1
        while (j > 0 && counts[j - 1] < count) { counts[j] = counts[j - 1]; ids[j] = ids[j - 1]; j-- }
        counts[j] = count; ids[j] = id
    }

    fun ids(): List<Int> = ids.asList().subList(0, n)
}

/**
 * Hạt giống hash riêng cho từng bảng. Nếu mọi bảng dùng chung một hàm hash, chép các khoá theo thứ tự
 * ô của bảng này sang một bảng khác đang lớn dần (khi copy, khi đọc file do chính hàm hash đó sắp
 * thứ tự) sẽ dồn khoá thành chuỗi dò rất dài, tốn gần bình phương thời gian.
 */
private val seeds = java.util.concurrent.atomic.AtomicInteger(java.util.Random().nextInt())
private fun nextSeed(): Int = seeds.getAndAdd(-0x61c88647)

/** Bảng băm int → int (địa chỉ mở, dò tuyến tính), kèm tổng số đếm. Khoá là id âm tiết (≥ 0). */
internal class IntCounts private constructor(capacity: Int, private val seed: Int) {
    constructor() : this(2, nextSeed())

    private var keys = IntArray(capacity) { EMPTY }
    private var vals = IntArray(capacity)
    var size = 0; private set
    var total = 0; private set

    /** Số ô (cho test). */
    internal val capacity: Int get() = keys.size

    private fun slot(k: Int, mask: Int): Int = ((k xor seed) * -0x61c88647) ushr 7 and mask

    fun get(k: Int): Int {
        val mask = keys.size - 1
        var i = slot(k, mask)
        while (true) {
            val key = keys[i]
            if (key == k) return vals[i]
            if (key == EMPTY) return 0
            i = (i + 1) and mask
        }
    }

    fun add(k: Int, delta: Int) {
        val mask = keys.size - 1
        var i = slot(k, mask)
        while (true) {
            val key = keys[i]
            if (key == k) { vals[i] += delta; total += delta; return }
            if (key == EMPTY) break
            i = (i + 1) and mask
        }
        // Chỉ nới bảng khi thật sự thêm khoá mới
        if ((size + 1) * 4 > keys.size * 3) { grow(); insertNew(k, delta) }
        else { keys[i] = k; vals[i] = delta; size++; total += delta }
    }

    private fun insertNew(k: Int, v: Int) {
        val mask = keys.size - 1
        var i = slot(k, mask)
        while (keys[i] != EMPTY) i = (i + 1) and mask
        keys[i] = k; vals[i] = v; size++; total += v
    }

    /** Xoá khoá (dịch lùi để giữ chuỗi dò liền mạch). */
    fun remove(k: Int) {
        val mask = keys.size - 1
        var i = slot(k, mask)
        while (keys[i] != k) { if (keys[i] == EMPTY) return; i = (i + 1) and mask }
        total -= vals[i]; size--
        var j = i
        while (true) {
            j = (j + 1) and mask
            if (keys[j] == EMPTY) break
            val home = slot(keys[j], mask)
            // keys[j] có thể dời về i nếu i nằm trên đường dò từ home tới j
            if ((if (j > i) home <= i || home > j else home <= i && home > j)) {
                keys[i] = keys[j]; vals[i] = vals[j]; i = j
            }
        }
        keys[i] = EMPTY; vals[i] = 0
    }

    fun forEach(f: (Int, Int) -> Unit) {
        for (i in keys.indices) { val k = keys[i]; if (k != EMPTY) f(k, vals[i]) }
    }

    /** Bản sao cùng bố cục: chép thẳng mảng, không băm lại. */
    fun copy(): IntCounts {
        val c = IntCounts(keys.size, seed)
        keys.copyInto(c.keys); vals.copyInto(c.vals); c.size = size; c.total = total
        return c
    }

    /** Chia đôi mọi số đếm, bỏ mục về 0 (giữ dung lượng). */
    fun halve() {
        val oldK = keys; val oldV = vals
        keys = IntArray(oldK.size) { EMPTY }; vals = IntArray(oldK.size); size = 0; total = 0
        for (i in oldK.indices) if (oldK[i] != EMPTY && oldV[i] / 2 > 0) insertNew(oldK[i], oldV[i] / 2)
    }

    private fun grow() {
        val oldK = keys; val oldV = vals
        keys = IntArray(oldK.size * 2) { EMPTY }; vals = IntArray(oldK.size * 2); size = 0; total = 0
        for (i in oldK.indices) if (oldK[i] != EMPTY) insertNew(oldK[i], oldV[i])
    }

    companion object { const val EMPTY = -1 }
}

/** Bảng băm long → V (địa chỉ mở), cho ngữ cảnh hai âm tiết của bộ ba. */
internal class LongMap<V : Any> private constructor(capacity: Int, private val seed: Long) {
    constructor() : this(16, nextSeed().toLong())

    private var keys = LongArray(capacity) { EMPTY }
    private var vals = arrayOfNulls<Any>(capacity)
    var size = 0; private set

    private fun slot(k: Long, mask: Int): Int = (((k xor seed) * -0x61c8864680b583ebL) ushr 40).toInt() and mask

    @Suppress("UNCHECKED_CAST")
    operator fun get(k: Long): V? {
        val mask = keys.size - 1
        var i = slot(k, mask)
        while (true) {
            if (keys[i] == k) return vals[i] as V
            if (keys[i] == EMPTY) return null
            i = (i + 1) and mask
        }
    }

    fun put(k: Long, v: V) {
        val mask = keys.size - 1
        var i = slot(k, mask)
        while (keys[i] != EMPTY) {
            if (keys[i] == k) { vals[i] = v; return }
            i = (i + 1) and mask
        }
        if ((size + 1) * 4 > keys.size * 3) { grow(); insertNew(k, v) }
        else { keys[i] = k; vals[i] = v; size++ }
    }

    private fun insertNew(k: Long, v: Any) {
        val mask = keys.size - 1
        var i = slot(k, mask)
        while (keys[i] != EMPTY) i = (i + 1) and mask
        keys[i] = k; vals[i] = v; size++
    }

    fun getOrPut(k: Long, make: () -> V): V = get(k) ?: make().also { put(k, it) }

    @Suppress("UNCHECKED_CAST")
    fun forEach(f: (Long, V) -> Unit) { for (i in keys.indices) if (keys[i] != EMPTY) f(keys[i], vals[i] as V) }

    fun values(): List<V> { val out = ArrayList<V>(size); forEach { _, v -> out.add(v) }; return out }

    fun clear() { keys = LongArray(16) { EMPTY }; vals = arrayOfNulls(16); size = 0 }

    /** Bản sao cùng bố cục (chép thẳng mảng khoá), giá trị sao bằng [copyValue]. */
    @Suppress("UNCHECKED_CAST")
    fun copy(copyValue: (V) -> V): LongMap<V> {
        val m = LongMap<V>(keys.size, seed)
        keys.copyInto(m.keys)
        for (i in vals.indices) vals[i]?.let { m.vals[i] = copyValue(it as V) }
        m.size = size
        return m
    }

    /** Bỏ các mục thoả [drop]: dựng lại ở cùng dung lượng (dùng hiếm, khi giảm kích thước). */
    fun removeIf(drop: (V) -> Boolean) {
        val oldK = keys; val oldV = vals
        keys = LongArray(oldK.size) { EMPTY }; vals = arrayOfNulls(oldK.size); size = 0
        @Suppress("UNCHECKED_CAST")
        for (i in oldK.indices) if (oldK[i] != EMPTY && !drop(oldV[i] as V)) insertNew(oldK[i], oldV[i]!!)
    }

    private fun grow() {
        val oldK = keys; val oldV = vals
        keys = LongArray(oldK.size * 2) { EMPTY }; vals = arrayOfNulls(oldK.size * 2); size = 0
        for (i in oldK.indices) if (oldK[i] != EMPTY) insertNew(oldK[i], oldV[i]!!)
    }

    companion object { const val EMPTY = Long.MIN_VALUE }
}
