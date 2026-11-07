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
    fun restoresMissingDiacriticsBeforeKeyboardTypos() {
        for ((keys, expected) in mapOf(
            "truoc" to "trước", "duoc" to "được", "nuoc" to "nước", "nguoi" to "người",
            "tieng" to "tiếng", "thuoc" to "thuốc", "viec" to "việc", "truocs" to "trước",
        )) {
            val suggestions = corrector.suggest(keys)
            assertEquals("$keys: $suggestions", expected, suggestions.first())
        }
    }

    @Test
    fun correctionsNeverOfferStopFinalsWithoutAcuteOrDotTone() {
        for (keys in listOf("truoc", "duoc", "nuoc", "cach", "tap", "viet")) {
            val suggestions = corrector.suggest(keys, max = 100)
            assertTrue("$keys: $suggestions", suggestions.none { it in listOf("trôc", "truơc", "trươc") })
            for (word in suggestions) {
                if (listOf("c", "ch", "p", "t").any { word.endsWith(it) }) {
                    assertTrue("$keys → $word", word.any {
                        ToneRules.toneOfChar(it) == Tone.ACUTE || ToneRules.toneOfChar(it) == Tone.DOT
                    })
                }
            }
        }
    }

    @Test
    fun restorationPreservesCapitalizationAndExplicitDiacritics() {
        assertEquals("Trước", corrector.suggest("truoc", listOf(true)).first())
        assertEquals("TRƯỚC", corrector.suggest("truoc", List(5) { true }).first())
        assertTrue("đã gõ dấu ngã", "trước" !in corrector.suggest("truocx"))
        assertTrue("đã gõ ô", "tờ" !in corrector.suggest("too"))
    }

    @Test
    fun restorationRespectsToneStyleAndCustomToneKeys() {
        val custom = Corrector(TelexConfig(toneKeys = mapOf('v' to Tone.ACUTE), newToneStyle = true), neighbors)
        assertEquals("trước", custom.suggest("truocv").first())
        assertEquals("khoẻ", custom.suggest("khoe").first())
        assertEquals("khỏe", corrector.suggest("khoe").first())
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
