package com.telehex.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.telehex.telex.TelexEngine

/** Thanh trên bàn phím (~52dp): 6 biến thể dấu của âm tiết đang soạn. Chạm để đổi dấu. */
class CandidateBar(context: Context) : HorizontalScrollView(context) {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val p = dp(6)
        setPadding(p, 0, p, 0)
    }

    var theme: Theme = Theme.LIGHT
        set(value) { field = value; setBackgroundColor(value.barBg) }

    var onPick: ((Int) -> Unit)? = null

    init {
        isHorizontalScrollBarEnabled = false
        addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        setBackgroundColor(theme.barBg)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    fun show(candidates: List<TelexEngine.Candidate>) {
        row.removeAllViews()
        for (c in candidates) {
            val tv = TextView(context).apply {
                text = c.text
                textSize = 17f
                gravity = Gravity.CENTER
                val px = dp(12)
                setPadding(px, 0, px, 0)
                minWidth = dp(44)
                setTextColor(if (c.selected) theme.chipActiveFg else theme.chipFg)
                background = GradientDrawable().apply {
                    cornerRadius = dp(19).toFloat()
                    setColor(if (c.selected) theme.chipActiveBg else theme.chipBg)
                }
                setOnClickListener { onPick?.invoke(c.tone) }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
            lp.marginEnd = dp(6)
            row.addView(tv, lp)
        }
        scrollTo(0, 0)
    }

    fun clear() = row.removeAllViews()

    /** Một nút trên thanh chỉnh sửa: nhãn, gợi ý cử chỉ (dòng nhỏ), hành động. */
    class EditAction(val label: String, val gestureHint: String, val enabled: Boolean = true, val action: () -> Unit)

    /** Thanh chỉnh sửa thay cho gợi ý khi có vùng chọn hoặc đang ở chế độ con trỏ. */
    fun showEditBar(actions: List<EditAction>) {
        row.removeAllViews()
        for (a in actions) {
            val tv = TextView(context).apply {
                text = if (a.gestureHint.isEmpty()) a.label else "${a.label}\n${a.gestureHint}"
                textSize = 12f
                setLineSpacing(0f, 0.95f)
                gravity = Gravity.CENTER
                val px = dp(8)
                setPadding(px, 0, px, 0)
                minWidth = dp(52)
                alpha = if (a.enabled) 1f else 0.4f
                setTextColor(theme.chipFg)
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(theme.chipBg) }
                isEnabled = a.enabled
                setOnClickListener { a.action() }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
            lp.marginEnd = dp(6)
            row.addView(tv, lp)
        }
        scrollTo(0, 0)
    }
}
