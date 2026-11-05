package com.telehex.keyboard.ime

import com.telehex.keyboard.layout.KeyCode
import com.telehex.keyboard.layout.LayoutModel
import com.telehex.keyboard.layout.SymbolPages
import com.telehex.telex.TelexConfig

/** Một phím đã sẵn sàng để vẽ: mã, các ô nó chiếm, nhãn chính, nhãn phụ (dấu), gợi ý vuốt xuống, loại. */
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

enum class Mode { ABC, SYMBOLS, EMOJI, DIAL }

/** Dựng danh sách phím cho một trang từ layout + chế độ + cấu hình. */
object KeyPage {
    private val TONE_MARK = arrayOf("", "á", "à", "ả", "ã", "ạ", "bỏ")

    fun build(
        layout: LayoutModel,
        mode: Mode,
        symbolPage: Int,
        telex: TelexConfig,
        hintOf: (String) -> String,
        enterLabel: String,
    ): List<Key> {
        val rows: List<List<String?>> = when (mode) {
            Mode.SYMBOLS -> if (symbolPage == 2) SymbolPages.PAGE2 else SymbolPages.page1(layout)
            else -> layout.rows
        }
        val keys = ArrayList<Key>(32)
        val spaceCells = ArrayList<Pair<Int, Int>>(3)
        for (r in rows.indices) for (c in rows[r].indices) {
            val code = rows[r][c]
            if (code == null) { spaceCells.add(r to c); continue }
            val cell = listOf(r to c)
            keys += when {
                code == KeyCode.EMOJI -> Key(code, cell, "☺", kind = KeyKind.FUNCTION)
                code == KeyCode.NUMBERS -> Key(code, cell, if (mode == Mode.SYMBOLS) "ABC" else "123", kind = KeyKind.FUNCTION)
                code == KeyCode.SETTINGS -> Key(code, cell, "⚙", kind = KeyKind.FUNCTION)
                code == KeyCode.PAGE -> Key(code, cell, "$symbolPage/2", kind = KeyKind.FUNCTION)
                code == KeyCode.ENTER -> Key(code, cell, enterLabel, kind = KeyKind.ENTER)
                code == KeyCode.ABC -> Key(code, cell, "ABC", kind = KeyKind.FUNCTION)
                code == KeyCode.BACKSPACE -> Key(code, cell, "⌫", kind = KeyKind.FUNCTION)
                code == "," || code == "." ->
                    Key(code, cell, code, hint = if (mode == Mode.ABC) hintOf(code) else "", kind = KeyKind.FUNCTION)
                mode == Mode.SYMBOLS -> Key(code, cell, code, kind = KeyKind.SYMBOL)
                else -> letterKey(code, cell, telex, hintOf(code))
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
        val sub = when {
            tv != 0 -> TONE_MARK[tv]
            ch == 'w' -> "ư"
            else -> ""
        }
        return Key(code, cell, code.uppercase(), sub = sub, hint = hint, kind = kind)
    }
}
