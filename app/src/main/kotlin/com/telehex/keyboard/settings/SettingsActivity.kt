package com.telehex.keyboard.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.telehex.keyboard.data.Prefs

/** Màn hình Cài đặt (View thuần, không thêm dependency). */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(col) })

        col.add(TextView(this).apply {
            text = "TeleHex không xin quyền INTERNET. Không gửi dữ liệu gõ đi đâu; mọi xử lý nằm trên máy."
            textSize = 14f
        })

        col.add(header("Bật bàn phím"))
        col.add(Button(this).apply {
            text = "1. Bật TeleHex trong Cài đặt hệ thống"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        col.add(Button(this).apply {
            text = "2. Chọn TeleHex làm bàn phím hiện tại"
            setOnClickListener { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        })
        col.add(EditText(this).apply {
            hint = "Thử gõ ở đây"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        })

        col.add(header("Telex"))
        col.add(TextView(this).apply { text = "Kiểu đặt dấu" })
        col.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            val old = RadioButton(context).apply { text = "Cũ (hòa, thúy)"; id = 1 }
            val new = RadioButton(context).apply { text = "Mới (hoà, thuý)"; id = 2 }
            addView(old); addView(new)
            check(if (prefs.newToneStyle) 2 else 1)
            setOnCheckedChangeListener { _, id -> prefs.newToneStyle = id == 2 }
        })
        col.add(switch("L cũng là dấu huyền (lal → là)", prefs.lAsGrave) { prefs.lAsGrave = it })
        col.add(switch("Tự khôi phục từ tiếng Anh (windows không thành ưindows)", prefs.englishFallback) { prefs.englishFallback = it })

        col.add(header("Bàn phím"))
        col.add(Button(this).apply {
            text = "Sắp xếp phím"
            setOnClickListener { startActivity(Intent(context, LayoutEditorActivity::class.java)) }
        })
        col.add(Button(this).apply {
            text = "Ký tự vuốt xuống"
            setOnClickListener { startActivity(Intent(context, CustomKeysActivity::class.java)) }
        })
        col.add(switch("Rung khi chạm", prefs.haptic) { prefs.haptic = it })
        col.add(switch("Âm thanh khi chạm", prefs.sound) { prefs.sound = it })
        col.add(TextView(this).apply { text = "Giao diện" })
        col.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            val ids = listOf("system" to "Theo hệ thống", "light" to "Sáng", "dark" to "Tối")
            ids.forEachIndexed { i, (_, label) -> addView(RadioButton(context).apply { text = label; id = i + 1 }) }
            check(ids.indexOfFirst { it.first == prefs.themeMode } + 1)
            setOnCheckedChangeListener { _, id -> prefs.themeMode = ids[id - 1].first }
        })
        val heightLabel = TextView(this).apply { text = "Khoảng trống dưới bàn phím: ${prefs.extraBottomDp} dp" }
        col.add(heightLabel)
        col.add(SeekBar(this).apply {
            max = 80
            progress = prefs.extraBottomDp
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    heightLabel.text = "Khoảng trống dưới bàn phím: $p dp"
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { prefs.extraBottomDp = sb?.progress ?: 0 }
            })
        })

        col.add(header("Tương thích"))
        col.add(TextView(this).apply {
            text = "Các app xử lý composing không ổn (Zalo, Messenger…) dùng chế độ dự phòng. Mỗi dòng một tên package."
            textSize = 13f
        })
        col.add(EditText(this).apply {
            setText(prefs.fallbackPackages.sorted().joinToString("\n"))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setOnFocusChangeListener { v, hasFocus ->
                if (!hasFocus) prefs.fallbackPackages = (v as EditText).text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }
        })
    }

    private fun header(text: String) = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(20), 0, dp(6))
    }

    @Suppress("DEPRECATION")
    private fun switch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = label
        isChecked = checked
        setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    private fun LinearLayout.add(v: android.view.View) =
        addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
