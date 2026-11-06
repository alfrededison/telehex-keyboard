package com.telehex.keyboard.settings

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.telehex.keyboard.R
import com.telehex.keyboard.data.Prefs
import com.telehex.keyboard.ime.Theme

/** Giao diện chung cho các màn hình Cài đặt: sáng/tối theo tuỳ chọn của bàn phím và nút có khoảng cách. */
internal object SettingsUi {
    /** Gọi trước super.onCreate: sáng/tối theo tuỳ chọn Giao diện của bàn phím. Màu nhấn chỉ áp cho bàn phím. */
    fun applyTheme(activity: Activity, prefs: Prefs) {
        syncNightMode(activity, prefs.themeMode)
        val dark = Theme.isDark(activity, prefs.themeMode)
        activity.setTheme(if (dark) R.style.Theme_TeleHex_Dark else R.style.Theme_TeleHex_Light)
    }

    fun styleButton(b: Button, accent: Int) {
        b.setTextColor(accent)
        val fill = GradientDrawable().apply {
            cornerRadius = b.context.dp(12).toFloat()
            setColor(withAlpha(accent, 0x24))
        }
        b.background = RippleDrawable(ColorStateList.valueOf(withAlpha(accent, 0x3D)), fill, null)
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    /**
     * Android 12+ vẽ màn chờ lúc mở app theo chế độ tối của app, không theo windowDisablePreview,
     * nên báo cho hệ thống chế độ đã chọn. Không đổi gì thì hệ thống không phát config change.
     */
    private fun syncNightMode(context: Context, mode: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val ui = context.getSystemService(UiModeManager::class.java) ?: return
        ui.setApplicationNightMode(when (mode) {
            "light" -> UiModeManager.MODE_NIGHT_NO
            "dark" -> UiModeManager.MODE_NIGHT_YES
            else -> UiModeManager.MODE_NIGHT_AUTO
        })
    }

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
    text = label
    isAllCaps = false
    textSize = 15f
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    stateListAnimator = null // bỏ bóng đổ mặc định
    minHeight = context.dp(48)
    setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(10))
    SettingsUi.styleButton(this, SettingsUi.accentColor(context))
    setOnClickListener { onClick() }
}
