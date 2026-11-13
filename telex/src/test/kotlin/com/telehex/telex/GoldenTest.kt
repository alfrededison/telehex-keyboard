package com.telehex.telex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bộ test vàng: đọc `golden.tsv` (input<TAB>expected[<TAB>options]). */
class GoldenTest {
    private data class Case(val line: Int, val input: String, val expected: String, val options: Set<String>)

    private fun cases(): List<Case> {
        val text = javaClass.getResourceAsStream("/golden.tsv")!!.bufferedReader().readText()
        return text.lines().mapIndexedNotNull { i, raw ->
            val line = raw.trimEnd()
            if (line.isBlank() || line.startsWith("#")) return@mapIndexedNotNull null
            val parts = line.split('\t')
            require(parts.size >= 2) { "Dòng ${i + 1} thiếu tab: $line" }
            val opts = parts.getOrNull(2)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
            Case(i + 1, parts[0], parts[1], opts)
        }
    }

    @Test
    fun golden() {
        val all = cases()
        assertTrue("Cần đủ số ca test, hiện có ${all.size}", all.size >= 200)
        val failures = ArrayList<String>()
        for (c in all) {
            val cfg = TelexConfig(newToneStyle = "new" in c.options, lAsGrave = "l" in c.options)
            val got = type(c.input, cfg)
            if (got != c.expected) failures.add("dòng ${c.line}: '${c.input}' → '$got' (mong '${c.expected}')")
        }
        assertTrue("Sai ${failures.size}/${all.size} ca:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun candidatesShowSixToneVariants() {
        val e = TelexEngine()
        for (k in "moi") e.press(k)
        val texts = e.candidates().map { it.text }
        assertEquals(listOf("mói", "mỏi", "mòi", "mõi", "mọi", "moi"), texts)
        e.press('w'); e.press('s')
        assertEquals(listOf("mới", "mởi", "mời", "mỡi", "mợi", "mơi"), e.candidates().map { it.text })
        assertEquals(Tone.ACUTE, e.candidates().first { it.selected }.tone)
        e.setTone(Tone.GRAVE)
        assertEquals("mời", e.text())
    }

    @Test
    fun candidatesSkipTonesAStopFinalCannotTake() {
        val e = TelexEngine()
        for (k in "cach") e.press(k)
        assertEquals(listOf("cách", "cạch", "cach"), e.candidates().map { it.text })
        e.reset()
        for (k in "tap") e.press(k)
        assertEquals(listOf("táp", "tạp", "tap"), e.candidates().map { it.text })
        e.reset()
        for (k in "ca") e.press(k) // còn gõ tiếp được (cam, can…) nên giữ đủ 6 dấu
        assertEquals(6, e.candidates().size)
    }

    @Test
    fun resumeReloadsACommittedWord() {
        fun resumed(word: String, keys: String, config: TelexConfig = TelexConfig()): String? {
            val e = TelexEngine(config)
            if (!e.resume(word)) return null
            for (k in keys) e.press(k)
            return e.text()
        }
        assertEquals("hiếu", resumed("hiêu", "s"))
        assertEquals("bán", resumed("ban", "s"))
        assertEquals("chiều", resumed("chiêu", "f"))
        assertEquals("Người", resumed("Ngươi", "f"))
        assertEquals("Đó", resumed("Đo", "s"))
        assertEquals("tiêng", resumed("tiếng", "z"))
        assertEquals("xoong", resumed("xoong", ""))
        assertEquals("developers", resumed("developer", "s"))
        // NFD từ ô nhập vẫn nạp được
        assertEquals("hiếu", resumed("hiêu", "s"))
        // Không gõ lại ra đúng từ: không nạp (kiểu đặt dấu khác, từ có ký tự lạ)
        assertEquals(null, resumed("hòa", "", TelexConfig(newToneStyle = true)))
        assertEquals(null, resumed("as", ""))
        assertEquals("wifi", resumed("wifi", ""))
        assertEquals(null, resumed("a1", ""))
    }

    @Test
    fun backspaceKeepsToneWhileVowelRemains() {
        val e = TelexEngine()
        for (k in "tieengs") e.press(k)
        assertEquals("tiếng", e.text())
        assertTrue(e.backspace()); assertEquals("tiến", e.text())
        assertTrue(e.backspace()); assertEquals("tiế", e.text())
        assertTrue(e.backspace()); assertEquals("tí", e.text())
        assertTrue(e.backspace()); assertEquals("t", e.text())
        assertTrue(e.backspace()); assertEquals("", e.text())
        assertTrue(e.isEmpty)
        assertTrue(!e.backspace())
    }

    @Test
    fun backspaceInFallbackReplaysRawKeys() {
        val e = TelexEngine()
        for (k in "windows") e.press(k)
        assertEquals("windows", e.text())
        e.backspace(); assertEquals("window", e.text())
        e.backspace(); e.backspace(); e.backspace(); e.backspace()
        assertEquals("ưi", e.text()) // "wi" lại là âm tiết hợp lệ nên hiện dạng Telex
        e.backspace(); assertEquals("ư", e.text())
        e.backspace(); assertTrue(e.isEmpty)
    }

    @Test
    fun upperCaseSurvivesTransform() {
        val e = TelexEngine()
        e.press('d', upper = true); e.press('d')
        assertEquals("Đ", e.text())
        e.press('a', upper = true); e.press('a')
        assertEquals("ĐÂ", e.text())
    }

    @Test
    fun validator() {
        assertTrue(SyllableValidator.isValid("tiêng", Tone.ACUTE))
        assertTrue(SyllableValidator.isValid("nguyên", Tone.NONE))
        assertTrue(SyllableValidator.isValid("quôc", Tone.ACUTE))
        assertTrue(SyllableValidator.isValid("giưa", Tone.TILDE))
        assertTrue(!SyllableValidator.isValid("tiêc", Tone.GRAVE)) // c chỉ đi với sắc/nặng
        assertTrue(!SyllableValidator.isValid("ce", Tone.NONE))
        assertTrue(!SyllableValidator.isValid("ghа", Tone.NONE))
        assertTrue(SyllableValidator.isValidPrefix("ngh"))
        assertTrue(SyllableValidator.couldBecomeValid("tie"))
        assertTrue(!SyllableValidator.couldBecomeValid("win"))
    }

    private fun type(input: String, cfg: TelexConfig): String {
        val e = TelexEngine(cfg)
        for (ch in input) e.press(ch.lowercaseChar(), upper = ch.isUpperCase())
        return e.text()
    }
}
