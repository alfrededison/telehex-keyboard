package com.telehex.platform

actual typealias TextLineReader = java.io.BufferedReader
actual fun stringLineReader(text: String): TextLineReader = text.reader().buffered()
internal actual fun normalizeUnicode(text: String, decomposed: Boolean): String =
    java.text.Normalizer.normalize(text, if (decomposed) java.text.Normalizer.Form.NFD else java.text.Normalizer.Form.NFC)
