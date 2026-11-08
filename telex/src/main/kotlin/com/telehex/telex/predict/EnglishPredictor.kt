package com.telehex.telex.predict

import java.util.Locale
import java.util.PriorityQueue
import java.text.Normalizer

/** Từ điển EN theo độ phổ biến + n-gram riêng; không tự sửa hay học dữ liệu người dùng. */
class EnglishPredictor(words: List<String>, private val model: NgramModel? = null) {
    // Sắp theo chữ để tìm tiền tố bằng binary search; index gốc giữ thứ tự phổ biến.
    private val dictionary = (words + (model as? BinaryNgramModel)?.vocabulary.orEmpty())
        .map { it.lowercase(Locale.ROOT).replace('’', '\'') }.filter { validWord(it) }.distinct().withIndex().sortedBy { it.value }
    private val unigram = model?.scorer(emptyList())
    private data class Candidate(val word: String, val rank: Int, val score: Double)

    fun complete(prefix: String, max: Int = 24, capsLock: Boolean = false, context: List<String> = emptyList()): List<String> {
        if (max <= 0 || prefix.length > 32 || !prefixPattern.matches(prefix)) return emptyList()
        val key = prefix.lowercase(Locale.ROOT).replace('’', '\'')
        val upper = capsLock || (prefix.length > 1 && prefix.filter { it.isAsciiLetter() }.all { it.isUpperCase() })
        val scorer = if (context.isEmpty()) null else model?.scorer(context.takeLast(2).map { it.lowercase(Locale.ROOT).replace('’', '\'') })
        var lo = 0
        var hi = dictionary.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (dictionary[mid].value < key) lo = mid + 1 else hi = mid
        }
        // Chỉ giữ max từ phổ biến nhất trong khoảng tiền tố, tránh sắp xếp hàng nghìn từ mỗi phím.
        val order = compareBy<Candidate> { it.score }.thenByDescending { it.rank }
        val best = PriorityQueue(order)
        for (i in lo until dictionary.size) {
            val entry = dictionary[i]
            if (!entry.value.startsWith(key)) break
            if (entry.value.length == key.length) continue
            // Chỉ thưởng phần xác suất tăng nhờ ngữ cảnh. Ngữ cảnh lạ giữ xếp hạng từ điển.
            val lift = if (scorer == null) 0.0 else
                (scorer.probability(entry.value) - (unigram?.probability(entry.value) ?: 0.0)).coerceAtLeast(0.0)
            val candidate = Candidate(entry.value, entry.index, 0.1 / (entry.index + 10) + lift)
            if (best.size < max) best.add(candidate)
            else if (order.compare(candidate, best.peek()) > 0) { best.poll(); best.add(candidate) }
        }
        return best.sortedWith(order.reversed()).map {
            if (upper) it.word.uppercase(Locale.ROOT) else prefix + it.word.substring(key.length)
        }
    }

    fun next(context: List<String>, max: Int = 24, capsLock: Boolean = false, capitalize: Boolean = false): List<String> {
        if (max <= 0) return emptyList()
        return model?.predict(context.takeLast(2).map { it.lowercase(Locale.ROOT).replace('’', '\'') }, max)
            .orEmpty().map { it.text }.filter { validWord(it) }.map {
                when {
                    capsLock -> it.uppercase(Locale.ROOT)
                    capitalize || it == "i" || it.startsWith("i'") -> it.replaceFirstChar { c -> c.uppercaseChar() }
                    else -> it
                }
            }
    }

    data class Token(val head: String, val tail: String)
    data class Query(val token: Token, val context: List<String>)

    companion object {
        private val wordPattern = Regex("[a-zA-Z]+(?:['’][a-zA-Z]+)*")
        private val prefixPattern = Regex("[a-zA-Z]+(?:['’][a-zA-Z]+)*['’]?")
        private val contextPattern = Regex("[\\p{L}\\p{N}_]+(?:['’][\\p{L}\\p{N}_]+)*")
        private fun validWord(word: String): Boolean = word.length <= 32 && wordPattern.matches(word)

        /** Hai từ trước con trỏ, không đi qua câu mới, số hay token ngoài bảng chữ EN. */
        fun contextBefore(text: String): List<String> {
            val sentence = Normalizer.normalize(text.substring(text.indexOfLast { it in ".!?\n" } + 1), Normalizer.Form.NFC)
            val context = ArrayDeque<String>()
            for (match in contextPattern.findAll(sentence)) {
                val word = match.value.lowercase(Locale.ROOT).replace('’', '\'')
                if (!validWord(word)) context.clear()
                else { context.addLast(word); if (context.size > 2) context.removeFirst() }
            }
            return context.toList()
        }

        /** Hoàn thành token đang gõ; chỉ dự đoán từ mới ở đầu ô hoặc sau khoảng trắng. */
        fun queryAtCursor(before: String, after: String): Query? {
            val token = tokenAtCursor(before, after)
            if (token != null) return Query(token, contextBefore(before.dropLast(token.head.length)))
            if (before.isNotEmpty() && !before.last().isWhitespace()) return null
            if (after.isNotEmpty() && !after.first().isWhitespace() && after.first() !in ".!?,;:)]}") return null
            return Query(Token("", ""), contextBefore(before))
        }

        /** Bỏ qua email, URL, identifier và phần từ chứa chữ ngoài ASCII. */
        fun tokenAtCursor(before: String, after: String): Token? {
            val head = before.takeLastWhile { it.isAsciiLetter() || it in "'’" }
            val tail = after.takeWhile { it.isAsciiLetter() || it in "'’" }
            if (head.isEmpty() || head.length + tail.length > 32) return null
            val left = before.getOrNull(before.length - head.length - 1)
            val right = after.getOrNull(tail.length)
            if (left != null && (left.isLetterOrDigit() || left in "_@./\\:$-")) return null
            if (right != null && (right.isLetterOrDigit() || right in "_@/\\:$-")) return null
            if (right == '.' && after.getOrNull(tail.length + 1)?.isLetterOrDigit() == true) return null
            return Token(head, tail)
        }

        private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
    }
}
