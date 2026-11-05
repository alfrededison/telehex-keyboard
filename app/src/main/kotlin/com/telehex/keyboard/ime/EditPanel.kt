package com.telehex.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.telehex.keyboard.data.ClipboardStore

/**
 * Bảng sửa văn bản (cho ai không muốn dùng cử chỉ): mũi tên, Home/End, chọn từ/chọn hết,
 * cut/copy/paste, undo/redo, giọng nói, một tay; bên phải là clipboard history (chạm để dán,
 * giữ để ghim / xoá). Mở bằng cách giữ phím ⚙, chiếm chỗ lưới phím như bảng emoji.
 */
class EditPanel(context: Context, private val theme: Theme) : LinearLayout(context) {

    interface Listener {
        fun onAction(action: String)
        fun onPasteItem(text: String)
        fun onPinItem(text: String)
        fun onDeleteItem(text: String)
    }

    var listener: Listener? = null
    private val clipList = LinearLayout(context).apply { orientation = VERTICAL }
    private val clipEmpty = TextView(context).apply {
        text = "Chưa có gì trong clipboard.\nMục thường tự hết hạn sau 1 giờ; mục ghim giữ lâu dài."
        textSize = 12f
        setTextColor(theme.hintFg)
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    init {
        orientation = HORIZONTAL
        setBackgroundColor(theme.background)
        setPadding(dp(6), dp(6), dp(6), dp(6))

        val left = LinearLayout(context).apply { orientation = VERTICAL }
        fun row(vararg items: Pair<String, String>) {
            val r = LinearLayout(context).apply { orientation = HORIZONTAL }
            for ((label, action) in items) {
                r.addView(button(label) { listener?.onAction(action) }, LayoutParams(0, dp(40), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            }
            left.addView(r, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        row("ABC" to "abc", "↑" to "up", "⇤" to "home", "⇥" to "end")
        row("←" to "left", "↓" to "down", "→" to "right", "Chọn từ" to "selectWord")
        row("Cắt" to "cut", "Chép" to "copy", "Dán" to "paste", "Chọn hết" to "selectAll")
        row("↶ Undo" to "undo", "↷ Redo" to "redo", "🎤" to "voice", "⌫" to "backspace")
        addView(left, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.1f))

        val right = LinearLayout(context).apply { orientation = VERTICAL; setPadding(dp(6), 0, 0, 0) }
        right.addView(TextView(context).apply {
            text = "Clipboard"
            textSize = 12f
            setTextColor(theme.hintFg)
            setPadding(dp(4), 0, 0, dp(2))
        })
        val scroll = ScrollView(context)
        scroll.addView(clipList, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        right.addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(right, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
    }

    fun showClipboard(items: List<ClipboardStore.Item>) {
        clipList.removeAllViews()
        if (items.isEmpty()) { clipList.addView(clipEmpty); return }
        for (item in items) {
            val tv = TextView(context).apply {
                text = (if (item.pinned) "📌 " else "") + item.text.replace('\n', ' ').take(60)
                textSize = 13f
                maxLines = 2
                setTextColor(theme.chipFg)
                setPadding(dp(8), dp(6), dp(8), dp(6))
                background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(theme.chipBg) }
                setOnClickListener { listener?.onPasteItem(item.text) }
                setOnLongClickListener {
                    android.widget.PopupMenu(context, this).apply {
                        menu.add(if (item.pinned) "Bỏ ghim" else "Ghim").setOnMenuItemClickListener { listener?.onPinItem(item.text); true }
                        menu.add("Xoá").setOnMenuItemClickListener { listener?.onDeleteItem(item.text); true }
                    }.show()
                    true
                }
            }
            clipList.addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) })
        }
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun button(text: String, onClick: () -> Unit): TextView = TextView(context).apply {
        this.text = text
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(theme.functionFg)
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(theme.functionBg) }
        setOnClickListener { onClick() }
    }
}
