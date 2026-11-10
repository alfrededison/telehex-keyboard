package com.telehex.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.telehex.keyboard.data.ClipboardStore

/**
 * Bảng sửa văn bản (cho ai không muốn dùng cử chỉ): bên trái là lưới phím lục giác [EditPadView]
 * (mũi tên, đầu/cuối dòng, chọn từ/chọn hết, cut/copy/paste, undo/redo, đổi profile); bên phải là
 * clipboard history (chạm để dán, giữ để ghim / xoá). Mở bằng cách vuốt xuống phím ⚙, chiếm chỗ lưới phím như bảng emoji.
 */
class EditPanel(context: Context, private val theme: Theme) : LinearLayout(context) {

    interface Listener {
        fun onAction(action: String)
        fun onPasteItem(text: String)
        fun onPinItem(text: String)
        fun onDeleteItem(text: String)
    }

    var listener: Listener? = null
    val pad = EditPadView(context, theme).apply { onAction = { listener?.onAction(it) } }
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

        addView(pad, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.3f))

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
}
