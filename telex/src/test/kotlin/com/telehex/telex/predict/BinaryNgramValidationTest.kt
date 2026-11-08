package com.telehex.telex.predict

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertThrows
import org.junit.Test

class BinaryNgramValidationTest {
    @Test fun rejectsTruncatedHeadersAndImpossibleCountsBeforeAllocatingVocabulary() {
        assertThrows(IllegalArgumentException::class.java) { BinaryNgramModel(ByteBuffer.allocate(4)) }
        for (count in listOf(-1, Int.MAX_VALUE)) {
            val data = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
            data.put("THNG".toByteArray()).put(1.toByte()).putInt(count)
            assertThrows(IllegalArgumentException::class.java) { BinaryNgramModel(data) }
        }
    }

    @Test fun rejectsTruncatedNgramTablesBeforeReadingColumns() {
        for ((bigram, trigram) in listOf(Int.MAX_VALUE to 0, 0 to Int.MAX_VALUE, -1 to 0, 0 to -1)) {
            val data = ByteBuffer.allocate(17).order(ByteOrder.LITTLE_ENDIAN)
            data.put("THNG".toByteArray()).put(1.toByte()).putInt(0).putInt(bigram).putInt(trigram)
            assertThrows(IllegalArgumentException::class.java) { BinaryNgramModel(data) }
        }
    }
}
