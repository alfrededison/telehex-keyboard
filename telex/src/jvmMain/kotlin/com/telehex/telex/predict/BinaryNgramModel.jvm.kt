package com.telehex.telex.predict

import com.telehex.platform.ModelBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Preserve the Android/JVM constructor call and zero-copy mmap behavior. */
fun BinaryNgramModel(buffer: ByteBuffer): BinaryNgramModel {
    val bytes = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    return BinaryNgramModel(object : ModelBuffer {
        override fun get(index: Int): Byte = bytes.get(index)
        override fun getInt(index: Int): Int = bytes.getInt(index)
        override fun limit(): Int = bytes.limit()
    })
}
