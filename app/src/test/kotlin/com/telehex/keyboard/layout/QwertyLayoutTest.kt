package com.telehex.keyboard.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyLayoutTest {
    private val q = LayoutModel.QWERTY

    private fun row(s: String) = s.split(' ')

    @Test
    fun readsLikeGboard() {
        assertEquals(GridSpec.QWERTY, q.spec)
        assertEquals("qwertyuiop".map { "$it" }, q.rows[0])
        assertEquals("asdfghjkl".map { "$it" }, q.rows[1])
        assertEquals(listOf(KeyCode.SHIFT) + "zxcvbnm".map { "$it" } + KeyCode.BACKSPACE, q.rows[2])
        assertEquals(listOf(KeyCode.NUMBERS, KeyCode.EMOJI, KeyCode.SETTINGS, ",", null, null, null, null, ".", KeyCode.ENTER), q.rows[3])
        assertNull(q.validate())
    }

    @Test
    fun specIsRecognisedFromRowSizes() {
        assertEquals(GridSpec.HEX7, LayoutModel.DEFAULT.spec)
        assertEquals(GridSpec.QWERTY, LayoutModel(q.rows.map { it.toList() }).spec)
        assertEquals(GridSpec.QWERTY, CellGrid(KeyLayers.QWERTY_SWIPE.rows).spec)
        assertNull(GridSpec.of(listOf(10, 9, 10, 9)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownShapeIsRejected() {
        LayoutModel(listOf(listOf("a")))
    }

    @Test
    fun spaceMustCoverTheFourMiddleCells() {
        val moved = q.withSwapped(3 to 4, 3 to 8) // phím cách lệch sang phải một ô
        assertEquals("Phím cách phải chiếm 4 ô giữa hàng cuối", moved.validate())
    }

    @Test
    fun zRowIsCentredWithGapsBesideShiftAndBackspace() {
        val spec = GridSpec.QWERTY
        // Z ngay dưới S, M ngay dưới K; ⇧ và ⌫ sát mép, cách chữ ½ ô
        assertEquals(spec.centerX(1, 1), spec.centerX(2, 1))
        assertEquals(spec.centerX(1, 7), spec.centerX(2, 7))
        assertEquals(0f, spec.left(2, 0))
        assertEquals(0.5f, spec.left(2, 1) - (spec.left(2, 0) + 1))
        assertEquals(0.5f, spec.left(2, 8) - (spec.left(2, 7) + 1))
        assertEquals(spec.span, spec.left(2, 8) + 1)
    }

    @Test
    fun neighboursFollowKeyPositions() {
        val n = q.letterNeighbors()
        assertEquals(setOf('q', 'w', 's', 'z'), n['a']!!.toSet())
        // Hàng Z thẳng cột với hàng A…L: G kề C, V, B
        assertEquals(setOf('t', 'y', 'f', 'h', 'c', 'v', 'b'), n['g']!!.toSet())
        assertEquals(setOf('o', 'p', 'k', 'm'), n['l']!!.toSet())
        assertEquals(setOf('a', 's', 'd', 'x'), n['z']!!.toSet())
        assertEquals(setOf('w', 'a'), n['q']!!.toSet())
    }

    @Test
    fun hexNeighboursUnchanged() {
        // Lưới 7 cột giữ đúng quan hệ kề cũ: hàng lệch ½ sang phải kề ô cùng cột và cột kế tiếp ở hàng trên/dưới
        val l = LayoutModel.DEFAULT
        assertEquals(listOf(2 to 2, 2 to 4, 1 to 3, 1 to 4, 3 to 3, 3 to 4), l.neighborCells(2, 3))
        assertEquals(listOf(1 to 2, 1 to 4, 0 to 2, 0 to 3, 2 to 2, 2 to 3), l.neighborCells(1, 3))
    }

    @Test
    fun swipeDigitsSitUnderTheTopRow() {
        "qwertyuiop".forEachIndexed { i, ch -> assertEquals("${(i + 1) % 10}", KeyLayers.QWERTY_SWIPE[0, i]) }
        assertEquals("?", KeyLayers.QWERTY_SWIPE[3, 3])
        assertEquals("!", KeyLayers.QWERTY_SWIPE[3, 8])
    }

    @Test
    fun symbolPagesPutPageOnShiftAndDialpadOnEmoji() {
        val p = KeyCode.PAGE
        val bottom = { set: String, comma: String, dot: String ->
            listOf(KeyCode.NUMBERS, KeyCode.DIAL, set, comma, null, null, null, null, dot, KeyCode.ENTER)
        }
        assertEquals(
            listOf(
                row("1 2 3 4 5 6 7 8 9 0"),
                row("@ # $ _ & - + ( )"),
                listOf(p) + row("* \" ' : ; ! ?") + KeyCode.BACKSPACE,
                bottom("/", ",", "."),
            ),
            SymbolPages.render(q, SymbolPages.QWERTY[0]),
        )
        assertEquals(
            listOf(
                row("~ ` | • √ π ÷ × § ∆"),
                row("£ ₫ € ¥ ^ ° = { }"),
                listOf(p) + row("% © ® ™ ✓ [ ]") + KeyCode.BACKSPACE,
                bottom("\\", "<", ">"),
            ),
            SymbolPages.render(q, SymbolPages.QWERTY[1]),
        )
        val values = SymbolPages.QWERTY.flatMap { g -> g.rows.flatten() }.filter { it.isNotEmpty() && it != p }
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun builtInProfileIsConsistent() {
        val p = Profile.builtInFor(GridSpec.QWERTY)
        assertEquals(Profile.QWERTY, p)
        assertTrue(p.sameLayers(p.reconciled()))
        assertEquals(Profile.DEFAULT, Profile.builtInFor(GridSpec.HEX7))
    }

    @Test(expected = IllegalArgumentException::class)
    fun layersMustShareTheGrid() {
        Profile("x", LayoutModel.QWERTY, KeyLayers.DEFAULT_SWIPE, SymbolPages.QWERTY)
    }
}
