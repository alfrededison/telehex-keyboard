package com.telehex.keyboard.ime

import com.telehex.keyboard.R
import com.telehex.layout.CellGrid
import com.telehex.layout.KeyCode
import com.telehex.layout.KeyKinds
import com.telehex.layout.LayoutModel
import com.telehex.layout.SymbolPages
import com.telehex.telex.TelexConfig

/**
 * Một phím đã sẵn sàng để vẽ: mã, các ô nó chiếm, nhãn chính, nhãn phụ, ký tự vuốt xuống, loại. [icon] / [hintIcon]
 * (drawable, 0 = không có) vẽ thay cho [label] / [hint], tô theo màu chữ của phím; [label] vẫn dùng cho trình đọc màn
 * hình và thông báo.
 */
data class Key(
    val code: String,
    val cells: List<Pair<Int, Int>>,
    val label: String,
    val sub: String = "",
    val hint: String = "",
    val kind: KeyKind,
    val icon: Int = 0,
    val hintIcon: Int = 0,
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
        // Nhóm phím (màu) theo quy tắc chung với iOS ([KeyKinds])
        val tones = ("abcdefghijklmnopqrstuvwxyz").filter { telex.toneOf(it) != 0 }
        fun kindOf(code: String) = KeyKinds.ofLetterLayer(code, tones)
        val keys = ArrayList<Key>(32)
        val spaceCells = ArrayList<Pair<Int, Int>>(4)
        for (r in rows.indices) for (c in rows[r].indices) {
            val code = rows[r][c]
            if (code == null) { spaceCells.add(r to c); continue }
            val cell = listOf(r to c)
            keys += when {
                // Icon phẳng thay cho emoji màu, như các phím chức năng khác
                code == KeyCode.EMOJI -> Key(code, cell, "☺", kind = kindOf(code), icon = R.drawable.ic_key_emoji)
                code == KeyCode.NUMBERS -> Key(code, cell, if (mode == Mode.SYMBOLS) "ABC" else "123", kind = kindOf(code))
                code == KeyCode.SETTINGS ->
                    Key(code, cell, "⚙", hint = "📋", kind = kindOf(code), hintIcon = R.drawable.ic_edit_paste)
                code == KeyCode.PAGE -> Key(code, cell, "$symbolPage/2", kind = kindOf(code))
                code == KeyCode.DIAL -> Key(code, cell, "1234", kind = kindOf(code))
                code == KeyCode.ENTER -> Key(code, cell, enterLabel, kind = kindOf(code))
                code == KeyCode.ABC -> Key(code, cell, "ABC", kind = kindOf(code))
                code == KeyCode.BACKSPACE -> Key(code, cell, "⌫", kind = kindOf(code))
                code == KeyCode.SHIFT -> Key(code, cell, "⇧", kind = kindOf(code))
                // Phím dấu câu của lớp chữ (ở lớp 123 chỉ khi ô trống, hiện lại dấu đó). Ký hiệu `' ! ?` của trang 123
                // vẫn là phím ký hiệu: kéo đổi chỗ được, màu như ký hiệu khác.
                KeyCode.isPunct(code) && (mode != Mode.SYMBOLS || layout[r, c] == code) ->
                    Key(code, cell, code, hint = if (mode == Mode.ABC) swipe[r, c] else "", kind = kindOf(code))
                mode == Mode.SYMBOLS -> Key(code, cell, code, kind = KeyKinds.ofSymbolValue(code))
                KeyCode.isDigit(code) -> Key(code, cell, code, hint = swipe[r, c], kind = kindOf(code))
                // Không ghi dấu (á, ả…) hay "ư" dưới chữ: màu phím đã cho biết phím dấu / nguyên âm.
                else -> Key(code, cell, code.uppercase(), hint = swipe[r, c], kind = kindOf(code))
            }
        }
        keys += Key(KeyCode.SPACE, spaceCells, "", sub = "cách", kind = KeyKinds.ofLetterLayer(null, tones))
        return keys
    }
}
