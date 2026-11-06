package com.telehex.keyboard.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout

/** Giao diện chung cho các màn hình Cài đặt: nút có khoảng cách, màu theo colorAccent của theme. */
internal object SettingsUi {
    fun accentColor(context: Context): Int {
        val a = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent))
        try { return a.getColor(0, 0xFF1A5FD0.toInt()) } finally { a.recycle() }
    }
}

internal fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

internal fun LinearLayout.add(v: View, topMargin: Int = 0, bottomMargin: Int = 0) =
    addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
        it.topMargin = topMargin; it.bottomMargin = bottomMargin
    })

/** Nút tông màu nhấn, bo góc, cách nút kề 8dp để không dính vào nhau. */
internal fun LinearLayout.addButton(label: String, onClick: Button.() -> Unit): Button {
    val b = settingsButton(context, label, onClick)
    add(b, context.dp(4), context.dp(4))
    return b
}

internal fun settingsButton(context: Context, label: String, onClick: Button.() -> Unit): Button = Button(context).apply {
    val accent = SettingsUi.accentColor(context)
    text = label
    isAllCaps = false
    textSize = 15f
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    setTextColor(accent)
    stateListAnimator = null // bỏ bóng đổ mặc định
    minHeight = context.dp(48)
    setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
    val fill = GradientDrawable().apply {
        cornerRadius = context.dp(12).toFloat()
        setColor((accent and 0x00FFFFFF) or 0x24000000)
    }
    background = RippleDrawable(ColorStateList.valueOf((accent and 0x00FFFFFF) or 0x3D000000), fill, null)
    setOnClickListener { onClick() }
}
