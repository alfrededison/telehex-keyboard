package com.telehex.telex.predict

import com.telehex.telex.ToneRules

/**
 * Gợi ý âm tiết tiếp theo, trộn mô hình gốc (gói ngôn ngữ) với mô hình cá nhân theo kiểu lùi bậc
 * Witten–Bell:
 *
 *   P(w | c2 c1) = (n(c2 c1 w) + T(c2 c1) · P(w | c1)) / (n(c2 c1) + T(c2 c1))
 *   P(w | c1)    = (n(c1 w)    + T(c1)    · P₀(w | ngữ cảnh)) / (n(c1) + T(c1))
 *
 * n là số lần người dùng đã gõ, T là số âm tiết khác nhau từng theo sau ngữ cảnh đó. Ngữ cảnh người
 * dùng chưa từng gõ (n = 0) thì lùi hẳn về P₀; ngữ cảnh gõ lặp lại nhiều lần thì thói quen cá nhân thắng.
 *
 * P₀ là mô hình gốc, vốn đã xét ngữ cảnh. Tần suất âm tiết của người dùng bất kể ngữ cảnh chỉ được
 * dùng ở đầu câu hoặc khi chưa có mô hình gốc: trộn nó vào mọi ngữ cảnh sẽ làm những âm tiết vừa gõ
 * (vốn chiếm tỉ lệ lớn khi mới học) chen lên trên gợi ý đúng ngữ cảnh.
 *
 * Có thêm bảng cụm từ cố định để ưu tiên hoàn thành từ ghép.
 */
class Predictor(
    var base: NgramModel? = null,
    val personal: PersonalModel = PersonalModel(),
    private val phrases: Map<String, List<String>> = Phrases.TABLE,
) {
    /**
     * Gợi ý âm tiết tiếp theo sau [context]. Nếu [prefix] khác rỗng (đang gõ dở), chỉ giữ ứng viên
     * bắt đầu bằng prefix (so sánh sau khi bỏ dấu thanh).
     */
    fun next(context: List<String>, prefix: String = "", max: Int = 3): List<String> {
        val ctx = context.takeLast(2).map { it.lowercase() }
        val b = base
        val candidates = LinkedHashSet<String>()
        if (b != null) b.predict(ctx, CANDIDATES).forEach { candidates += it.text }
        if (ctx.size >= 2) candidates += personal.trigramFollowers(ctx[0], ctx[1]).keys
        ctx.lastOrNull()?.let { candidates += personal.bigramFollowers(it).keys }
        if (b == null || ctx.isEmpty()) candidates += personal.topUnigrams(CANDIDATES)

        val scores = HashMap<String, Double>(candidates.size * 2)
        for (w in candidates) scores[w] = probability(ctx, w)
        // Cụm từ cố định: âm tiết trước gợi ý âm tiết sau với điểm cao
        ctx.lastOrNull()?.let { prev -> phrases[prev]?.forEachIndexed { i, s -> scores.merge(s, 0.5 - i * 0.05, Double::plus) } }

        val p = stripTones(prefix.lowercase())
        return scores.entries
            .asSequence()
            .filter { it.value > 0.0 }
            .filter { p.isEmpty() || stripTones(it.key).startsWith(p) }
            .filter { it.key != prefix.lowercase() }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(max)
            .toList()
    }

    /** Xác suất đã trộn của [w] sau [ctx] (tối đa 2 âm tiết, chữ thường). */
    internal fun probability(ctx: List<String>, w: String): Double {
        val b = base
        // P₀: mô hình gốc theo ngữ cảnh; không có thì tần suất cá nhân bất kể ngữ cảnh.
        var p = if (b != null) b.probability(ctx, w) else unigramMle(w)
        if (ctx.isEmpty()) {
            // Đầu câu: không có ngữ cảnh nào để lệch, nên trộn cả tần suất cá nhân.
            if (b != null) p = wittenBell(personal.unigramCount(w), personal.size.toInt(), personal.unigramTypes, p)
            return p
        }
        val c1 = ctx.last()
        p = wittenBell(personal.bigramFollowers(c1), w, p)
        if (ctx.size >= 2) p = wittenBell(personal.trigramFollowers(ctx[0], c1), w, p)
        return p
    }

    private fun unigramMle(w: String): Double {
        val total = personal.size
        return if (total == 0L) 0.0 else personal.unigramCount(w).toDouble() / total
    }

    private fun wittenBell(followers: Map<String, Int>, w: String, backoff: Double): Double =
        wittenBell(followers[w] ?: 0, followers.values.sum(), followers.size, backoff)

    private fun wittenBell(count: Int, total: Int, types: Int, backoff: Double): Double =
        if (total <= 0) backoff else (count + types * backoff) / (total + types)

    private fun stripTones(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(ToneRules.stripTone(c))
        return sb.toString()
    }

    private companion object {
        const val CANDIDATES = 20
    }
}

/** Cụm từ 2–3 âm tiết hay gặp: âm tiết đầu → các âm tiết tiếp theo thường đi cùng. */
object Phrases {
    private const val RAW = """
điện:thoại,tử,lực
cảm:ơn,giác,thấy
không:sao,có,được,phải,biết
xin:chào,lỗi,cảm
chúc:mừng,ngủ,may
hẹn:gặp
gặp:lại
bạn:bè
gia:đình
học:sinh,tập
sinh:viên,nhật
thời:gian,tiết
công:việc,ty
làm:việc
tiếng:việt,anh
việt:nam
hà:nội
sài:gòn
hôm:nay,qua
ngày:mai
buổi:sáng,chiều,tối
cuộc:sống,họp
thành:phố,công
sức:khỏe
hạnh:phúc
vui:vẻ
nhanh:chóng
cẩn:thận
mọi:người,thứ
chúng:ta,tôi
tất:cả
bao:nhiêu,giờ
như:thế,vậy
thế:nào
đồng:ý
"""
    val TABLE: Map<String, List<String>> by lazy {
        RAW.lineSequence().filter { ':' in it }.associate { line ->
            val (k, v) = line.split(':', limit = 2)
            k.trim() to v.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
