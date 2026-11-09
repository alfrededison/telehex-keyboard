package com.telehex.keyboard.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutModelTest {
    @Test
    fun builtInProfilesAreValid() {
        for (p in Profile.BUILT_IN) {
            assertNull(p.name, p.letters.validate())
            // Lớp vuốt xuống / 123 không có giá trị nằm dưới phím chức năng
            assertTrue(p.name, p.sameLayers(p.reconciled()))
        }
    }

    @Test
    fun defaultReadsLikeQwerty() {
        val l = LayoutModel.DEFAULT
        // Hàng 1 sát mép trái, hàng 0 lệch ½ ô: đọc từ trái sang là zigzag giữa hai hàng
        val zigzag = (0 until LayoutModel.COLS).flatMap { c -> listOf(l[1, c], l[0, c]) }
        assertEquals(listOf(KeyCode.SETTINGS) + "qwertgyhuiop".map { "$it" } + KeyCode.NUMBERS, zigzag)
        assertEquals("asdfjkl".map { "$it" }, l.rows[2])
        assertEquals("zxcvbnm".map { "$it" }, l.rows[3])
        assertEquals(listOf(KeyCode.EMOJI, ",", null, null, null, ".", KeyCode.ENTER), l.rows[4])
    }
}
