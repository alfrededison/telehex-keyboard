package com.telehex.keyboard.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.View
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
            isSaveEnabled = false
            val old = RadioButton(context).apply { text = "Cũ (hòa, thúy)"; id = View.generateViewId() }
            val new = RadioButton(context).apply { text = "Mới (hoà, thuý)"; id = View.generateViewId() }
            addView(old); addView(new)
            check(if (prefs.newToneStyle) new.id else old.id)
            setOnCheckedChangeListener { _, id -> prefs.newToneStyle = id == new.id }
        })
        col.add(switch("L cũng là dấu huyền (lal → là)", prefs.lAsGrave) { prefs.lAsGrave = it })
        col.add(switch("Tự viết hoa chữ đầu câu", prefs.autoCapitalize) { prefs.autoCapitalize = it })
        col.add(switch("↵ gửi tin ở khung chat (giữ ↵ để xuống dòng)", prefs.enterSends) { prefs.enterSends = it })
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
        col.add(TextView(this).apply { text = "Chế độ một tay (lưới thu còn ~80%, lục giác vẫn đều)" })
        col.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            isSaveEnabled = false
            val opts = listOf("Tắt", "Dồn trái", "Dồn phải")
            val buttons = opts.map { label -> RadioButton(context).apply { text = label; id = View.generateViewId() }.also { addView(it) } }
            check(buttons[prefs.oneHanded.coerceIn(0, 2)].id)
            setOnCheckedChangeListener { _, id -> prefs.oneHanded = buttons.indexOfFirst { it.id == id } }
        })
        col.add(switch("Rung khi chạm", prefs.haptic) { prefs.haptic = it })
        col.add(switch("Âm thanh khi chạm", prefs.sound) { prefs.sound = it })
        col.add(TextView(this).apply { text = "Giao diện" })
        col.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            isSaveEnabled = false
            val modes = listOf("system" to "Theo hệ thống", "light" to "Sáng", "dark" to "Tối")
            val buttons = modes.map { (_, label) -> RadioButton(context).apply { text = label; id = View.generateViewId() }.also { addView(it) } }
            check(buttons[modes.indexOfFirst { it.first == prefs.themeMode }.coerceAtLeast(0)].id)
            setOnCheckedChangeListener { _, id -> prefs.themeMode = modes[buttons.indexOfFirst { it.id == id }].first }
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

        col.add(header("Emoji và clipboard"))
        col.add(TextView(this).apply { text = "Tông da mặc định (cho emoji nhận tông da)" })
        col.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            isSaveEnabled = false
            val tones = listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿")
            val buttons = tones.map { label -> RadioButton(context).apply { text = label; id = View.generateViewId() }.also { addView(it) } }
            check(buttons[prefs.skinTone.coerceIn(0, 5)].id)
            setOnCheckedChangeListener { _, id -> prefs.skinTone = buttons.indexOfFirst { it.id == id } }
        })
        col.add(switch("Lưu clipboard history (mục thường hết hạn sau 1 giờ, mục ghim giữ lâu)", prefs.clipboardHistory) { prefs.clipboardHistory = it })

        col.add(header("Gõ tắt"))
        col.add(TextView(this).apply {
            text = "Mỗi dòng một mục dạng  viết tắt = nội dung . Kích hoạt khi chạm cách. Ví dụ:\nko = không\ndc = được\n@@ = ten@email.com"
            textSize = 13f
        })
        col.add(EditText(this).apply {
            setText(prefs.shortcuts.entries.sortedBy { it.key }.joinToString("\n") { "${it.key} = ${it.value}" })
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 3
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    prefs.shortcuts = s.toString().lines().mapNotNull { line ->
                        val i = line.indexOf('=')
                        if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
                    }.filter { it.first.isNotEmpty() && it.second.isNotEmpty() }.toMap()
                }
            })
        })

        col.add(header("Gợi ý (không tự sửa)"))
        col.add(TextView(this).apply {
            text = "Bàn phím không bao giờ tự đổi chữ đã gõ. Gợi ý âm tiết tiếp theo học từ chính bạn, hoàn toàn trên máy. Giữ một gợi ý để xoá nó khỏi từ điển cá nhân."
            textSize = 13f
        })
        col.add(switch("Gợi ý âm tiết tiếp theo", prefs.predictions) { prefs.predictions = it })
        col.add(switch("Chế độ ẩn danh (không học)", prefs.incognito) { prefs.incognito = it })
        val store = com.telehex.keyboard.data.PredictionStore(this)
        val packStatus = TextView(this).apply { text = packStatusText(store) }
        col.add(packStatus)
        col.add(Button(this).apply {
            text = "Nạp gói ngôn ngữ (.ngram)…"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                }, REQ_PACK)
            }
        })
        col.add(Button(this).apply {
            text = "Xoá gói ngôn ngữ đã nạp"
            setOnClickListener { store.removePack(); packStatus.text = packStatusText(store) }
        })
        col.add(Button(this).apply {
            text = "Xoá dữ liệu đã học"
            setOnClickListener { store.clearPersonal(); android.widget.Toast.makeText(context, "Đã xoá từ điển cá nhân", android.widget.Toast.LENGTH_SHORT).show() }
        })
        packStatusView = packStatus
        predictionStore = store

        col.add(header("Chống bấm nhầm"))
        col.add(switch("Học độ lệch chạm theo từng phím", prefs.learnTouchOffsets) { prefs.learnTouchOffsets = it })
        col.add(switch("Vùng chạm động: nới vùng phím hợp lệ tiếp theo (một dạng tự sửa ngầm)", prefs.dynamicHitZones) { prefs.dynamicHitZones = it })
        col.add(Button(this).apply {
            text = "Xoá dữ liệu độ lệch đã học"
            setOnClickListener { prefs.touchOffsets = emptyMap() }
        })

        col.add(header("Cử chỉ"))
        col.add(TextView(this).apply {
            text = "Phím cách: vuốt trái = xoá (ngắn: 1 ký tự, dài: theo từ, có bôi đen xem trước); giữ rồi kéo = di con trỏ; chạm đúp rồi giữ-kéo = chọn; vuốt phải = dán; khi có vùng chọn: vuốt lên = chép, vuốt trái = cắt. Hai ngón vuốt trái/phải = undo/redo, hai ngón giữ = chọn hết."
            textSize = 13f
        })
        col.add(slider("Ngưỡng vuốt", "dp", 12, 60, prefs.swipeThresholdDp) { prefs.swipeThresholdDp = it })
        col.add(slider("Giữ phím cách trước khi kéo (con trỏ)", "ms", 150, 800, prefs.holdMs) { prefs.holdMs = it })
        col.add(slider("Giữ lâu (long-press)", "ms", 250, 1000, prefs.longPressMs) { prefs.longPressMs = it })
        col.add(slider("Mỗi bước xoá từ / di con trỏ", "dp", 25, 100, prefs.stepDp) { prefs.stepDp = it })
        col.add(slider("Chạm đúp phím cách", "ms", 150, 600, prefs.doubleTapMs) { prefs.doubleTapMs = it })

        col.add(header("Sao lưu / khôi phục"))
        col.add(TextView(this).apply {
            text = "Một file zip gồm bố cục, ký tự vuốt xuống, gõ tắt, tuỳ chọn, dữ liệu đã học và clipboard đã ghim."
            textSize = 13f
        })
        col.add(Button(this).apply {
            text = "Sao lưu ra file…"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"; putExtra(Intent.EXTRA_TITLE, "telehex-backup.zip")
                }, REQ_BACKUP)
            }
        })
        col.add(Button(this).apply {
            text = "Khôi phục từ file…"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, REQ_RESTORE)
            }
        })

        col.add(header("Tương thích"))
        col.add(TextView(this).apply {
            text = "Các app xử lý composing không ổn (Zalo, Messenger…) dùng chế độ dự phòng: chữ được ghi thẳng thay vì gạch chân. Chọn app nếu thấy gõ bị lặp hoặc mất chữ."
            textSize = 13f
        })
        val fallbackSummary = TextView(this).apply { setPadding(0, dp(6), 0, dp(6)) }
        fun refreshFallback() {
            val names = AppPicker.labels(this, prefs.fallbackPackages)
            fallbackSummary.text = if (names.isEmpty()) "Chưa chọn app nào" else names.joinToString(", ")
        }
        refreshFallback()
        col.add(fallbackSummary)
        val picker = AppPicker(this, "Chế độ dự phòng") {
            prefs.fallbackPackages = it
            refreshFallback()
        }
        fallbackPicker = picker
        col.add(Button(this).apply {
            text = "Chọn app dùng chế độ dự phòng…"
            setOnClickListener { picker.show(prefs.fallbackPackages) }
        })
        picker.restoreState(savedInstanceState)
    }

    private var fallbackPicker: AppPicker? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        fallbackPicker?.saveState(outState)
    }

    override fun onDestroy() {
        fallbackPicker?.dismiss()
        super.onDestroy()
    }

    private var packStatusView: TextView? = null
    private var predictionStore: com.telehex.keyboard.data.PredictionStore? = null

    private fun packStatusText(store: com.telehex.keyboard.data.PredictionStore): String = when {
        store.hasPack() -> "Gói ngôn ngữ: đã nạp từ file."
        store.hasBase() -> "Gói ngôn ngữ: dùng bản đóng gói sẵn."
        else -> "Gói ngôn ngữ: chưa có (chỉ dùng từ điển cá nhân). Dựng bằng tools/build_ngram.py rồi nạp ở đây."
    }

    @Deprecated("Dùng API cũ để không thêm dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == REQ_BACKUP) {
            val r = com.telehex.keyboard.data.Backup.export(this, data?.data ?: return)
            android.widget.Toast.makeText(this, if (r.isSuccess) "Đã sao lưu" else "Lỗi: ${r.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (resultCode == RESULT_OK && requestCode == REQ_RESTORE) {
            val r = com.telehex.keyboard.data.Backup.import(this, data?.data ?: return)
            android.widget.Toast.makeText(this, if (r.isSuccess) "Đã khôi phục, mở lại Cài đặt để thấy thay đổi" else "Lỗi: ${r.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
            if (r.isSuccess) recreate()
            return
        }
        if (requestCode == REQ_PACK && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val store = predictionStore ?: return
            val r = store.importPack(uri)
            android.widget.Toast.makeText(this, if (r.isSuccess) "Đã nạp gói ngôn ngữ" else "Không nạp được: ${r.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
            packStatusView?.text = packStatusText(store)
        }
    }

    private fun slider(label: String, unit: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tv = TextView(this).apply { text = "$label: $value $unit" }
        box.addView(tv)
        box.addView(SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { tv.text = "$label: ${p + min} $unit" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) { onChange((sb?.progress ?: 0) + min) }
            })
        })
        return box
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

    companion object { private const val REQ_PACK = 11; private const val REQ_BACKUP = 12; private const val REQ_RESTORE = 13 }
}
