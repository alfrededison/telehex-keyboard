package com.telehex.telex.predict

import com.telehex.telex.SyllableValidator
import com.telehex.telex.ToneRules
import com.telehex.platform.Normalizer

/** Từ vựng/tần suất bổ sung; không chứa ngữ cảnh và không học dữ liệu cá nhân. Dựng trên luồng nền. */
class VietnameseVocabulary(source: List<String>) {
    val words: List<String> = source.asSequence()
        .map { Normalizer.normalize(it.lowercase(), Normalizer.Form.NFC) }
        .filter { word ->
            word.length in 1..8 && word.count { ToneRules.toneOfChar(it) != 0 } <= 1 &&
                SyllableValidator.isValid(stripTones(word), word.maxOf { ToneRules.toneOfChar(it) })
        }.distinct().toList()
    private val ranks = words.withIndex().associate { it.value to it.index }
    private val prefixes = words.map { it to stripTones(it) }

    fun rankOf(word: String): Int = ranks[word] ?: Int.MAX_VALUE

    fun complete(prefix: String, max: Int): List<String> {
        if (max <= 0) return emptyList()
        val key = stripTones(Normalizer.normalize(prefix.lowercase(), Normalizer.Form.NFC))
        return prefixes.asSequence().filter { it.second.startsWith(key) }.take(max).map { it.first }.toList()
    }

    /** Ưu tiên thấp để dữ liệu đã học và cụm từ theo ngữ cảnh thắng. */
    fun prior(word: String): Double = ranks[word]?.let { 0.001 / (it + 1) } ?: 0.0

    private fun stripTones(text: String): String = text.map { ToneRules.stripTone(it) }.joinToString("")
}
