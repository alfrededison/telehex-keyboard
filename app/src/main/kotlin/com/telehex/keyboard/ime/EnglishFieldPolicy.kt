package com.telehex.keyboard.ime

import android.text.InputType

/** Chỉ gợi ý cho ô văn bản thường; tôn trọng cờ no-suggestions của editor (ví dụ ô mã). */
object EnglishFieldPolicy {
    fun allows(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (inputType and (InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) return false
        return inputType and InputType.TYPE_MASK_VARIATION in setOf(
            InputType.TYPE_TEXT_VARIATION_NORMAL, InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE, InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
        )
    }
}
