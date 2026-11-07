package com.telehex.keyboard.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
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
import com.telehex.keyboard.R
import com.telehex.keyboard.data.Prefs
import com.telehex.keyboard.ime.Accent
import com.telehex.keyboard.layout.Profile

/** Màn hình Cài đặt (View thuần, không thêm dependency). */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var accentLabel: TextView
    private lateinit var swatchRow: LinearLayout
    private lateinit var profileButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        SettingsUi.applyTheme(this, prefs)
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
        }
        content = col
        savedInstanceState?.getStringArrayList(STATE_EXPANDED)?.let { expanded.addAll(it) }
        // Có id để giữ vị trí cuộn khi recreate() sau khi đổi giao diện
        setContentView(ScrollView(this).apply { id = R.id.settings_scroll; addView(col) })

        col.add(TextView(this).apply {
            text = "TeleHex không xin quyền INTERNET. Không gửi dữ liệu gõ đi đâu; mọi xử lý nằm trên máy."
            textSize = 14f
        })
        // Luôn hiện, để thử ngay tuỳ chọn vừa đổi
        col.add(EditText(this).apply {
            hint = "Thử gõ ở đây"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        })

        // Chưa dùng TeleHex thì mở sẵn hướng dẫn bật
        if (savedInstanceState == null && !isCurrentIme()) expanded.add("Bật bàn phím")
        var sec = section("Bật bàn phím")
        sec.addButton("1. Bật TeleHex trong Cài đặt hệ thống") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        sec.addButton("2. Chọn TeleHex làm bàn phím hiện tại") { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }

        sec = section("Bàn phím")
        // Đổi nhanh profile (bố cục đủ ba lớp chữ / vuốt xuống / 123); nhãn cập nhật ở onResume
        profileButton = sec.addButton("") {
            showProfilePicker(context, Profile.BUILT_IN + prefs.userProfiles, prefs.activeProfile.name) { p ->
                prefs.saveProfiles(prefs.userProfiles, p)
                showActiveProfile()
            }
        }
        sec.addButton("Chỉnh sửa phím") { startActivity(Intent(context, LayoutEditorActivity::class.java)) }
        sec.add(switch("Đổi chỗ phím ngay trên bàn phím: giữ một phím ký tự đến khi nó nổi lên (~0,8 giây), kéo thả lên phím khác (tự lưu vào profile đang dùng)", prefs.dragToRearrange) { prefs.dragToRearrange = it })
        sec.add(label("Chế độ một tay (lưới thu còn ~80%, lục giác vẫn đều)"))
        sec.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            isSaveEnabled = false
            val opts = listOf("Tắt", "Dồn trái", "Dồn phải")
            val buttons = opts.map { label -> RadioButton(context).apply { text = label; id = View.generateViewId() }.also { addView(it) } }
            check(buttons[prefs.oneHanded.coerceIn(0, 2)].id)
            setOnCheckedChangeListener { _, id -> prefs.oneHanded = buttons.indexOfFirst { it.id == id } }
        })
        sec.add(switch("Rung khi chạm", prefs.haptic) { prefs.haptic = it })
        sec.add(switch("Âm thanh khi chạm", prefs.sound) { prefs.sound = it })
        sec.add(label("Giao diện"))
        sec.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            isSaveEnabled = false
            val modes = listOf("system" to "Theo hệ thống", "light" to "Sáng", "dark" to "Tối")
            val buttons = modes.map { (_, label) -> RadioButton(context).apply { text = label; id = View.generateViewId() }.also { addView(it) } }
            check(buttons[modes.indexOfFirst { it.first == prefs.themeMode }.coerceAtLeast(0)].id)
            setOnCheckedChangeListener { _, id ->
                prefs.themeMode = modes[buttons.indexOfFirst { it.id == id }].first
                recreate() // Cài đặt đổi sáng/tối theo bàn phím
            }
        })
        accentLabel = label("")
        sec.add(accentLabel)
        swatchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(8))
        }
        sec.add(swatchRow)
        refreshSwatches()
        val heightLabel = label("Khoảng trống dưới bàn phím: ${prefs.extraBottomDp} dp")
        sec.add(heightLabel)
        sec.add(SeekBar(this).apply {
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

        sec = section("Emoji và clipboard")
        sec.add(label("Tông da mặc định (cho emoji nhận tông da)"))
        sec.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            isSaveEnabled = false
            val tones = listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿")
            val buttons = tones.map { label -> RadioButton(context).apply { text = label; id = View.generateViewId() }.also { addView(it) } }
            check(buttons[prefs.skinTone.coerceIn(0, 5)].id)
            setOnCheckedChangeListener { _, id -> prefs.skinTone = buttons.indexOfFirst { it.id == id } }
        })
        sec.add(switch("Lưu clipboard history (giữ phím ⚙ để mở; mục thường hết hạn sau 1 giờ, mục ghim giữ lâu)", prefs.clipboardHistory) { prefs.clipboardHistory = it })

        sec = section("Telex")
        sec.add(label("Kiểu đặt dấu"))
        sec.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            isSaveEnabled = false
            val old = RadioButton(context).apply { text = "Cũ (hòa, thúy)"; id = View.generateViewId() }
            val new = RadioButton(context).apply { text = "Mới (hoà, thuý)"; id = View.generateViewId() }
            addView(old); addView(new)
            check(if (prefs.newToneStyle) new.id else old.id)
            setOnCheckedChangeListener { _, id -> prefs.newToneStyle = id == new.id }
        })
        sec.add(switch("L cũng là dấu huyền (lal → là)", prefs.lAsGrave) { prefs.lAsGrave = it })
        sec.add(switch("Tự viết hoa chữ đầu câu", prefs.autoCapitalize) { prefs.autoCapitalize = it })
        sec.add(switch("Tự khôi phục từ tiếng Anh (windows không thành ưindows)", prefs.englishFallback) { prefs.englishFallback = it })
        sec.add(label("Chế độ EN (tắt Telex)"))
        sec.add(TextView(this).apply {
            text = "Vuốt xuống phím cách để bật/tắt; phím cách ghi \"EN\" khi đang bật. Các app dưới đây và ô email mở sẵn ở EN."
            textSize = 13f
        })
        val englishSummary = TextView(this).apply { setPadding(0, dp(6), 0, dp(6)) }
        fun refreshEnglish() {
            val names = AppPicker.labels(this, prefs.englishPackages)
            englishSummary.text = if (names.isEmpty()) "Chưa chọn app nào" else names.joinToString(", ")
        }
        refreshEnglish()
        sec.add(englishSummary)
        val enPicker = AppPicker(this, "Tự động bật EN", "english") {
            prefs.englishPackages = it
            refreshEnglish()
        }
        englishPicker = enPicker
        sec.addButton("Chọn app tự bật EN…") { enPicker.show(prefs.englishPackages) }
        enPicker.restoreState(savedInstanceState)

        sec = section("Gõ tắt")
        sec.add(TextView(this).apply {
            text = "Mỗi dòng một mục dạng  viết tắt = nội dung . Kích hoạt khi chạm cách. Ví dụ:\nko = không\ndc = được\n@@ = ten@email.com"
            textSize = 13f
        })
        sec.add(EditText(this).apply {
            setText(prefs.shortcuts.entries.sortedBy { it.key }.joinToString("\n") { "${it.key} = ${it.value}" })
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            // Đầu dòng tự viết hoa sẽ lưu "Ko" thay vì "ko"
            privateImeOptions = com.telehex.keyboard.ime.HexKeyboardService.NO_AUTO_CAP
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

        sec = section("Gợi ý (không tự sửa)")
        sec.add(TextView(this).apply {
            text = "Bàn phím không bao giờ tự đổi chữ đã gõ. Gợi ý âm tiết tiếp theo học từ chính bạn, hoàn toàn trên máy. Giữ một gợi ý để xoá nó khỏi từ điển cá nhân."
            textSize = 13f
        })
        sec.add(switch("Gợi ý âm tiết tiếp theo", prefs.predictions) { prefs.predictions = it })
        sec.add(switch("Slide to type: lướt qua các phím chữ để gợi ý từ", prefs.glideTyping) { prefs.glideTyping = it })
        sec.add(TextView(this).apply {
            text = "Lướt qua ít nhất 3 phím chữ khác nhau, dùng chữ không dấu (t → i → e → n → g) hoặc chuỗi Telex; nhả tay rồi chạm từ gợi ý để chèn. Mỗi lần lướt một âm tiết. Chỉ qua 1–2 phím thì vẫn là cử chỉ thường, gồm vuốt lên/xuống để gõ hoa/ký tự phụ. Dùng ở chế độ tiếng Việt."
            textSize = 13f
        })
        sec.add(switch("Chế độ ẩn danh (không học)", prefs.incognito) { prefs.incognito = it })
        val store = com.telehex.keyboard.data.PredictionStore(this)
        val packStatus = TextView(this).apply { text = packStatusText(store) }
        sec.add(packStatus)
        sec.addButton("Nạp gói ngôn ngữ (.ngram)…") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }, REQ_PACK)
        }
        sec.addButton("Xoá gói ngôn ngữ đã nạp") { store.removePack(); packStatus.text = packStatusText(store) }
        sec.addButton("Xoá dữ liệu đã học") { store.clearPersonal(); android.widget.Toast.makeText(context, "Đã xoá từ điển cá nhân", android.widget.Toast.LENGTH_SHORT).show() }
        packStatusView = packStatus
        predictionStore = store

        sec = section("Chống bấm nhầm")
        sec.add(switch("Học độ lệch chạm theo từng phím", prefs.learnTouchOffsets) { prefs.learnTouchOffsets = it })
        sec.add(switch("Vùng chạm động: nới vùng phím hợp lệ tiếp theo (một dạng tự sửa ngầm)", prefs.dynamicHitZones) { prefs.dynamicHitZones = it })
        sec.addButton("Xoá dữ liệu độ lệch đã học") { prefs.touchOffsets = emptyMap() }

        sec = section("Cử chỉ")
        sec.add(TextView(this).apply {
            text = "Phím cách: vuốt trái = xoá (ngắn: 1 ký tự, dài: theo từ, có bôi đen xem trước); giữ rồi kéo = di con trỏ; chạm đúp rồi giữ-kéo = chọn; vuốt phải = dán; vuốt lên = đổi hoa/thường; vuốt xuống = bật/tắt EN (tắt Telex); khi có vùng chọn: vuốt lên = chép, vuốt trái = cắt. Hai ngón vuốt trái/phải = undo/redo, hai ngón giữ = chọn hết."
            textSize = 13f
        })
        sec.add(slider("Ngưỡng vuốt", "dp", 12, 60, prefs.swipeThresholdDp) { prefs.swipeThresholdDp = it })
        sec.add(slider("Giữ phím cách trước khi kéo (con trỏ)", "ms", 150, 800, prefs.holdMs) { prefs.holdMs = it })
        sec.add(slider("Giữ lâu (long-press)", "ms", 250, 1000, prefs.longPressMs) { prefs.longPressMs = it })
        sec.add(slider("Mỗi bước xoá từ / di con trỏ", "dp", 25, 100, prefs.stepDp) { prefs.stepDp = it })
        sec.add(slider("Chạm đúp phím cách", "ms", 150, 600, prefs.doubleTapMs) { prefs.doubleTapMs = it })

        sec = section("Sao lưu / khôi phục")
        sec.add(TextView(this).apply {
            text = "Một file zip gồm bố cục, ký tự vuốt xuống, gõ tắt, tuỳ chọn, dữ liệu đã học và clipboard đã ghim."
            textSize = 13f
        })
        sec.addButton("Sao lưu ra file…") {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"; putExtra(Intent.EXTRA_TITLE, "telehex-backup.zip")
            }, REQ_BACKUP)
        }
        sec.addButton("Khôi phục từ file…") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, REQ_RESTORE)
        }

        sec = section("Tương thích")
        sec.add(TextView(this).apply {
            text = "Các app xử lý composing không ổn (Zalo, Messenger…) dùng chế độ dự phòng: chữ được ghi thẳng thay vì gạch chân. Chọn app nếu thấy gõ bị lặp hoặc mất chữ."
            textSize = 13f
        })
        val fallbackSummary = TextView(this).apply { setPadding(0, dp(6), 0, dp(6)) }
        fun refreshFallback() {
            val names = AppPicker.labels(this, prefs.fallbackPackages)
            fallbackSummary.text = if (names.isEmpty()) "Chưa chọn app nào" else names.joinToString(", ")
        }
        refreshFallback()
        sec.add(fallbackSummary)
        val picker = AppPicker(this, "Chế độ dự phòng", "fallback") {
            prefs.fallbackPackages = it
            refreshFallback()
        }
        fallbackPicker = picker
        sec.addButton("Chọn app dùng chế độ dự phòng…") { picker.show(prefs.fallbackPackages) }
        picker.restoreState(savedInstanceState)

        col.add(TextView(this).apply {
            text = versionText()
            textSize = 12f
            gravity = Gravity.CENTER
            setTextIsSelectable(true) // để chép khi báo lỗi
            alpha = 0.6f
            setPadding(0, dp(28), 0, 0)
        })
    }

    /** "TeleHex 0.1.1-dev.3 (build 10100003)": bản dev có hậu tố -dev.N do tools/version.sh đặt. */
    private fun versionText(): String {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        return "TeleHex ${info.versionName} (build $code)"
    }

    private var fallbackPicker: AppPicker? = null
    private var englishPicker: AppPicker? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_EXPANDED, ArrayList(expanded))
        fallbackPicker?.saveState(outState)
        englishPicker?.saveState(outState)
    }

    override fun onDestroy() {
        fallbackPicker?.dismiss()
        englishPicker?.dismiss()
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
        val tv = label("$label: $value $unit")
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

    private fun refreshSwatches() {
        val current = Accent.of(prefs.accent)
        accentLabel.text = "Màu nhấn: ${current.label}"
        swatchRow.removeAllViews()
        for (a in Accent.entries) swatchRow.addView(swatch(a, a == current), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
    }

    /** Ô tròn chọn màu nhấn; ô đang chọn có viền và dấu ✓. "Không màu" là vòng tròn có gạch chéo. */
    private fun swatch(a: Accent, selected: Boolean) = TextView(this).apply {
        gravity = Gravity.CENTER
        textSize = 18f
        setTextColor(Color.WHITE)
        contentDescription = a.label
        val ring = currentTextColor(android.R.attr.textColorPrimary)
        if (a == Accent.NONE) {
            background = NoAccentSwatch(if (selected) ring else a.swatch, dp(if (selected) 3 else 2).toFloat(), a.swatch)
        } else {
            text = if (selected) "✓" else ""
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(a.swatch)
                if (selected) setStroke(dp(3), ring)
            }
        }
        setOnClickListener {
            if (selected) return@setOnClickListener
            prefs.accent = a.id // chỉ đổi màu bàn phím; không recreate() nên bàn phím đang mở không bị đóng
            refreshSwatches()
        }
    }

    /** Vòng tròn có gạch chéo cho "Không màu". */
    private class NoAccentSwatch(private val ringColor: Int, private val ringWidth: Float, private val slashColor: Int) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val r = minOf(b.width(), b.height()) / 2f - ringWidth / 2
            val cx = b.exactCenterX(); val cy = b.exactCenterY()
            paint.color = ringColor; paint.strokeWidth = ringWidth
            canvas.drawCircle(cx, cy, r, paint)
            val d = r * 0.7071f // gạch từ góc dưới trái lên góc trên phải, chạm vòng tròn
            paint.color = slashColor; paint.strokeWidth = ringWidth.coerceAtMost(r / 6)
            canvas.drawLine(cx - d, cy + d, cx + d, cy - d, paint)
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
        @Deprecated("Bắt buộc override") override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private fun currentTextColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        try { return ta.getColor(0, Color.GRAY) } finally { ta.recycle() }
    }

    /** Nhãn cho nhóm tuỳ chọn, cách phần phía trên để không dính vào nút. */
    private fun label(text: String) = TextView(this).apply {
        this.text = text
        setPadding(0, dp(12), 0, dp(2))
    }

    private lateinit var content: LinearLayout
    /** Tiêu đề các section đang mở; giữ qua recreate() (đổi giao diện, khôi phục sao lưu). */
    private val expanded = LinkedHashSet<String>()

    /**
     * Section thu gọn được: chạm tiêu đề để mở / đóng. Trả về thân section để thêm tuỳ chọn vào.
     * Màn dài nên mặc định đóng hết, trừ những section đã mở trước khi recreate().
     */
    private fun section(title: String): LinearLayout {
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
            visibility = if (title in expanded) View.VISIBLE else View.GONE
        }
        val chevron = TextView(this).apply { textSize = 16f; alpha = 0.6f }
        fun mark() { chevron.text = if (body.visibility == View.VISIBLE) "▴" else "▾" }
        mark()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            isClickable = true
            isFocusable = true
            background = selectableBackground()
            addView(TextView(context).apply {
                text = title
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(currentTextColor(android.R.attr.textColorPrimary))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(chevron)
            setOnClickListener {
                val open = body.visibility != View.VISIBLE
                body.visibility = if (open) View.VISIBLE else View.GONE
                if (open) expanded.add(title) else expanded.remove(title)
                mark()
            }
        }
        content.add(divider())
        content.add(head)
        content.add(body)
        return body
    }

    override fun onResume() {
        super.onResume()
        showActiveProfile() // có thể vừa đổi ở màn Chỉnh sửa phím
    }

    private fun showActiveProfile() { profileButton.text = "Profile: ${prefs.activeProfile.name}  ▾" }

    private fun isCurrentIme(): Boolean =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true

    private fun divider() = View(this).apply {
        setBackgroundColor(currentTextColor(android.R.attr.textColorPrimary))
        alpha = 0.12f
        minimumHeight = 1
    }

    private fun selectableBackground(): android.graphics.drawable.Drawable? {
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        try { return ta.getDrawable(0) } finally { ta.recycle() }
    }

    @Suppress("DEPRECATION")
    private fun switch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = label
        isChecked = checked
        setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    companion object {
        private const val REQ_PACK = 11; private const val REQ_BACKUP = 12; private const val REQ_RESTORE = 13
        private const val STATE_EXPANDED = "expanded"
    }
}
