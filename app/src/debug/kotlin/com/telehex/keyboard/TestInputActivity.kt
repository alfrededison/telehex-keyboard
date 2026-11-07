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
        // Debug: am start ... --es shortcuts "ko=không;dc=được" để thử gõ tắt mà không cần gõ vào Cài đặt
        intent.getStringExtra("shortcuts")?.let { spec ->
            com.telehex.keyboard.data.Prefs(this).shortcuts = spec.split(';').mapNotNull { kv ->
                val i = kv.indexOf('='); if (i <= 0) null else kv.substring(0, i) to kv.substring(i + 1)
            }.toMap()
        }
        if (intent.hasExtra("onehanded")) com.telehex.keyboard.data.Prefs(this).oneHanded = intent.getIntExtra("onehanded", 0)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 64, 32, 32) }
        val text = EditText(this).apply {
            id = android.view.View.generateViewId()
            hint = "Thử gõ"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            textSize = 22f
        }
        val phone = EditText(this).apply { id = android.view.View.generateViewId(); hint = "Điện thoại"; inputType = InputType.TYPE_CLASS_PHONE }
        val password = EditText(this).apply { id = android.view.View.generateViewId(); hint = "Mật khẩu"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val email = EditText(this).apply { id = android.view.View.generateViewId(); hint = "Email"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        root.addView(text); root.addView(phone); root.addView(password); root.addView(email)
        setContentView(root)
        text.requestFocus()
    }
}
