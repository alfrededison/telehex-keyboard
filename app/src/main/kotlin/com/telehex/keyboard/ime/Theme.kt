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

        /** [mode]: "system" | "light" | "dark" */
        fun resolve(context: Context, mode: String): Theme = if (isDark(context, mode)) DARK else LIGHT

        fun isDark(context: Context, mode: String): Boolean = when (mode) {
            "light" -> false
            "dark" -> true
            else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        }
    }
}

enum class KeyKind { CONSONANT, VOWEL, TONE, FUNCTION, ENTER, SPACE, SYMBOL }
