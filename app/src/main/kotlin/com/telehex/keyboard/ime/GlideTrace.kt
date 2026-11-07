package com.telehex.keyboard.ime

import com.telehex.telex.predict.GlideDecoder.Point

/** Theo dõi một ngón; chỉ lướt qua ít nhất 3 phím chữ khác nhau mới là glide, không phân biệt hướng. */
class GlideTrace(start: Point, firstLetter: Char) {
    private val samples = arrayListOf(start)
    private val letters = hashSetOf(firstLetter)
    private var length = 0f
    val points: List<Point> get() = samples
    var active = false
        private set

    fun add(point: Point, letter: Char?) {
        val distance = samples.last().distance(point)
        if (distance < 0.025f) return
        length += distance
        samples.add(point)
        // Giới hạn bộ nhớ với những đường vuốt rất dài; vẫn giữ đầu/cuối và hình dạng.
        if (samples.size > 256) {
            val reduced = samples.filterIndexed { i, _ -> i % 2 == 0 || i == samples.lastIndex }
            samples.clear(); samples.addAll(reduced)
        }
        if (letter != null) letters.add(letter)
        if (letters.size >= 3 && length >= 1.5f) active = true
    }
}
