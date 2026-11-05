package com.telehex.telex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectorTest {
    // Lân cận theo layout mặc định 7 cột (một phần, đủ cho test)
    private val neighbors = mapOf(
        'n' to listOf('b', 'm', '.', 'f', 'g', 'l'),
        'g' to listOf('f', 'j', 'u', 'i', 'n', 'm'),
        'h' to listOf('i', 'p', 'g', 'j', 'k'),
        'o' to listOf('u', 'i', 'p', 'h'),
        't' to listOf('e', 'y', 's', 'd', 'u'),
        'i' to listOf('u', 'h', 'o', 'g', 'j'),
    )
    private val corrector = Corrector(TelexConfig(), neighbors)

    @Test
    fun suggestsNeighborSubstitution() {
        // "nguoi" thiếu w → "người"? chỉ hợp lệ khi có w; "ngươi" không có dấu vẫn hợp lệ
        val s = corrector.suggest("nguoi")
        assertTrue(s.toString(), s.contains("ngươi") || s.contains("người"))
    }

    @Test
    fun suggestsForInvalidSyllable() {
        val s = corrector.suggest("tiengs") // quên e thứ hai
        assertEquals(s.toString(), "tiếng", s.first())
    }

    @Test
    fun decomposerRoundTrip() {
        for (w in listOf("người", "tiếng", "Việt", "đường", "thuở", "quốc", "giữa", "xoong")) {
            val (keys, caps) = TelexDecomposer.keysOf(w)!!
            val e = TelexEngine()
            for (i in keys.indices) e.press(keys[i], caps[i])
            assertEquals(w, e.text())
        }
    }

    @Test
    fun commonSyllablesRanked() {
        assertTrue(CommonSyllables.rankOf("và") < CommonSyllables.rankOf("xoong"))
    }
}
