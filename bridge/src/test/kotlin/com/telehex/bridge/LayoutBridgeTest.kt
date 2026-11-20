package com.telehex.bridge

import com.telehex.layout.GridSpec
import com.telehex.layout.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutBridgeTest {
    private val presets = LayoutBridge.presets()

    @Test
    fun presetsKeepTheStoredIosIdsAndCodes() {
        assertEquals(
            listOf("builtin.default", "builtin.qwerty", "builtin.qwertyNum", "builtin.hexNum", "builtin.hex8", "builtin.hex8Num"),
            presets.map { it.id },
        )
        val hex = presets[0]
        assertEquals(listOf("q", "e", "t", "y", "u", "o", "#symbols", "#settings"), hex.letters.take(8))
        assertEquals(listOf("#emoji", ",", " ", "", "", ".", "\n"), hex.letters.takeLast(7))
        assertTrue(presets[1].letters.contains("#backspace"))
        assertEquals(35, hex.swipe.size)
        assertEquals(listOf(35, 35), hex.symbols.map { it.size })
    }

    @Test
    fun everyPresetIsValidAndReconcilesToItself() {
        for (p in presets) {
            assertTrue(p.name, LayoutBridge.validLetters(p.letters))
            val layers = LayoutBridge.reconcile(p.letters, p.swipe, p.symbols)!!
            assertEquals(p.swipe, layers.swipe)
            assertEquals(p.symbols, layers.symbols)
        }
    }

    @Test
    fun validationRejectsBrokenLayouts() {
        val hex = presets[0].letters
        assertFalse(LayoutBridge.validLetters(hex.dropLast(1)))
        // Phím cách bị tách đôi
        val split = hex.toMutableList().apply { val i = indexOf(","); this[i] = ""; this[indexOf(" ") + 1] = "," }
        assertFalse(LayoutBridge.validLetters(split))
        // Ô "" đứng trước ô phím cách: iOS sẽ vẽ phím cách lệch đè phím bên cạnh
        val space = hex.indexOf(" ")
        val shifted = hex.toMutableList().apply { this[space - 1] = ""; this[space] = " "; this[space + 1] = ","; this[space + 2] = "" }
        assertFalse(LayoutBridge.validLetters(hex.toMutableList().apply { this[space] = ""; this[space + 1] = " " }))
        assertFalse(LayoutBridge.validLetters(shifted))
        // Mã kiểu Android trong dữ liệu iOS: phím sẽ không làm gì
        assertFalse(LayoutBridge.validLetters(hex.map { if (it == "#symbols") "#123" else it }))
        // Thiếu ⌫ so với bộ phím của Truyền thống
        assertFalse(LayoutBridge.validLetters(presets[1].letters.map { if (it == "#backspace") "x" else it }))
    }

    @Test
    fun reconcileMovesSymbolsOffFunctionKeys() {
        val p = presets[0]
        val settings = p.letters.indexOf("#settings")
        val emoji = p.letters.indexOf("#emoji")
        val swapped = p.letters.toMutableList().apply { this[settings] = "#emoji"; this[emoji] = "#settings" }
        val layers = LayoutBridge.reconcile(swapped, p.swipe, p.symbols)!!
        // Phím 1/2 không còn ở ô ☺ vừa dời tới chỗ ⚙ cũ… mà theo ⚙ (ô dùng được)
        assertEquals("", layers.symbols[0][settings])
        assertTrue(layers.symbols[0].contains("#page"))
    }

    @Test
    fun neighboursFollowTheRealGrid() {
        // Truyền thống: Q kề W và A (không phải lưới 7 cột cắt sai)
        assertEquals(setOf('w', 'a'), LayoutBridge.neighbors(presets[1].letters).getValue("q").toSet())
        // Tổ ong: như Android
        val android = Profile.DEFAULT.letters.letterNeighbors()
        assertEquals(android.map { (k, v) -> "$k" to v.joinToString("") }.toMap(), LayoutBridge.neighbors(presets[0].letters))
    }

    @Test
    fun gridMetricsMatchTheGeometry() {
        val grids = LayoutBridge.grids().associateBy { it.name }
        val hex7 = grids.getValue("HEX7")
        assertEquals(8 / Math.sqrt(3.0), hex7.hexHeight, 1e-4)
        val expected = listOf(0.0, 0.75, 1.5, 2.25, 3.0).map { it * 2 / Math.sqrt(3.0) }
        expected.forEachIndexed { r, top -> assertEquals(top, hex7.hexRowTops[r], 1e-4) }
        assertEquals(GridSpec.entries.size, grids.size)
        assertTrue(grids.getValue("QWERTY").isHex.not())
        assertEquals(4, grids.getValue("QWERTY_NUM").spaceCells)
    }

    @Test
    fun codePredicatesUseIosCodes() {
        assertTrue(LayoutBridge.symbolUsable("#settings"))
        assertFalse(LayoutBridge.symbolUsable("#symbols"))
        assertFalse(LayoutBridge.swipeUsable(" "))
        assertTrue(LayoutBridge.swipeUsable("?"))
        assertTrue(LayoutBridge.isPunct("'"))
    }
}
