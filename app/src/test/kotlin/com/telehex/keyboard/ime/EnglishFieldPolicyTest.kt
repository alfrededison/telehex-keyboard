package com.telehex.keyboard.ime

import android.text.InputType
import org.junit.Assert.*
import org.junit.Test

class EnglishFieldPolicyTest {
    @Test fun onlyAllowsOrdinaryTextEditors() {
        assertTrue(EnglishFieldPolicy.allows(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE))
        assertTrue(EnglishFieldPolicy.allows(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE))
        assertTrue(EnglishFieldPolicy.allows(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT))
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_FILTER)) {
            assertFalse(EnglishFieldPolicy.allows(InputType.TYPE_CLASS_TEXT or variation))
        }
        for (type in listOf(InputType.TYPE_NULL, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE)) assertFalse(EnglishFieldPolicy.allows(type))
    }
}
