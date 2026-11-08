package com.telehex.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView

/** Danh sách mở rộng thay lưới phím, không đổi chiều cao IME hay chuyển focus khỏi ô nhập. */
class SuggestionPanel(context: Context, theme: Theme, choices: List<Choice>, onClose: () -> Unit) : LinearLayout(context) {
    class Choice(val text: String, val onForget: (() -> Unit)? = null, val onPick: () -> Unit)

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        val back = TextView(context).apply {
            text = "← Bàn phím"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            setTextColor(theme.functionFg)
            contentDescription = "Đóng gợi ý mở rộng"
            setOnClickListener { onClose() }
        }
        addView(back, LayoutParams(LayoutParams.MATCH_PARENT, dp(48)))
        addView(GridView(context).apply {
            numColumns = 3
            horizontalSpacing = dp(6)
            verticalSpacing = dp(6)
            setPadding(dp(8), dp(4), dp(8), dp(8))
            adapter = object : BaseAdapter() {
                override fun getCount() = choices.size
                override fun getItem(position: Int) = choices[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
                    ((convertView as? TextView) ?: TextView(context)).apply {
                        text = choices[position].text
                        textSize = 18f
                        gravity = Gravity.CENTER
                        minHeight = dp(48)
                        setTextColor(theme.chipFg)
                        background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(theme.chipBg) }
                    }
            }
            setOnItemClickListener { _, _, position, _ -> choices[position].onPick() }
            setOnItemLongClickListener { _, _, position, _ ->
                choices[position].onForget?.let { it(); true } ?: false
            }
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
