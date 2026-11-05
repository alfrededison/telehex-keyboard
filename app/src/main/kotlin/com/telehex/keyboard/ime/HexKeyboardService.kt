package com.telehex.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.TextView

class HexKeyboardService : InputMethodService() {
    override fun onCreateInputView(): View = TextView(this).apply { text = "TeleHex" }
}
