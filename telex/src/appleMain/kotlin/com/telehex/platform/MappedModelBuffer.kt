@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.telehex.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData

/**
 * Reads a pack straight from [data] without copying. Pass memory-mapped data so a keyboard extension pays only for the
 * clean file pages it touches instead of holding the whole pack in its small memory budget. Out-of-range reads throw,
 * so a malformed pack fails validation like [ByteArrayModelBuffer] instead of reading past the mapping.
 */
class NSDataModelBuffer(private val data: NSData) : ModelBuffer {
    private val size = data.length.toInt()
    private val bytes = data.bytes?.reinterpret<ByteVar>()
    private fun byte(index: Int): Int {
        if (index !in 0 until size) throw IndexOutOfBoundsException("Index $index out of bounds for length $size")
        return bytes!![index].toInt() and 255
    }
    override fun get(index: Int): Byte = byte(index).toByte()
    override fun limit(): Int = size
    override fun getInt(index: Int): Int = byte(index) or (byte(index + 1) shl 8) or (byte(index + 2) shl 16) or (byte(index + 3) shl 24)
}
