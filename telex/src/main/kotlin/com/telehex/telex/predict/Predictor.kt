package com.telehex.telex.predict

import com.telehex.telex.ToneRules

/**
 * Trộn mô hình gốc với mô hình cá nhân; trọng số cá nhân tăng dần khi dữ liệu nhiều lên.
 * Có thêm bảng cụm từ cố định để ưu tiên hoàn thành từ ghép.
 */
class Predictor(
    var base: NgramModel? = null,
    val personal: PersonalModel = PersonalModel(),
    private val phrases: Map<String, List<String>> = Phrases.TABLE,
) {
    /** Trọng số mô hình cá nhân: 0.2 khi mới, tiến tới 0.7 khi đã học ≥ ~2000 âm tiết. */
    private fun personalWeight(): Double {
        val n = personal.size.toDouble()
        return 0.2 + 0.5 * (n / (n + 2000.0))
    }

    /**
     * Gợi ý âm tiết tiếp theo sau [context]. Nếu [prefix] khác rỗng (đang gõ dở), chỉ giữ ứng viên
     * bắt đầu bằng prefix (so sánh sau khi bỏ dấu thanh).
     */
    fun next(context: List<String>, prefix: String = "", max: Int = 3): List<String> {
        val ctx = context.takeLast(2).map { it.lowercase() }
        val scores = HashMap<String, Double>()
        val wp = personalWeight()
        val b = base
        for (s in personal.predict(ctx, 12)) scores.merge(s.text, wp * s.score, Double::plus)
        if (b != null) for (s in b.predict(ctx, 12)) scores.merge(s.text, (1 - wp) * s.score, Double::plus)
        // Cụm từ cố định: âm tiết trước gợi ý âm tiết sau với điểm cao
        ctx.lastOrNull()?.let { prev -> phrases[prev]?.forEachIndexed { i, s -> scores.merge(s, 0.5 - i * 0.05, Double::plus) } }
        val p = stripTones(prefix.lowercase())
        return scores.entries
            .asSequence()
            .filter { p.isEmpty() || stripTones(it.key).startsWith(p) }
            .filter { it.key != prefix.lowercase() }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(max)
            .toList()
    }

    private fun stripTones(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(ToneRules.stripTone(c))
        return sb.toString()
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
