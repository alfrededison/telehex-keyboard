package com.telehex.keyboard.ime

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** Màu theo loại phím: phụ âm trắng, nguyên âm xanh nhạt, phím dấu cam nhạt, chức năng xám, Enter tối. */
data class Theme(
    val background: Int,
    val consonantBg: Int, val consonantFg: Int,
    val vowelBg: Int, val vowelFg: Int,
    val toneBg: Int, val toneFg: Int,
    val functionBg: Int, val functionFg: Int,
    val enterBg: Int, val enterFg: Int,
    val spaceBg: Int, val spaceFg: Int,
    val hintFg: Int,
    val pressedOverlay: Int,
    val barBg: Int, val chipBg: Int, val chipFg: Int, val chipActiveBg: Int, val chipActiveFg: Int,
    val isDark: Boolean,
) {
    fun bgFor(kind: KeyKind): Int = when (kind) {
        KeyKind.CONSONANT, KeyKind.SYMBOL -> consonantBg
        KeyKind.VOWEL -> vowelBg
        KeyKind.TONE -> toneBg
        KeyKind.FUNCTION -> functionBg
        KeyKind.ENTER -> enterBg
        KeyKind.SPACE -> spaceBg
    }

    fun fgFor(kind: KeyKind): Int = when (kind) {
        KeyKind.CONSONANT, KeyKind.SYMBOL -> consonantFg
        KeyKind.VOWEL -> vowelFg
        KeyKind.TONE -> toneFg
        KeyKind.FUNCTION -> functionFg
        KeyKind.ENTER -> enterFg
        KeyKind.SPACE -> spaceFg
    }

    /**
     * Màu nhấn đổi phím nguyên âm (và ↵ / gợi ý đang chọn ở nền tối); phím dấu giữ màu cam để không lẫn.
     * [Accent.NONE] bỏ cả hai: nguyên âm và phím dấu cùng màu phụ âm.
     */
    fun withAccent(a: Accent): Theme {
        val t = if (isDark) copy(vowelBg = a.darkBg, vowelFg = a.darkFg, enterBg = a.lightBg, chipActiveBg = a.lightBg)
        else copy(vowelBg = a.lightBg, vowelFg = a.lightFg)
        return if (a == Accent.NONE) t.copy(toneBg = consonantBg, toneFg = consonantFg) else t
    }

    companion object {
        val LIGHT = Theme(
            background = Color.parseColor("#E9ECF0"),
            consonantBg = Color.parseColor("#FFFFFF"), consonantFg = Color.parseColor("#15191E"),
            vowelBg = Color.parseColor("#DCE8FB"), vowelFg = Color.parseColor("#0A3A80"),
            toneBg = Color.parseColor("#FFE2CC"), toneFg = Color.parseColor("#7A3300"),
            functionBg = Color.parseColor("#B9C2CC"), functionFg = Color.parseColor("#1F262E"),
            enterBg = Color.parseColor("#15191E"), enterFg = Color.parseColor("#FFFFFF"),
            spaceBg = Color.parseColor("#B9C2CC"), spaceFg = Color.parseColor("#1F262E"),
            hintFg = Color.parseColor("#5B6670"),
            pressedOverlay = Color.parseColor("#24000000"),
            barBg = Color.parseColor("#E9ECF0"), chipBg = Color.parseColor("#FFFFFF"), chipFg = Color.parseColor("#15191E"),
            chipActiveBg = Color.parseColor("#15191E"), chipActiveFg = Color.parseColor("#FFFFFF"),
            isDark = false,
        )
        val DARK = Theme(
            background = Color.parseColor("#15191E"),
            consonantBg = Color.parseColor("#2B3138"), consonantFg = Color.parseColor("#F2F4F6"),
            vowelBg = Color.parseColor("#1E3A64"), vowelFg = Color.parseColor("#CFE0FB"),
            toneBg = Color.parseColor("#5A3313"), toneFg = Color.parseColor("#FFD9BC"),
            functionBg = Color.parseColor("#3D454E"), functionFg = Color.parseColor("#E5E9ED"),
            enterBg = Color.parseColor("#DCE8FB"), enterFg = Color.parseColor("#15191E"),
            spaceBg = Color.parseColor("#3D454E"), spaceFg = Color.parseColor("#E5E9ED"),
            hintFg = Color.parseColor("#9AA5B1"),
            pressedOverlay = Color.parseColor("#33FFFFFF"),
            barBg = Color.parseColor("#15191E"), chipBg = Color.parseColor("#2B3138"), chipFg = Color.parseColor("#F2F4F6"),
            chipActiveBg = Color.parseColor("#DCE8FB"), chipActiveFg = Color.parseColor("#15191E"),
            isDark = true,
        )

        /** [mode]: "system" | "light" | "dark"; [accent]: [Accent.id]. */
        fun resolve(context: Context, mode: String, accent: String): Theme =
            (if (isDark(context, mode)) DARK else LIGHT).withAccent(Accent.of(accent))

        fun isDark(context: Context, mode: String): Boolean = when (mode) {
            "light" -> false
            "dark" -> true
            else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
    }
}

/**
 * Màu nhấn người dùng chọn. [lightBg]/[lightFg], [darkBg]/[darkFg] là phím nguyên âm trên nền sáng / tối, [swatch] là màu ô chọn trong Cài đặt.
 */
enum class Accent(
    val id: String, val label: String, val swatch: Int,
    val lightBg: Int, val lightFg: Int, val darkBg: Int, val darkFg: Int,
) {
    BLUE("blue", "Xanh dương", 0xFF1A5FD0.toInt(), 0xFFDCE8FB.toInt(), 0xFF0A3A80.toInt(), 0xFF1E3A64.toInt(), 0xFFCFE0FB.toInt()),
    GREEN("green", "Xanh lá", 0xFF1E7E45.toInt(), 0xFFD6F0DE.toInt(), 0xFF0B5A2A.toInt(), 0xFF1B4A2E.toInt(), 0xFFC8EBD3.toInt()),
    TEAL("teal", "Xanh ngọc", 0xFF00796B.toInt(), 0xFFD2F0ED.toInt(), 0xFF0B5550.toInt(), 0xFF134A47.toInt(), 0xFFC6ECE9.toInt()),
    PURPLE("purple", "Tím", 0xFF6E3FC0.toInt(), 0xFFE8DEF8.toInt(), 0xFF4A2382.toInt(), 0xFF3B2A5E.toInt(), 0xFFE3D5FA.toInt()),
    PINK("pink", "Hồng", 0xFFB8235A.toInt(), 0xFFFADDE9.toInt(), 0xFF8A1C4A.toInt(), 0xFF5A2340.toInt(), 0xFFFBD3E4.toInt()),
    /** Không màu: nguyên âm và phím dấu cùng màu phụ âm. */
    NONE("none", "Không màu", 0xFF8A949E.toInt(), 0xFFFFFFFF.toInt(), 0xFF15191E.toInt(), 0xFF2B3138.toInt(), 0xFFF2F4F6.toInt());

    companion object {
        fun of(id: String): Accent = entries.firstOrNull { it.id == id } ?: BLUE
    }
}

enum class KeyKind { CONSONANT, VOWEL, TONE, FUNCTION, ENTER, SPACE, SYMBOL }
