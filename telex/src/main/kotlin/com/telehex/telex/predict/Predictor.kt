package com.telehex.telex.predict

import com.telehex.telex.ToneRules
import com.telehex.telex.CommonSyllables

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
    var phrases: Map<String, List<String>> = emptyMap(),
    var vocabulary: VietnameseVocabulary? = null,
) {
    /** Từ vựng cho vuốt: dùng được ngay khi chưa nạp gói, mở rộng bằng gói và từ đã học. */
    fun glideWords(context: List<String>): List<NgramModel.Scored> {
        val ctx = context.takeLast(2).map { it.lowercase() }
        val scorer = base?.scorer(ctx)
        val bi = ctx.lastOrNull()?.let { personal.bigram(it) }
        val tri = if (ctx.size == 2) personal.trigram(ctx[0], ctx[1]) else null
        val phrases = ctx.lastOrNull()?.let { phrases[it] }.orEmpty()
        val words = LinkedHashSet(CommonSyllables.RANK.keys)
        vocabulary?.let { words += it.words }
        words += personal.vocabulary()
        words += (base as? BinaryNgramModel)?.vocabulary.orEmpty()
        scorer?.let { words += it.candidates(100) }
        words += phrases
        return words.map { word ->
            val rank = vocabulary?.rankOf(word)?.takeIf { it != Int.MAX_VALUE } ?: CommonSyllables.rankOf(word)
            val fallback = if (rank == Int.MAX_VALUE) 0.00001 else 0.01 / (1 + rank / 40.0)
            val phrase = if (word in phrases) 0.4 else 0.0
            NgramModel.Scored(word, maxOf(fallback, mixed(word, ctx.isEmpty(), scorer, bi, tri)) + phrase)
        }
    }

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
        if (scorer == null) candidates += vocabulary?.complete(key,
            if (key.isEmpty()) BASE_CANDIDATES else BASE_CANDIDATES_TYPING).orEmpty()
        candidates += phraseList

        val scores = ArrayList<Pair<String, Double>>(candidates.size)
        for (w in candidates) {
            if (w == typed || (key.isNotEmpty() && !stripTones(w).startsWith(key))) continue
            var p = mixed(w, ctx.isEmpty(), scorer, bi, tri)
            if (scorer == null) p = maxOf(p, vocabulary?.prior(w) ?: 0.0)
            // Cụm từ cố định: âm tiết trước gợi ý âm tiết sau với điểm cao
            val phrase = phraseList.indexOf(w)
            if (phrase >= 0) p += 0.5 - phrase * 0.05
            if (p > 0.0) scores += w to p
        }
        return scores.sortedByDescending { it.second }.take(max).map { it.first }
    }

    /** Chuẩn bị phần chung một lần cho mọi ứng viên sửa lỗi; không có ngữ cảnh thì giữ xếp hạng cũ. */
    fun correctionScorer(context: List<String>): (String) -> Double {
        val ctx = context.takeLast(2).map { it.lowercase() }
        if (ctx.isEmpty()) return { 0.0 }
        val scorer = base?.scorer(ctx)
        val bi = personal.bigram(ctx.last())?.takeIf { it.total > 0 }
        val tri = if (ctx.size == 2) personal.trigram(ctx[0], ctx[1])?.takeIf { it.total > 0 } else null
        if (scorer == null && bi == null && tri == null) return { 0.0 }
        return { word -> mixed(word.lowercase(), false, scorer, bi, tri) }
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

/** Đọc bảng cụm từ từ asset: âm tiết đầu<TAB>các âm tiết theo sau, phân cách dấu phẩy. */
object Phrases {
    fun parse(text: String): Map<String, List<String>> =
        text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
            val columns = line.split('\t', limit = 2)
            require(columns.size == 2 && columns[0].isNotBlank()) { "Dòng cụm từ không hợp lệ" }
            val followers = columns[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
            require(followers.isNotEmpty()) { "Cụm từ thiếu âm tiết theo sau" }
            columns[0].trim() to followers
        }
}
