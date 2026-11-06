package com.telehex.keyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.telehex.keyboard.ime.EmojiData

/** Hộp chọn ký tự vuốt xuống cho một ô: bảng ký hiệu / emoji, cộng ô nhập tự do. */
internal object SwipeCharPicker {
    const val MAX_LEN = 8

    private val SYMBOLS = ("1 2 3 4 5 6 7 8 9 0 @ # & * - + = ( ) _ $ % \" ' : ; / \\ ! ? ~ ^ | < > [ ] { } € ₫ £ ¥ … • ° × ÷").split(' ')

    /** @param onPick nhận giá trị mới; chuỗi rỗng = vuốt xuống phím này không chèn gì. */
    fun show(activity: Activity, keyLabel: String, current: String, default: String, onPick: (String) -> Unit) {
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(activity.dp(16), activity.dp(8), activity.dp(16), 0) }
        val input = EditText(activity).apply {
            hint = "Nhập tự do"
            setText(current)
            filters = arrayOf(InputFilter.LengthFilter(MAX_LEN))
        }
        box.addView(input)
        val tabs = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val grid = GridView(activity).apply { numColumns = 8 }
        var items: List<String> = SYMBOLS
        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val tv = (convertView as? TextView) ?: TextView(activity).apply {
                    gravity = Gravity.CENTER; textSize = 22f
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(44))
                }
                tv.text = items[position]
                return tv
            }
        }
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> input.setText(items[pos]) }
        fun tab(name: String, list: List<String>) = settingsButton(activity, name) { items = list; adapter.notifyDataSetChanged() }
            .apply { setPadding(0, paddingTop, 0, paddingBottom) }
        fun tabParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = activity.dp(2); marginEnd = activity.dp(2)
        }
        tabs.addView(tab("Ký hiệu", SYMBOLS), tabParams())
        for (c in EmojiData.get(activity).categories.take(4)) tabs.addView(tab(c.icon, c.emoji), tabParams())
        box.addView(tabs)
        box.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, activity.dp(220)))

        AlertDialog.Builder(activity)
            .setTitle("Vuốt xuống phím ${keyLabel.uppercase()}")
            .setView(box)
            .setPositiveButton("Chọn") { _, _ ->
                val v = input.text.toString()
                if (v.isEmpty()) Toast.makeText(activity, "Để trống: vuốt xuống phím này sẽ không chèn gì", Toast.LENGTH_SHORT).show()
                onPick(v)
            }
            .setNeutralButton("Mặc định") { _, _ -> onPick(default) }
            .setNegativeButton("Huỷ", null)
            .show()
    }
}
