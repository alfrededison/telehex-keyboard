package com.telehex.keyboard.ime

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bảng emoji kiểu Gboard: lưới 8 cột cuộn được; hàng dưới có ABC, các tab và nút xoá.
 */
class EmojiPanel(context: Context, private val theme: Theme) : LinearLayout(context) {

    interface Listener {
        fun onEmoji(emoji: String)
        fun onAbc()
        fun onBackspace()
    }

    var listener: Listener? = null
    /** Danh sách Gần đây; chỉ áp dụng khi mở lại tab để không xê dịch dưới ngón tay. */
    var recent: List<String> = emptyList()

    private var currentTab = "face"
    private val grid = GridView(context)
    private val tabRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val tabButtons = HashMap<String, TextView>()
    private var items: List<String> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(context).apply {
                gravity = Gravity.CENTER
                textSize = 26f
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
            }
            tv.text = items[position]
            return tv
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(theme.background)
        grid.numColumns = 8
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ -> listener?.onEmoji(items[position]) }
        grid.selector = GradientDrawable().apply { setColor(theme.pressedOverlay) }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(2), dp(6), dp(2))
        }
        bottom.addView(button("ABC", 14f) { listener?.onAbc() }, LayoutParams(dp(56), dp(38)))
        val scroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        scroll.addView(tabRow)
        bottom.addView(scroll, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        bottom.addView(button("⌫", 18f) { listener?.onBackspace() }, LayoutParams(dp(56), dp(38)))
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))

        addTab(RECENT, "🕘")
        for (c in EmojiData.CATEGORIES) addTab(c.id, c.icon)
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun button(text: String, size: Float, onClick: () -> Unit): TextView = TextView(context).apply {
        this.text = text
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(theme.functionFg)
        background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(theme.functionBg) }
        setOnClickListener { onClick() }
    }

    private fun addTab(id: String, icon: String) {
        val tv = TextView(context).apply {
            text = icon
            textSize = 18f
            gravity = Gravity.CENTER
            setOnClickListener { showTab(id) }
        }
        val lp = LayoutParams(dp(40), dp(38)).apply { marginStart = dp(4) }
        tabRow.addView(tv, lp)
        tabButtons[id] = tv
    }

    fun open() {
        showTab(if (recent.isNotEmpty()) RECENT else "face")
    }

    private fun showTab(id: String) {
        currentTab = id
        items = if (id == RECENT) recent else EmojiData.CATEGORIES.first { it.id == id }.emoji
        adapter.notifyDataSetChanged()
        grid.setSelection(0)
        for ((tid, tv) in tabButtons) {
            tv.background = if (tid == id) GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(theme.functionBg) } else null
        }
    }

    companion object { const val RECENT = "recent" }
}
