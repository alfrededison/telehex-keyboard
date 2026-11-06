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
 * P₀ là mô hình gốc, vốn đã xét ngữ cảnh và được chuẩn hoá theo ngữ cảnh (xem BinaryNgramModel). Tần
 * suất âm tiết của người dùng bất kể ngữ cảnh không được trộn vào giữa câu: những âm tiết vừa gõ (vốn
 * chiếm tỉ lệ lớn khi mới học) sẽ chen lên trên gợi ý đúng ngữ cảnh. Ở đầu câu chỉ trộn các âm tiết
 * đã gõ ít nhất [PersonalModel.FREQUENT] lần. Khi chưa có mô hình gốc thì tần suất cá nhân là P₀.
 *
 * Âm tiết chỉ có trong dữ liệu cá nhân (tên riêng, từ ngoài từ vựng của gói) nhận P₀ sàn bằng
 * [OOV_FLOOR] × tần suất cá nhân, để vẫn hoàn thành được khi đang gõ dở ở ngữ cảnh mới.
 *
 * Mỗi lần gõ phím đều gọi [next], nên phần chung của ngữ cảnh (khoảng hàng trong gói, các âm tiết
 * theo sau trong mô hình cá nhân) tính một lần, ứng viên giới hạn vài chục và lọc theo tiền tố trước
 * khi chấm điểm.
 *
 * Có thêm bảng cụm từ cố định để ưu tiên hoàn thành từ ghép.
 */
class Predictor(
    var base: NgramModel? = null,
    var personal: PersonalModel = PersonalModel(),
    private val phrases: Map<String, List<String>> = Phrases.TABLE,
) {
    /**
     * Gợi ý âm tiết tiếp theo sau [context]. Nếu [prefix] khác rỗng (đang gõ dở), chỉ giữ ứng viên
     * bắt đầu bằng prefix (so sánh sau khi bỏ dấu thanh).
     */
    fun next(context: List<String>, prefix: String = "", max: Int = 3): List<String> {
        val ctx = context.takeLast(2).map { it.lowercase() }
        val typed = prefix.lowercase()
        val key = stripTones(typed)
        val scorer = base?.scorer(ctx)
        val tri = if (ctx.size >= 2) personal.trigram(ctx[0], ctx[1]) else null
        val bi = ctx.lastOrNull()?.let { personal.bigram(it) }
        val phraseList = ctx.lastOrNull()?.let { phrases[it] }.orEmpty()

        val candidates = LinkedHashSet<String>()
        // Đang gõ dở thì lấy rộng hơn từ gói: ứng viên theo ngữ cảnh chưa chắc khớp tiền tố.
        scorer?.let { candidates += it.candidates(if (key.isEmpty()) BASE_CANDIDATES else BASE_CANDIDATES_TYPING) }
        tri?.let { candidates += it.top(PERSONAL_CANDIDATES) }
        bi?.let { candidates += it.top(PERSONAL_CANDIDATES) }
        when {
            scorer == null -> candidates += personal.topUnigrams(PERSONAL_CANDIDATES)
            ctx.isEmpty() -> candidates += personal.topFrequent(PERSONAL_CANDIDATES)
        }
        if (key.isNotEmpty()) candidates += personal.wordsWithPrefix(key, PERSONAL_CANDIDATES)
        candidates += phraseList

        val scores = ArrayList<Pair<String, Double>>(candidates.size)
        for (w in candidates) {
            if (w == typed || (key.isNotEmpty() && !stripTones(w).startsWith(key))) continue
            var p = mixed(w, ctx.isEmpty(), scorer, bi, tri)
            // Cụm từ cố định: âm tiết trước gợi ý âm tiết sau với điểm cao
            val phrase = phraseList.indexOf(w)
            if (phrase >= 0) p += 0.5 - phrase * 0.05
            if (p > 0.0) scores += w to p
        }
        return scores.sortedByDescending { it.second }.take(max).map { it.first }
    }

    /** Xác suất đã trộn của [w], với phần chung của ngữ cảnh đã tính sẵn. */
    private fun mixed(
        w: String,
        sentenceStart: Boolean,
        scorer: NgramModel.Scorer?,
        bi: PersonalModel.Followers?,
        tri: PersonalModel.Followers?,
    ): Double {
        var p = if (scorer != null) scorer.probability(w) else unigramMle(w)
        if (scorer != null && p == 0.0) p = OOV_FLOOR * unigramMle(w)
        if (sentenceStart) {
            if (scorer != null) {
                // Đầu câu: chỉ những âm tiết gõ đủ nhiều lần mới được trộn tần suất cá nhân.
                val (total, types) = personal.frequent
                val n = personal.unigramCount(w).takeIf { it >= PersonalModel.FREQUENT } ?: 0
                p = wittenBell(n.toLong(), total, types, p)
            }
            return p
        }
        if (bi != null) p = wittenBell(bi.count(w).toLong(), bi.total.toLong(), bi.types, p)
        if (tri != null) p = wittenBell(tri.count(w).toLong(), tri.total.toLong(), tri.types, p)
        return p
    }

    private fun unigramMle(w: String): Double {
        val total = personal.size
        return if (total == 0L) 0.0 else personal.unigramCount(w).toDouble() / total
    }

    private fun wittenBell(count: Long, total: Long, types: Int, backoff: Double): Double =
        if (total <= 0) backoff else (count + types * backoff) / (total + types)

    private fun stripTones(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(ToneRules.stripTone(c))
        return sb.toString()
    }

    private companion object {
        const val BASE_CANDIDATES = 20
        const val BASE_CANDIDATES_TYPING = 50
        const val PERSONAL_CANDIDATES = 20
        /** P₀ cho âm tiết ngoài từ vựng của gói = hệ số này × tần suất cá nhân. */
        const val OOV_FLOOR = 0.1
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
