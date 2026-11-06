package com.telehex.keyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.telehex.keyboard.data.Prefs
import com.telehex.keyboard.ime.EmojiData
import com.telehex.keyboard.ime.Gesture
import com.telehex.keyboard.ime.Key
import com.telehex.keyboard.ime.KeyPage
import com.telehex.keyboard.ime.KeyboardView
import com.telehex.keyboard.ime.Mode
import com.telehex.keyboard.ime.Theme
import com.telehex.keyboard.layout.KeyCode

/** Chạm phím → bảng chọn ký tự vuốt xuống: Số & ký hiệu / emoji, cộng ô nhập tự do. */
class CustomKeysActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var view: KeyboardView

    private val symbols = ("1 2 3 4 5 6 7 8 9 0 @ # & * - + = ( ) _ $ % \" ' : ; / \\ ! ? ~ ^ | < > [ ] { } € ₫ £ ¥ … • ° × ÷").split(' ')

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(24)) }
        setContentView(ScrollView(this).apply { addView(col) })
        col.add(TextView(this).apply {
            text = "Chạm một phím để chọn ký tự, ký hiệu, emoji hoặc đoạn văn bản ngắn (tối đa $MAX_LEN ký tự) sẽ chèn khi vuốt xuống."
        })
        view = KeyboardView(this).apply {
            theme = Theme.resolve(context, prefs.themeMode)
            hapticEnabled = false
            listener = object : KeyboardView.Listener {
                override fun onKeyDown(key: Key) {}
                override fun onKeyMove(key: Key, dx: Float, dy: Float, dt: Long) {}
                override fun onKeyCancel(key: Key) {}
                override fun onKeyUp(key: Key, gesture: Gesture, dx: Float, dy: Float, dt: Long) {
                    if (key.isLetter || key.code == "," || key.code == ".") pick(key.code)
                }
            }
        }
        refresh()
        col.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        col.addButton("Khôi phục mặc định tất cả") { prefs.customKeys = emptyMap(); refresh() }
    }

    private fun refresh() {
        view.keys = KeyPage.build(prefs.layout, Mode.ABC, 1, prefs.telexConfig(), prefs::hintOf, "↵")
    }

    private fun pick(code: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        val input = EditText(this).apply {
            hint = "Nhập tự do"
            setText(prefs.hintOf(code))
            filters = arrayOf(android.text.InputFilter.LengthFilter(MAX_LEN))
        }
        box.addView(input)
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val grid = GridView(this).apply { numColumns = 8 }
        var items: List<String> = symbols
        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup?): android.view.View {
                val tv = (convertView as? TextView) ?: TextView(this@CustomKeysActivity).apply {
                    gravity = Gravity.CENTER; textSize = 22f
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
                }
                tv.text = items[position]
                return tv
            }
        }
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, pos, _ -> input.setText(items[pos]) }
        fun tab(name: String, list: List<String>) = settingsButton(this, name) { items = list; adapter.notifyDataSetChanged() }
            .apply { setPadding(0, paddingTop, 0, paddingBottom) }
        fun tabParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(2); marginEnd = dp(2) }
        tabs.addView(tab("Ký hiệu", symbols), tabParams())
        for (c in EmojiData.get(this).categories.take(4)) tabs.addView(tab(c.icon, c.emoji), tabParams())
        box.addView(tabs)
        box.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))

        AlertDialog.Builder(this)
            .setTitle("Vuốt xuống phím ${code.uppercase()}")
            .setView(box)
            .setPositiveButton("Lưu") { _, _ ->
                val v = input.text.toString()
                if (v.isEmpty()) { Toast.makeText(this, "Để trống: vuốt xuống phím này sẽ không chèn gì", Toast.LENGTH_SHORT).show() }
                prefs.customKeys = prefs.customKeys + (code to v)
                refresh()
            }
            .setNeutralButton("Mặc định") { _, _ -> prefs.customKeys = prefs.customKeys - code; refresh() }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    companion object {
        const val MAX_LEN = 8
        @Suppress("unused") private val FUNCTION_CODES = setOf(KeyCode.EMOJI, KeyCode.NUMBERS, KeyCode.SETTINGS, KeyCode.ENTER)
    }
}
