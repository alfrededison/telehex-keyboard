package com.telehex.typing

import com.telehex.typing.AutoCap.Mode
import com.telehex.typing.CapsState.Override
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapsTest {
    @Test
    fun sentencesNeedASpaceAfterTheFullStop() {
        assertTrue(AutoCap.capitalizes("", Mode.SENTENCES))
        assertTrue(AutoCap.capitalizes("Xin chào. ", Mode.SENTENCES))
        assertTrue(AutoCap.capitalizes("Thật sao? (", Mode.SENTENCES))
        assertTrue(AutoCap.capitalizes("dòng một\n", Mode.SENTENCES))
        assertTrue(AutoCap.capitalizes("Hay quá!) ", Mode.SENTENCES))
        assertFalse(AutoCap.capitalizes("google.", Mode.SENTENCES))
        assertFalse(AutoCap.capitalizes("xin ", Mode.SENTENCES))
        assertFalse(AutoCap.capitalizes("U.S. ", Mode.SENTENCES))
    }

    @Test
    fun wordsAndCharacters() {
        assertTrue(AutoCap.capitalizes("Nguyễn ", Mode.WORDS))
        assertFalse(AutoCap.capitalizes("Nguyễn", Mode.WORDS))
        assertTrue(AutoCap.capitalizes("abc", Mode.CHARACTERS))
        assertFalse(AutoCap.capitalizes("", Mode.NONE))
    }

    @Test
    fun shiftTogglesTheNextLetterAndDoubleTapLocks() {
        val caps = CapsState()
        caps.pressShift(1000, 300, auto = false)
        assertEquals(Override.UPPER, caps.override)
        assertTrue(caps.upperNext(auto = false))
        caps.pressShift(1200, 300, auto = false)
        assertTrue(caps.capsLock)
        caps.pressShift(1300, 300, auto = true)
        assertEquals(Override.LOWER, caps.override) // tắt khoá ở đầu câu: chữ kế tiếp thường
        caps.reset()
        caps.pressShift(5000, 300, auto = true)
        assertEquals(Override.LOWER, caps.override) // đầu câu: ⇧ cho chữ thường
        caps.otherKey()
        caps.pressShift(5100, 300, auto = true)
        assertEquals(Override.UPPER, caps.override) // không còn là chạm đúp: chữ kế tiếp đang thường nên ⇧ cho chữ hoa
    }

    @Test
    fun spaceUpLocksAndUnlocksAndOneShotClears() {
        val caps = CapsState()
        assertEquals(null, caps.toggleLock(auto = true))
        assertFalse(caps.upperNext(auto = true))
        caps.clearOneShot()
        assertNull(caps.override)
        assertEquals(true, caps.toggleLock(auto = false))
        caps.clearOneShot()
        assertTrue(caps.capsLock) // khoá hoa không bị bỏ
        assertEquals(false, caps.toggleLock(auto = true))
        assertTrue(caps.upperNext(auto = true)) // tắt khoá: tự viết hoa chạy lại
    }
}
