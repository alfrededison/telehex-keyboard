package com.telehex.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.widget.TextView

/** Viên thông báo ngắn nổi trên bàn phím sau một hành động (hoàn tác, dán…); không nhận chạm. */
class ActionToast(context: Context) : TextView(context) {
    private val hide = Runnable { animate().alpha(0f).setDuration(220).withEndAction { visibility = View.GONE } }

    init {
        textSize = 15f
        val px = dp(16); val py = dp(8)
        setPadding(px, py, px, py)
        visibility = View.GONE
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    var theme: Theme = Theme.LIGHT
        set(value) {
            field = value
            setTextColor(value.chipActiveFg)
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(value.chipActiveBg) }
        }

    fun show(message: String) {
        removeCallbacks(hide)
        animate().cancel()
        text = message
        announceForAccessibility(message)
        if (visibility != View.VISIBLE) { alpha = 0f; visibility = View.VISIBLE }
        animate().alpha(1f).setDuration(90)
        postDelayed(hide, 900)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
