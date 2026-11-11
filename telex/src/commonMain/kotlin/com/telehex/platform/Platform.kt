package com.telehex.platform

object Normalizer {
    enum class Form { NFC, NFD }
    fun normalize(text: String, form: Form): String = normalizeUnicode(text, form == Form.NFD)
}

internal expect fun normalizeUnicode(text: String, decomposed: Boolean): String
expect class TextLineReader { fun readLine(): String? }
expect fun stringLineReader(text: String): TextLineReader

/** Android wraps its mmap ByteBuffer; Apple wraps memory-mapped NSData (`NSDataModelBuffer`). */
interface ModelBuffer {
    operator fun get(index: Int): Byte
    fun getInt(index: Int): Int
    fun limit(): Int
}

class ByteArrayModelBuffer(bytes: ByteArray) : ModelBuffer {
    private val data = bytes.copyOf()
    override fun get(index: Int): Byte = data[index]
    override fun limit(): Int = data.size
    override fun getInt(index: Int): Int =
        (data[index].toInt() and 255) or ((data[index + 1].toInt() and 255) shl 8) or
            ((data[index + 2].toInt() and 255) shl 16) or (data[index + 3].toInt() shl 24)
}
