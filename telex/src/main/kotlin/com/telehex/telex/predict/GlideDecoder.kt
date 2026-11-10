package com.telehex.telex.predict

import com.telehex.telex.TelexConfig
import com.telehex.telex.TelexDecomposer
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln

/** So khớp hình dạng đường vuốt, không coi các phím đi ngang qua là chữ người dùng muốn gõ. */
class GlideDecoder {
    data class Point(val x: Float, val y: Float) {
        fun distance(other: Point): Float = hypot(x - other.x, y - other.y)
    }

    /** Toạ độ tính theo bề rộng một phím; cùng thuật toán cho mọi profile và chế độ một tay. */
    fun suggest(
        trace: List<Point>,
        centers: Map<Char, Point>,
        words: List<NgramModel.Scored>,
        config: TelexConfig = TelexConfig(),
        max: Int = 6,
        english: Boolean = false,
    ): List<String> {
        if (trace.size < 2 || max <= 0) return emptyList()
        val length = length(trace)
        if (length < 1.2f) return emptyList()
        val sampled = resample(trace)
        val scores = ArrayList<Pair<String, Double>>()
        for ((word, prior) in words) {
            if (word.length !in 1..24) continue
            // EN lướt đúng các chữ, bỏ dấu nháy (dont → don't). VI có thể lướt chữ không dấu
            // (tieng → tiếng), hoặc đủ phím Telex (tieengs).
            val spellings = if (english) listOf(word.lowercase().replace("'", "")) else listOfNotNull(
                Normalizer.normalize(word.lowercase().replace('đ', 'd'), Normalizer.Form.NFD)
                    .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() },
                TelexDecomposer.keysOf(word, config)?.first,
            ).distinct()
            var best = Double.POSITIVE_INFINITY
            for (spelling in spellings) {
                // Loại sớm theo phím đầu/cuối trước khi dựng đường: phần lớn từ điển rớt ở đây
                val first = centers[spelling.firstOrNull() ?: continue] ?: continue
                val last = centers[spelling.last()] ?: continue
                if (trace.first().distance(first) > 0.85f || trace.last().distance(last) > 0.85f) continue
                if (spelling.any { it !in centers }) continue
                val path = spelling.map { centers.getValue(it) }.fold(ArrayList<Point>()) { out, p ->
                    if (out.lastOrNull() != p) out.add(p)
                    out
                }
                if (path.size < 2) continue
                val start = trace.first().distance(path.first())
                val end = trace.last().distance(path.last())
                if (start > 0.85f || end > 0.85f) continue
                val expectedLength = length(path)
                if (length > expectedLength * 2.2f + 1 || expectedLength > length * 2.2f + 1) continue
                val shape = distance(sampled, resample(path))
                if (shape > 0.65) continue
                val score = shape + (start * start + end * end) * 0.35 +
                    abs(ln((length / expectedLength).toDouble())) * 0.12 -
                    ln(prior.coerceIn(0.000001, 1.0)) * 0.025
                best = minOf(best, score)
            }
            if (best.isFinite()) scores += word to best
        }
        return scores.sortedBy { it.second }.distinctBy { it.first }.take(max).map { it.first }
    }

    private fun length(points: List<Point>): Float = points.zipWithNext { a, b -> a.distance(b) }.sum()

    /** Lấy mẫu theo quãng đường: giữ lâu ở một phím không làm lệch điểm. */
    private fun resample(points: List<Point>): List<Point> {
        val total = length(points)
        if (total == 0f) return List(SAMPLES) { points.first() }
        var segment = 1
        var before = 0f
        return List(SAMPLES) { i ->
            val target = total * i / (SAMPLES - 1)
            while (segment < points.lastIndex && before + points[segment - 1].distance(points[segment]) < target) {
                before += points[segment - 1].distance(points[segment])
                segment++
            }
            val a = points[segment - 1]; val b = points[segment]
            val d = a.distance(b)
            val t = if (d == 0f) 0f else ((target - before) / d).coerceIn(0f, 1f)
            Point(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
    }

    /** Dynamic time warping cho phép ngón tay bo góc và đi qua mỗi đoạn với tốc độ khác nhau. */
    private fun distance(a: List<Point>, b: List<Point>): Double {
        var previous = DoubleArray(b.size + 1) { Double.POSITIVE_INFINITY }.apply { this[0] = 0.0 }
        for (p in a) {
            val current = DoubleArray(b.size + 1) { Double.POSITIVE_INFINITY }
            for (j in b.indices) {
                val d = p.distance(b[j]).toDouble()
                current[j + 1] = d * d + minOf(previous[j], previous[j + 1], current[j])
            }
            previous = current
        }
        return previous.last() / a.size
    }

    private companion object { const val SAMPLES = 32 }
}
