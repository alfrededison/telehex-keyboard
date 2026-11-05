package com.telehex.keyboard

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout

/** Ô nhập để thử bàn phím trên emulator (chỉ có trong bản debug). */
class TestInputActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 32) }
        val text = EditText(this).apply {
            id = 1001
            hint = "Thử gõ"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            textSize = 22f
        }
        val phone = EditText(this).apply { id = 1002; hint = "Điện thoại"; inputType = InputType.TYPE_CLASS_PHONE }
        val password = EditText(this).apply { id = 1003; hint = "Mật khẩu"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        root.addView(text); root.addView(phone); root.addView(password)
        setContentView(root)
        text.requestFocus()
    }
}
