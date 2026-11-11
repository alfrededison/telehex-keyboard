package com.telehex.telex.predict

import com.telehex.platform.Normalizer

/** Hai âm tiết cuối trước từ đang sửa; không đi qua ranh giới câu. */
object PredictionContext {
    private val words = Regex("\\p{L}+")

    fun before(text: String): List<String> {
        val sentence = text.substring(text.indexOfLast { it in ".!?\n" } + 1)
        val normalized = Normalizer.normalize(sentence, Normalizer.Form.NFC)
        return words.findAll(normalized).map { it.value }.toList().takeLast(2)
    }
}
