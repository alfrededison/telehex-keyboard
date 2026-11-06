package com.telehex.keyboard.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SymbolPagesTest {
    private val digitHome = "qwryfhuopl".mapIndexed { i, ch -> ((i + 1) % 10).toString() to LayoutModel.DEFAULT.find(ch.toString())!! }

    private fun values(pages: List<CellGrid>) = pages.flatMap { g -> g.rows.flatten() }.filter { it.isNotEmpty() }

    @Test
    fun defaultLayoutIsValid() = assertNull(LayoutModel.DEFAULT.validate())

    @Test
    fun defaultDigitsSitOnQwryfhuopl() {
        for ((digit, cell) in digitHome) {
            assertEquals("123: $digit", digit, SymbolPages.DEFAULT[0][cell.first, cell.second])
            assertEquals("vuốt xuống: $digit", digit, KeyLayers.DEFAULT_SWIPE[cell.first, cell.second])
        }
    }

    @Test
    fun defaultPagesHoldEverySymbolOnce() {
        val old = values(SymbolPages.fromLetters(LayoutModel.DEFAULT))
        val now = values(SymbolPages.DEFAULT)
        assertEquals(old.sorted(), now.sorted())
        val noPage = now.filter { it != KeyCode.PAGE }
        assertEquals(noPage.size, noPage.toSet().size)
    }

    @Test
    fun pageKeySitsOnTheLeftEdgeOfBothPages() {
        for (p in SymbolPages.DEFAULT) assertEquals(KeyCode.PAGE, SymbolPages.render(LayoutModel.DEFAULT, p)[3][0])
    }

    @Test
    fun functionKeysComeFromTheLetterLayer() {
        val abc = LayoutModel.BALANCED.rows
        for (p in SymbolPages.DEFAULT) {
            val page = SymbolPages.render(LayoutModel.BALANCED, p)
            for (r in abc.indices) for (c in abc[r].indices) {
                val code = abc[r][c]
                val expected = if (code == KeyCode.EMOJI) KeyCode.DIAL else code
                if (code == null || KeyCode.isFunction(code) || code == "," || code == ".") assertEquals(expected, page[r][c])
            }
        }
    }

    @Test
    fun swappingLettersLeavesOtherLayersInPlace() {
        val l = LayoutModel.DEFAULT.withSwapped(0 to 0, 2 to 0) // W ↔ A
        assertEquals(SymbolPages.DEFAULT, KeyLayers.reconcile(l, SymbolPages.DEFAULT, KeyLayers::symbolUsable))
        assertEquals(KeyLayers.DEFAULT_SWIPE, KeyLayers.reconcile(l, listOf(KeyLayers.DEFAULT_SWIPE), KeyLayers::swipeUsable).single())
    }

    @Test
    fun valuesHiddenUnderAMovedFunctionKeyMoveToTheFreedCell() {
        // ⚙ (0,3) ↔ Q (1,0): ô (1,0) thành phím chức năng, ô (0,3) thành ô chữ còn trống
        val l = LayoutModel.DEFAULT.withSwapped(0 to 3, 1 to 0)
        val pages = KeyLayers.reconcile(l, SymbolPages.DEFAULT, KeyLayers::symbolUsable)
        assertEquals("1", pages[0][0, 3])
        assertEquals("", pages[0][1, 0])
        assertEquals(values(SymbolPages.DEFAULT).sorted(), values(pages).sorted())
    }

    @Test
    fun pinningNeverMovesThePageKey() {
        // Phím chuyển trang nằm ở ô mặc định của số 1: số 1 bị bỏ qua, phím trang giữ nguyên
        // (chuyển dữ liệu cũ khi chữ Z đứng ở ô của Q)
        val page = SymbolPages.DEFAULT[0].with(1, 0, KeyCode.PAGE).with(3, 0, "1")
        val pinned = KeyLayers.pinDigits(page)
        assertEquals(KeyCode.PAGE, pinned[1, 0])
        assertEquals("1", pinned[3, 0])
    }

    @Test
    fun legacyDataOnTheDefaultLayoutMigratesToTheDefaults() {
        assertEquals(SymbolPages.DEFAULT[0], KeyLayers.pinDigits(SymbolPages.fromLetters(LayoutModel.DEFAULT)[0]))
        val swipe = CellGrid.fromCodes(LayoutModel.DEFAULT) { KeyLayers.DEFAULT_SWIPE_BY_CODE[it] }
        assertEquals(KeyLayers.DEFAULT_SWIPE, KeyLayers.pinDigits(swipe))
    }

    @Test
    fun pinningKeepsAValueTheUserPutInPlaceOfADigit() {
        // Dữ liệu cũ: vuốt xuống Q đổi từ "1" thành "x"; không được ghi đè "x"
        val swipe = CellGrid.fromCodes(LayoutModel.DEFAULT) { if (it == "q") "x" else KeyLayers.DEFAULT_SWIPE_BY_CODE[it] }
        val pinned = KeyLayers.pinDigits(swipe)
        assertEquals("x", pinned[1, 0])
        assertEquals("2", pinned[0, 0])
    }
}
