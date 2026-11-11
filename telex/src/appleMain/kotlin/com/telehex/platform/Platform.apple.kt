@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.telehex.platform

import platform.Foundation.*

actual class TextLineReader internal constructor(private val lines: Iterator<String>) {
    actual fun readLine(): String? = if (lines.hasNext()) lines.next() else null
}
actual fun stringLineReader(text: String): TextLineReader = TextLineReader(text.lineSequence().iterator())
internal actual fun normalizeUnicode(text: String, decomposed: Boolean): String {
    val value = NSString.create(string = text)
    return if (decomposed) value.decomposedStringWithCanonicalMapping else value.precomposedStringWithCanonicalMapping
}
