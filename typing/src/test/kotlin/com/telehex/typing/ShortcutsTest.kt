package com.telehex.typing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutsTest {
    private val snippets = mapOf("dc" to "được", "ko" to "không", "k2" to "không hai", "@@g" to "a@gmail.com", "dd" to "X", "đ" to "Y")

    private fun expand(before: String, typed: String, keys: String = typed) =
        Shortcuts.match(snippets, before, typed, keys)?.let { it.prefix to it.expansion }

    @Test
    fun matchesTheSyllableAndTheCommittedHead() {
        assertEquals("" to "được", expand("xin ", "dc"))
        // "k2": số chốt luôn chữ đang soạn, chạm cách lúc không soạn gì vẫn bung cả token đã chốt
        assertEquals("k2" to "không hai", expand("k2", ""))
        assertEquals("@@" to "a@gmail.com", expand("@@", "g"))
        // Ngay sau dấu mở chỉ thay âm tiết, giữ dấu mở
        assertEquals("" to "được", expand("(", "dc"))
    }

    @Test
    fun doesNotSplitInsideWordsOrLinks() {
        assertNull(expand("x.", "dc"))
        assertNull(expand("https://x.", "co"))
        assertNull(expand("", ""))
    }

    @Test
    fun displayTextWinsOverRawKeysAndCaseIsKept() {
        // "dd" đang hiện "đ": khớp "đ" trước
        assertEquals("" to "Y", expand("", "đ", "dd"))
        assertEquals("" to "Không", expand("", "Ko"))
        assertEquals("" to "được", expand("", "đc", "dc"))
    }
}
