package com.telehex.layout

/** Nhóm của một phím, quyết định màu nền và màu chữ. Bảng màu do từng app giữ; xếp phím vào nhóm thì dùng chung ở đây. */
enum class KeyKind { CONSONANT, VOWEL, TONE, FUNCTION, SHIFT_BACK, ENTER, SPACE, SYMBOL }

/** Xếp phím vào [KeyKind] theo mã phím, dùng chung cho hai app. */
object KeyKinds {
    const val VOWELS = "aeiouyw"

    /**
     * Phím của lớp chữ: phím cách ([code] null), ↵, ⇧ và ⌫ có nhóm riêng; chữ là phím dấu ([toneLetters], theo cấu hình
     * Telex), nguyên âm hoặc phụ âm; số của hàng số như phụ âm; còn lại (123, ☺, ⚙, dấu câu…) là phím chức năng.
     */
    fun ofLetterLayer(code: String?, toneLetters: String): KeyKind = when {
        code == null || code == KeyCode.SPACE -> KeyKind.SPACE
        code == KeyCode.ENTER -> KeyKind.ENTER
        code == KeyCode.SHIFT || code == KeyCode.BACKSPACE -> KeyKind.SHIFT_BACK
        KeyCode.isLetter(code) -> when {
            code[0] in toneLetters -> KeyKind.TONE
            code[0] in VOWELS -> KeyKind.VOWEL
            else -> KeyKind.CONSONANT
        }
        KeyCode.isDigit(code) -> KeyKind.CONSONANT
        else -> KeyKind.FUNCTION
    }

    /** Ô của lớp 123 đang hiện giá trị [value] của trang: phím chuyển trang là phím chức năng, còn lại là ký hiệu. */
    fun ofSymbolValue(value: String): KeyKind = if (value == KeyCode.PAGE) KeyKind.FUNCTION else KeyKind.SYMBOL

    /** Phím của bàn phím số: số và `.` như phụ âm, `-` và ABC là phím chức năng, ⌫ cùng nhóm với ⇧. */
    fun ofDialpad(code: String): KeyKind = when (code) {
        KeyCode.SPACE -> KeyKind.SPACE
        KeyCode.ENTER -> KeyKind.ENTER
        KeyCode.BACKSPACE -> KeyKind.SHIFT_BACK
        KeyCode.ABC, "-" -> KeyKind.FUNCTION
        else -> KeyKind.CONSONANT
    }
}
