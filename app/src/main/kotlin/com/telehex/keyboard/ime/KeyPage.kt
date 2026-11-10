package com.telehex.keyboard.ime

import com.telehex.keyboard.layout.CellGrid
import com.telehex.keyboard.layout.KeyCode
import com.telehex.keyboard.layout.LayoutModel
import com.telehex.keyboard.layout.SymbolPages
import com.telehex.telex.TelexConfig

/** Một phím đã sẵn sàng để vẽ: mã, các ô nó chiếm, nhãn chính, nhãn phụ, ký tự vuốt xuống, loại. */
data class Key(
    val code: String,
    val cells: List<Pair<Int, Int>>,
    val label: String,
    val sub: String = "",
    val hint: String = "",
    val kind: KeyKind,
) {
    val isSpace: Boolean get() = code == KeyCode.SPACE
    val isLetter: Boolean get() = KeyCode.isLetter(code)
}

enum class Mode { ABC, SYMBOLS, EMOJI, DIAL, EDIT }

/** Dựng danh sách phím cho một trang từ lớp chữ, lớp vuốt xuống, lớp 123 (đã khớp với lớp chữ) và chế độ. */
object KeyPage {
    fun build(
        layout: LayoutModel,
        mode: Mode,
        symbolPage: Int,
        telex: TelexConfig,
        swipe: CellGrid,
        symbols: List<CellGrid>,
        enterLabel: String,
    ): List<Key> {
        val rows: List<List<String?>> = when (mode) {
            Mode.SYMBOLS -> SymbolPages.render(layout, symbols[if (symbolPage == 2) 1 else 0])
            else -> layout.rows
        }
        val keys = ArrayList<Key>(32)
        val spaceCells = ArrayList<Pair<Int, Int>>(4)
        for (r in rows.indices) for (c in rows[r].indices) {
            val code = rows[r][c]
            if (code == null) { spaceCells.add(r to c); continue }
            val cell = listOf(r to c)
            keys += when {
                code == KeyCode.EMOJI -> Key(code, cell, "☺", kind = KeyKind.FUNCTION)
                code == KeyCode.NUMBERS -> Key(code, cell, if (mode == Mode.SYMBOLS) "ABC" else "123", kind = KeyKind.FUNCTION)
                code == KeyCode.SETTINGS -> Key(code, cell, "⚙", hint = "📋", kind = KeyKind.FUNCTION)
                code == KeyCode.PAGE -> Key(code, cell, "$symbolPage/2", kind = KeyKind.FUNCTION)
                code == KeyCode.DIAL -> Key(code, cell, "1234", kind = KeyKind.FUNCTION)
                code == KeyCode.ENTER -> Key(code, cell, enterLabel, kind = KeyKind.ENTER)
                code == KeyCode.ABC -> Key(code, cell, "ABC", kind = KeyKind.FUNCTION)
                code == KeyCode.BACKSPACE -> Key(code, cell, "⌫", kind = KeyKind.FUNCTION)
                code == KeyCode.SHIFT -> Key(code, cell, "⇧", kind = KeyKind.FUNCTION)
                code == "," || code == "." ->
                    Key(code, cell, code, hint = if (mode == Mode.ABC) swipe[r, c] else "", kind = KeyKind.FUNCTION)
                mode == Mode.SYMBOLS -> Key(code, cell, code, kind = KeyKind.SYMBOL)
                else -> letterKey(code, cell, telex, swipe[r, c])
            }
        }
        keys += Key(KeyCode.SPACE, spaceCells, "", sub = "cách", kind = KeyKind.SPACE)
        return keys
    }

    private fun letterKey(code: String, cell: List<Pair<Int, Int>>, telex: TelexConfig, hint: String): Key {
        val ch = code[0]
        val tv = telex.toneOf(ch)
        val kind = when {
            tv != 0 -> KeyKind.TONE
            ch in "aeiouyw" -> KeyKind.VOWEL
            else -> KeyKind.CONSONANT
        }
        // Không ghi dấu (á, ả…) hay "ư" dưới chữ: màu phím đã cho biết phím dấu / nguyên âm.
        return Key(code, cell, code.uppercase(), hint = hint, kind = kind)
    }
}
