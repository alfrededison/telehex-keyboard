package com.telehex.keyboard.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BulletSpan
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.telehex.keyboard.R
import com.telehex.keyboard.data.Prefs
import com.telehex.keyboard.data.PredictionStore
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
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        content = col
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

        var sec = section("enable_keyboard", "Bật bàn phím", "Thiết lập TeleHex trên thiết bị", R.drawable.ic_settings_enable)
        sec.addButton("1. Bật TeleHex trong Cài đặt hệ thống") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        sec.addButton("2. Chọn TeleHex làm bàn phím hiện tại") { (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }

        // Lần đầu mở app: màn chào hướng dẫn bật bàn phím và chọn profile, nằm trên màn Cài đặt
        if (savedInstanceState == null) WelcomeActivity.showIfNeeded(this, prefs)

        sec = section("keyboard", "Bàn phím", "Bố cục, giao diện và âm thanh", R.drawable.ic_settings_keyboard)
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
        sec.add(label("Tiếng phím dùng âm lượng Hệ thống. Nếu không nghe tiếng, hãy tăng âm lượng này trong Cài đặt của máy.").apply {
            textSize = 13f
            setTextColor(currentTextColor(android.R.attr.textColorSecondary))
            setPadding(0, dp(4), 0, dp(2))
        })
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

        sec = section("emoji_clipboard", "Emoji và clipboard", "Tông da, giới tính và lịch sử sao chép", R.drawable.ic_settings_emoji)
        sec.add(label("Tông da mặc định (cho emoji nhận tông da)"))
        sec.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            isSaveEnabled = false
            val tones = listOf("👍", "👍🏻", "👍🏼", "👍🏽", "👍🏾", "👍🏿")
            val buttons = tones.map { label -> RadioButton(context).apply { text = label; id = View.generateViewId() }.also { addView(it) } }
            check(buttons[prefs.skinTone.coerceIn(0, 5)].id)
            setOnCheckedChangeListener { _, id -> prefs.skinTone = buttons.indexOfFirst { it.id == id } }
        })
        sec.add(label("Giới tính mặc định (cho emoji trung tính có biến thể)"))
        sec.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            isSaveEnabled = false
            val buttons = listOf("Nguyên bản", "Nữ", "Nam").map { label ->
                RadioButton(context).apply { text = label; id = View.generateViewId() }.also { addView(it) }
            }
            check(buttons[prefs.emojiGender].id)
            setOnCheckedChangeListener { _, id -> prefs.emojiGender = buttons.indexOfFirst { it.id == id } }
        })
        sec.add(switch("Lưu clipboard history (vuốt xuống phím ⚙ để mở; mục thường hết hạn sau 1 giờ, mục ghim giữ lâu)", prefs.clipboardHistory) { prefs.clipboardHistory = it })

        sec = section("telex", "Telex", "Kiểu gõ tiếng Việt và chế độ EN", R.drawable.ic_settings_telex)
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

        sec = section("shortcuts", "Gõ tắt", "Cụm từ thường dùng, gõ nhanh hơn", R.drawable.ic_settings_shortcuts)
        sec.add(TextView(this).apply {
            text = "Nhập viết tắt và nội dung, bấm Tiếp để chuyển ô hoặc thêm dòng. Tự lưu, bỏ khoảng trắng thừa ở hai đầu. Khi gõ, chạm cách để bung nội dung."
            textSize = 13f
        })
        shortcutsEditor = ShortcutsEditor(this, prefs, savedInstanceState)
        sec.add(shortcutsEditor!!)

        sec = section("predictions", "Gợi ý (không tự sửa)", "Gợi ý từ và slide to type", R.drawable.ic_settings_predictions)
        sec.add(TextView(this).apply {
            text = "Bàn phím không bao giờ tự đổi chữ đã gõ. Gợi ý tiếng Việt học từ chính bạn; giữ một gợi ý để xoá khỏi từ điển cá nhân. Tiếng Anh có sẵn từ điển; nạp thêm gói EN để gợi ý theo ngữ cảnh và từ tiếp theo, không học dữ liệu gõ. Mọi gợi ý đều chạy trên máy."
            textSize = 13f
        })
        sec.add(switch("Gợi ý từ/âm tiết tiếp theo (VI và EN)", prefs.predictions) { prefs.predictions = it })
        sec.add(switch("Slide to type: lướt qua các phím chữ để gợi ý từ", prefs.glideTyping) { prefs.glideTyping = it })
        sec.add(TextView(this).apply {
            text = "Lướt qua ít nhất 3 phím chữ khác nhau, dùng chữ không dấu (t → i → e → n → g) hoặc chuỗi Telex; nhả tay rồi chạm từ gợi ý để chèn. Mỗi lần lướt một âm tiết. Chỉ qua 1–2 phím thì vẫn là cử chỉ thường, gồm vuốt lên/xuống để gõ hoa/ký tự phụ. Dùng ở chế độ tiếng Việt."
            textSize = 13f
        })
        sec.add(switch("Chế độ ẩn danh (không học)", prefs.incognito) { prefs.incognito = it })
        val store = PredictionStore(this)
        val packStatus = TextView(this).apply { text = packStatusText(store) }
        sec.add(packStatus)
        sec.addButton("Nạp gói tiếng Việt (.ngram)…") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }, REQ_PACK)
        }
        sec.addButton("Xoá gói tiếng Việt đã nạp") { store.removePack(); packStatus.text = packStatusText(store) }
        val englishPackStatus = TextView(this).apply { text = packStatusText(store, PredictionStore.Language.EN) }
        sec.add(englishPackStatus)
        sec.addButton("Nạp gói tiếng Anh (.ngram)…") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }, REQ_EN_PACK)
        }
        sec.addButton("Xoá gói tiếng Anh đã nạp") {
            store.removePack(PredictionStore.Language.EN)
            englishPackStatus.text = packStatusText(store, PredictionStore.Language.EN)
        }
        sec.addView(View(this).apply {
            setBackgroundColor((currentTextColor(android.R.attr.textColorSecondary) and 0x00FFFFFF) or (0x33 shl 24))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(16)
            bottomMargin = dp(8)
        })
        sec.add(label("Dữ liệu cá nhân"))
        sec.add(TextView(this).apply {
            text = "Chỉ xoá các từ và thói quen gõ tiếng Việt đã học. Tiếng Anh không lưu dữ liệu học; các gói ngôn ngữ đã nạp được giữ lại."
            textSize = 13f
            setTextColor(currentTextColor(android.R.attr.textColorSecondary))
        })
        sec.addButton("Xoá dữ liệu tiếng Việt đã học") { store.clearPersonal(); android.widget.Toast.makeText(context, "Đã xoá từ điển cá nhân tiếng Việt", android.widget.Toast.LENGTH_SHORT).show() }
        packStatusView = packStatus
        englishPackStatusView = englishPackStatus
        predictionStore = store

        sec = section("touch_accuracy", "Chống bấm nhầm", "Độ chính xác khi chạm phím", R.drawable.ic_settings_touch)
        sec.add(switch("Học độ lệch chạm theo từng phím", prefs.learnTouchOffsets) { prefs.learnTouchOffsets = it })
        sec.add(switch("Vùng chạm động: nới vùng phím hợp lệ tiếp theo (một dạng tự sửa ngầm)", prefs.dynamicHitZones) { prefs.dynamicHitZones = it })
        sec.addButton("Xoá dữ liệu độ lệch đã học") { prefs.touchOffsets = emptyMap() }

        sec = section("gestures", "Cử chỉ", "Vuốt, giữ và chạm đúp", R.drawable.ic_settings_gestures)
        listOf(
            "Phím chữ" to listOf(
                "Vuốt lên: gõ chữ hoa nếu nhãn đang thường, gõ chữ thường nếu nhãn đang hoa.",
                "Vuốt xuống: nhập ký tự phụ hiện bên dưới phím (số, emoji, ký hiệu).",
            ),
            "Phím cách" to listOf(
                "Vuốt trái ngắn: xoá 1 ký tự.",
                "Vuốt trái dài: xoá theo từ, có bôi đen xem trước.",
                "Giữ rồi kéo: di con trỏ.",
                "Chạm đúp rồi giữ-kéo: chọn văn bản.",
                "Vuốt phải: dán.",
                "Vuốt lên: đổi hoa/thường.",
                "Vuốt xuống: bật/tắt EN (tắt Telex).",
            ),
            "Phím cách khi có vùng chọn" to listOf(
                "Vuốt lên: chép.",
                "Vuốt trái: cắt.",
            ),
            "Hai ngón" to listOf(
                "Vuốt trái/phải: undo/redo.",
                "Giữ: chọn hết.",
            ),
        ).forEach { (title, instructions) ->
            sec.add(label(title))
            instructions.forEach { instruction ->
                sec.add(TextView(this).apply {
                    text = SpannableString(instruction).apply {
                        setSpan(BulletSpan(dp(8)), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    textSize = 13f
                    setPadding(0, dp(3), 0, dp(3))
                })
            }
        }
        sec.add(slider("Ngưỡng vuốt", "dp", 12, 60, prefs.swipeThresholdDp) { prefs.swipeThresholdDp = it })
        sec.add(slider("Giữ phím cách trước khi kéo (con trỏ)", "ms", 150, 800, prefs.holdMs) { prefs.holdMs = it })
        sec.add(slider("Giữ lâu (long-press)", "ms", 250, 1000, prefs.longPressMs) { prefs.longPressMs = it })
        sec.add(slider("Mỗi bước xoá từ / di con trỏ", "dp", 25, 100, prefs.stepDp) { prefs.stepDp = it })
        sec.add(slider("Chạm đúp phím cách", "ms", 150, 600, prefs.doubleTapMs) { prefs.doubleTapMs = it })

        sec = section("backup_restore", "Sao lưu / khôi phục", "Lưu và chuyển dữ liệu của bạn", R.drawable.ic_settings_backup)
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

        sec = section("compatibility", "Tương thích", "Tuỳ chỉnh cách gõ theo ứng dụng", R.drawable.ic_settings_apps)
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
    private var shortcutsEditor: ShortcutsEditor? = null

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        fallbackPicker?.saveState(outState)
        englishPicker?.saveState(outState)
        shortcutsEditor?.saveState(outState)
    }

    override fun onDestroy() {
        fallbackPicker?.dismiss()
        englishPicker?.dismiss()
        super.onDestroy()
    }

    private var packStatusView: TextView? = null
    private var englishPackStatusView: TextView? = null
    private var predictionStore: PredictionStore? = null

    private fun packStatusText(store: PredictionStore, language: PredictionStore.Language = PredictionStore.Language.VI): String {
        val name = if (language == PredictionStore.Language.VI) "tiếng Việt" else "tiếng Anh"
        return when {
            store.hasPack(language) -> "Gói $name: đã nạp từ file."
            store.hasBase(language) -> "Gói $name: dùng bản đóng gói sẵn."
            language == PredictionStore.Language.EN -> "Gói tiếng Anh: chưa có (hoàn thành từ theo độ phổ biến). Nạp en.ngram để gợi ý theo ngữ cảnh và từ tiếp theo."
            else -> "Gói tiếng Việt: chưa có (chỉ dùng từ điển cá nhân và cụm từ có sẵn). Dựng bằng tools/build_ngram.py rồi nạp ở đây."
        }
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
        if ((requestCode == REQ_PACK || requestCode == REQ_EN_PACK) && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val store = predictionStore ?: return
            val language = if (requestCode == REQ_EN_PACK) PredictionStore.Language.EN else PredictionStore.Language.VI
            val r = store.importPack(uri, language)
            android.widget.Toast.makeText(this, if (r.isSuccess) "Đã nạp gói ${if (language == PredictionStore.Language.EN) "tiếng Anh" else "tiếng Việt"}" else "Không nạp được: ${r.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
            if (language == PredictionStore.Language.EN) englishPackStatusView?.text = packStatusText(store, language)
            else packStatusView?.text = packStatusText(store)
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

    /**
     * Section thu gọn được: chạm tiêu đề để mở / đóng. Trả về thân section để thêm tuỳ chọn vào.
     * Mặc định mở; lưu trạng thái thu gọn theo mã cố định để giữ khi mở lại hoặc đổi tên tiêu đề.
     */
    private fun section(sectionId: String, title: String, summary: String, iconRes: Int): LinearLayout {
        val primary = currentTextColor(android.R.attr.textColorPrimary)
        val secondary = currentTextColor(android.R.attr.textColorSecondary)
        val accent = SettingsUi.accentColor(this)
        fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
            cornerRadius = dp(radius).toFloat()
            setColor(color)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded((primary and 0x00FFFFFF) or (0x0A shl 24), 24)
            clipToOutline = true
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(20))
            visibility = if (sectionId in prefs.collapsedSettingsSections) View.GONE else View.VISIBLE
        }
        val chevron = ImageView(this).apply {
            setImageResource(R.drawable.ic_settings_chevron)
            imageTintList = ColorStateList.valueOf(secondary)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(88)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            isFocusable = true
            contentDescription = "$title. $summary"
            background = RippleDrawable(
                ColorStateList.valueOf((accent and 0x00FFFFFF) or (0x20 shl 24)),
                null, rounded(Color.WHITE, 24),
            )
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(accent)
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = rounded((accent and 0x00FFFFFF) or (0x18 shl 24), 14)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                add(TextView(context).apply {
                    text = title
                    textSize = 17f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    setTextColor(primary)
                })
                add(TextView(context).apply {
                    text = summary
                    textSize = 13f
                    setTextColor(secondary)
                }, dp(4))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
                marginEnd = dp(8)
            })
            addView(chevron, LinearLayout.LayoutParams(dp(24), dp(24)))
        }
        ViewCompat.setAccessibilityHeading(head, true)
        fun mark() {
            val open = body.visibility == View.VISIBLE
            chevron.rotation = if (open) 180f else 0f
            ViewCompat.setStateDescription(head, if (open) "Đã mở rộng" else "Đã thu gọn")
        }
        mark()
        head.setOnClickListener {
            val open = body.visibility != View.VISIBLE
            body.visibility = if (open) View.VISIBLE else View.GONE
            prefs.collapsedSettingsSections = if (open) {
                prefs.collapsedSettingsSections - sectionId
            } else {
                prefs.collapsedSettingsSections + sectionId
            }
            mark()
        }
        card.add(head)
        card.add(body)
        content.add(card, dp(12))
        return body
    }

    override fun onResume() {
        super.onResume()
        showActiveProfile() // có thể vừa đổi ở màn Chỉnh sửa phím
    }

    private fun showActiveProfile() { profileButton.text = "Profile: ${prefs.activeProfile.name}  ▾" }

    @Suppress("DEPRECATION")
    private fun switch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        text = label
        isChecked = checked
        setPadding(0, dp(10), 0, dp(10))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }

    companion object {
        private const val REQ_PACK = 11; private const val REQ_BACKUP = 12; private const val REQ_RESTORE = 13; private const val REQ_EN_PACK = 14
    }
}
