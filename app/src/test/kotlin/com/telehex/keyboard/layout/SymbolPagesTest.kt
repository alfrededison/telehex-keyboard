package com.telehex.keyboard.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SymbolPagesTest {
    /** Bố cục mặc định đến 0.7.0: dữ liệu cũ (lớp đi theo chữ) được chuyển trên bố cục này. */
    private val legacy = LayoutModel(
        listOf(
            listOf("w", "r", "y", KeyCode.SETTINGS, "u", "o", KeyCode.NUMBERS),
            listOf("q", "e", "t", "f", "h", "i", "p"),
            listOf("a", "s", "d", "g", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m"),
            listOf(KeyCode.EMOJI, ",", null, null, null, ".", KeyCode.ENTER),
        )
    )

    private val legacyPages = SymbolPages.fromLetters(legacy).let { listOf(KeyLayers.pinDigits(it[0]), it[1]) }

    private fun values(pages: List<CellGrid>) = pages.flatMap { g -> g.rows.flatten() }.filter { it.isNotEmpty() }

    private fun row(s: String) = s.split(' ')

    @Test
    fun defaultLayoutIsValid() = assertNull(LayoutModel.DEFAULT.validate())

    @Test
    fun defaultPagesFollowGboard() {
        val p = KeyCode.PAGE
        val bottom = { comma: String, dot: String -> listOf(KeyCode.DIAL, comma, null, null, null, dot, KeyCode.ENTER) }
        assertEquals(
            listOf(
                row("1 3 5 7 9 0") + KeyCode.NUMBERS,
                listOf(p) + row("2 4 6 8 ( )"),
                row("@ # $ _ & - +"),
                row("* \" ' : / ! ?"),
                bottom(",", "."),
            ),
            SymbolPages.render(LayoutModel.DEFAULT, SymbolPages.DEFAULT[0]),
        )
        assertEquals(
            listOf(
                row("~ | √ ÷ § \\") + KeyCode.NUMBERS,
                listOf(p) + row("` • π × { }"),
                row("£ ₫ € ¥ ^ ° ="),
                row("% © ® ; ✓ [ ]"),
                bottom("<", ">"),
            ),
            SymbolPages.render(LayoutModel.DEFAULT, SymbolPages.DEFAULT[1]),
        )
    }

    @Test
    fun defaultPagesHoldEverySymbolOnce() {
        val noPage = values(SymbolPages.DEFAULT).filter { it != KeyCode.PAGE }
        assertEquals(noPage.size, noPage.toSet().size)
    }

    @Test
    fun defaultSwipeDigitsSitUnderQwertyLetters() {
        "qwertyuiop".forEachIndexed { i, ch ->
            val (r, c) = LayoutModel.DEFAULT.find("$ch")!!
            assertEquals("$ch", "${(i + 1) % 10}", KeyLayers.DEFAULT_SWIPE[r, c])
        }
    }

    @Test
    fun emptySettingsAndPunctuationCellsKeepTheLetterLayerKeys() {
        // Dữ liệu cũ không có giá trị ở ô ⚙ / , / . nên lớp 123 vẫn hiện các phím đó; phím 1/2 ở chỗ chữ Z
        for (page in legacyPages) {
            val rendered = SymbolPages.render(legacy, page)
            assertEquals(KeyCode.SETTINGS, rendered[0][3])
            assertEquals(",", rendered[4][1])
            assertEquals(".", rendered[4][5])
            assertEquals(KeyCode.PAGE, rendered[3][0])
        }
    }

    @Test
    fun swappingLettersLeavesOtherLayersInPlace() {
        val l = LayoutModel.DEFAULT.withSwapped(0 to 0, 2 to 0) // Q ↔ A
        assertEquals(SymbolPages.DEFAULT, KeyLayers.reconcileSymbols(l, SymbolPages.DEFAULT))
        assertEquals(KeyLayers.DEFAULT_SWIPE, KeyLayers.reconcile(l, listOf(KeyLayers.DEFAULT_SWIPE), KeyLayers::swipeUsable).single())
    }

    @Test
    fun valuesStayInTheSettingsCellWhenSettingsMoves() {
        // ⚙ ↔ Q: lớp 123 gắn theo ô nên vẫn y nguyên, ô ⚙ mới hiện số 1, ô Q mới hiện phím 1/2
        val l = LayoutModel.DEFAULT.withSwapped(1 to 0, 0 to 0)
        assertEquals(SymbolPages.DEFAULT, KeyLayers.reconcileSymbols(l, SymbolPages.DEFAULT))
        val rendered = SymbolPages.render(l, SymbolPages.DEFAULT[0])
        assertEquals("1", rendered[0][0])
        assertEquals(KeyCode.PAGE, rendered[1][0])
    }

    @Test
    fun hiddenValuesPreferLetterCellsOverAnEmptySettingsCell() {
        // 123 ↔ W trên bố cục cũ: số 2 bị che sang ô chữ vừa trống (0,6), không chiếm ô ⚙ trống (0,3) đứng trước
        val l = legacy.withSwapped(0 to 6, 0 to 0)
        val pages = KeyLayers.reconcileSymbols(l, legacyPages)
        assertEquals("2", pages[0][0, 6])
        assertEquals("", pages[0][0, 3])
        assertEquals(values(legacyPages).sorted(), values(pages).sorted())
    }

    @Test
    fun hiddenValuesUseAnEmptyPunctuationCellWhenNoLetterCellIsFree() {
        // 123 ↔ `,`: `<` của trang 2 bị che, mọi ô chữ đã đầy nên sang ô `,` mới (0,6) thay vì mất
        val l = LayoutModel.DEFAULT.withSwapped(0 to 6, 4 to 1)
        val pages = KeyLayers.reconcileSymbols(l, SymbolPages.DEFAULT)
        assertEquals("<", pages[1][0, 6])
        assertEquals(values(SymbolPages.DEFAULT).sorted(), values(pages).sorted())
    }

    @Test
    fun pinningNeverMovesThePageKey() {
        // Phím chuyển trang nằm ở ô cũ của số 1: số 1 bị bỏ qua, phím trang giữ nguyên
        // (chuyển dữ liệu cũ khi chữ Z đứng ở ô của Q)
        val page = legacyPages[0].with(1, 0, KeyCode.PAGE).with(3, 0, "1")
        val pinned = KeyLayers.pinDigits(page)
        assertEquals(KeyCode.PAGE, pinned[1, 0])
        assertEquals("1", pinned[3, 0])
    }

    @Test
    fun legacyDigitsMoveToTheirOldCells() {
        for ((digit, cell) in KeyLayers.DIGIT_CELLS) assertEquals(digit, legacyPages[0][cell.first, cell.second])
    }

    @Test
    fun pinningKeepsAValueTheUserPutInPlaceOfADigit() {
        // Dữ liệu cũ: vuốt xuống Q đổi từ "1" thành "x"; không được ghi đè "x"
        val swipe = CellGrid.fromCodes(legacy) { if (it == "q") "x" else KeyLayers.DEFAULT_SWIPE_BY_CODE[it] }
        val pinned = KeyLayers.pinDigits(swipe)
        assertEquals("x", pinned[1, 0])
        assertEquals("2", pinned[0, 0])
    }
}
