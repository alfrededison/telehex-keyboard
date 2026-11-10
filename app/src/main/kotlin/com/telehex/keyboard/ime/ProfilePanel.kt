package com.telehex.keyboard.ime

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.telehex.keyboard.layout.GridSpec
import com.telehex.keyboard.layout.Profile

/**
 * Danh sách profile thay lưới phím (mở từ nút Profile của bảng sửa văn bản): profile có sẵn rồi profile của bạn,
 * profile đang dùng tô màu và có ✓. Chạm một profile để dùng ngay; "← Bàn phím" để quay lại không đổi gì.
 */
class ProfilePanel(
    context: Context,
    theme: Theme,
    profiles: List<Profile>,
    active: String,
    onClose: () -> Unit,
    onPick: (Profile) -> Unit,
) : LinearLayout(context) {

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "← Bàn phím"
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), 0, dp(16), 0)
                setTextColor(theme.functionFg)
                contentDescription = "Đóng danh sách profile"
                setOnClickListener { onClose() }
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            addView(TextView(context).apply {
                text = "Chọn profile"
                textSize = 14f
                setTextColor(theme.hintFg)
            })
        }, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))

        val list = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(8), 0, dp(8), dp(8)) }
        for (p in profiles) {
            val on = p.name == active
            val fg = if (on) theme.chipActiveFg else theme.chipFg
            list.addView(LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                setPadding(dp(14), dp(6), dp(14), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(if (on) theme.chipActiveBg else theme.chipBg)
                }
                contentDescription = "${p.name}, ${kindName(p)}" + if (on) ", đang dùng" else ""
                setOnClickListener { onPick(p) }
                addView(LinearLayout(context).apply {
                    orientation = VERTICAL
                    addView(TextView(context).apply {
                        text = p.name
                        textSize = 16f
                        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                        setTextColor(fg)
                        maxLines = 1
                    })
                    addView(TextView(context).apply {
                        text = kindName(p) + if (p.isBuiltIn) " · có sẵn" else ""
                        textSize = 12f
                        setTextColor(fg)
                        alpha = 0.75f
                    })
                }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                if (on) addView(TextView(context).apply { text = "✓"; textSize = 18f; setTextColor(fg) })
            }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
        }
        addView(ScrollView(context).apply { addView(list) }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun kindName(p: Profile) = when (p.spec) {
        GridSpec.HEX7 -> "Tổ ong 7 cột"
        GridSpec.QWERTY -> "QWERTY 10 cột"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
