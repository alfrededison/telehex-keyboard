package com.telehex.keyboard.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.telehex.keyboard.R
import com.telehex.keyboard.data.Prefs
import com.telehex.keyboard.ime.KeyPage
import com.telehex.keyboard.ime.KeyboardView
import com.telehex.keyboard.ime.Mode
import com.telehex.keyboard.ime.Theme
import com.telehex.keyboard.layout.Profile
import com.telehex.telex.TelexConfig

/** Trạng thái TeleHex trong hệ thống: đã bật trong danh sách bàn phím, đang là bàn phím hiện tại. */
internal object ImeStatus {
    fun enabled(context: Context): Boolean =
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .enabledInputMethodList.any { it.packageName == context.packageName }

    fun selected(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("${context.packageName}/") == true
}

/**
 * Màn chào lần đầu mở app: bước 1 hướng dẫn bật và chọn TeleHex, bước 2 chọn profile (tổ ong hay truyền thống) có
 * hình xem trước và ô gõ thử. Xong (hoặc bỏ qua) thì không hiện lại; đổi profile sau trong Cài đặt.
 */
class WelcomeActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var page: LinearLayout
    private var step = 1

    // Bước 1: cập nhật dấu ✓ khi quay lại từ Cài đặt hệ thống hay đóng hộp chọn bàn phím
    private var enableStatus: TextView? = null
    private var selectStatus: TextView? = null
    private var nextButton: Button? = null

    // Bước 2
    private val cards = ArrayList<Pair<Profile, LinearLayout>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        SettingsUi.applyTheme(this, prefs)
        super.onCreate(savedInstanceState)
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply { id = R.id.welcome_scroll; addView(page) })
        step = savedInstanceState?.getInt(STATE_STEP) ?: 1
        show(step)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_STEP, step)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    // Hộp chọn bàn phím của hệ thống không làm activity pause: đóng hộp thì cửa sổ lấy lại focus
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshStatus()
    }

    private fun show(s: Int) {
        step = s
        page.removeAllViews()
        enableStatus = null; selectStatus = null; nextButton = null
        cards.clear()
        if (s == 1) showEnable() else showProfiles()
    }

    private fun showEnable() {
        page.add(title("Chào mừng đến với TeleHex"))
        page.add(body(
            "Bàn phím tiếng Việt gõ Telex, chạy hoàn toàn trên máy. TeleHex không xin quyền Internet nên không gửi " +
                "được gì bạn gõ ra ngoài."
        ), dp(8))

        page.add(heading("1. Bật TeleHex"), dp(24))
        page.add(body("Mở danh sách bàn phím của Android và bật TeleHex. Android sẽ cảnh báo chung cho mọi bàn phím rằng " +
            "bàn phím có thể thu thập văn bản bạn nhập."), dp(4))
        page.addButton("Mở Cài đặt bàn phím") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        enableStatus = status().also { page.add(it) }

        page.add(heading("2. Chọn TeleHex làm bàn phím"), dp(20))
        page.add(body("Chọn TeleHex trong hộp chọn bàn phím."), dp(4))
        page.addButton("Chọn bàn phím") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        selectStatus = status().also { page.add(it) }

        nextButton = page.addButton("Tiếp tục: chọn kiểu bàn phím") { show(2) }.also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(28)
        }
        page.add(textButton("Để sau") { show(2) })
        refreshStatus()
    }

    private fun refreshStatus() {
        val on = ImeStatus.enabled(this)
        val chosen = ImeStatus.selected(this)
        enableStatus?.let { it.text = if (on) "✓ Đã bật" else "Chưa bật"; it.setTextColor(if (on) accent() else secondary()) }
        selectStatus?.let { it.text = if (chosen) "✓ Đang dùng TeleHex" else "Chưa chọn"; it.setTextColor(if (chosen) accent() else secondary()) }
        nextButton?.let { it.isEnabled = on && chosen; it.alpha = if (it.isEnabled) 1f else 0.4f }
    }

    private fun showProfiles() {
        page.add(title("Chọn kiểu bàn phím"))
        page.add(body("Hai kiểu dùng chung Telex, cử chỉ và gợi ý. Chạm một kiểu rồi gõ thử ngay bên dưới."), dp(8))
        val theme = Theme.resolve(this, prefs.themeMode, prefs.accent)
        val active = prefs.activeProfile.name
        addCard(Profile.DEFAULT, "Tổ ong (Mặc định)",
            "Phím rộng hơn bàn phím 10 cột. Thứ tự chữ gần QWERTY; xoá và chữ hoa bằng cử chỉ vuốt.", theme)
        addCard(Profile.QWERTY, "Truyền thống",
            "Giống bàn phím quen thuộc: QWERTY đủ 10 cột, có phím ⇧ và ⌫.", theme)
        // Đang dùng profile riêng (khôi phục từ bản sao lưu…) thì chưa tô thẻ nào
        markSelected(cards.firstOrNull { it.first.name == active }?.first)

        page.add(EditText(this).apply {
            id = R.id.welcome_try // giữ chữ đã gõ khi xoay màn hình
            hint = "Gõ thử ở đây"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
        }, dp(16))
        page.add(body("Đổi lại lúc nào cũng được: Cài đặt › Bàn phím, hoặc nút Profile trong bảng sửa văn bản (vuốt xuống ⚙)."), dp(8))
        page.addButton("Xong") { done() }.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(20) }
    }

    private fun addCard(profile: Profile, name: String, description: String, theme: Theme) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
            isClickable = true
            isFocusable = true
            contentDescription = "$name. $description"
            setOnClickListener { choose(profile) }
        }
        card.add(TextView(this).apply {
            text = name
            setTextColor(primary())
            textSize = 17f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        card.add(body(description), dp(2))
        card.add(PreviewKeyboardView(this).apply {
            this.theme = theme
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            keys = KeyPage.build(profile.letters, Mode.ABC, 1, TelexConfig(), profile.swipe, profile.symbols, "↵")
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(theme.background) }
            clipToOutline = true
        }, dp(10))
        page.add(card, dp(14))
        cards += profile to card
    }

    /** Chọn là dùng ngay, để ô gõ thử hiện đúng bố cục vừa chọn. */
    private fun choose(profile: Profile) {
        if (prefs.activeProfile.name != profile.name) prefs.saveProfiles(prefs.userProfiles, profile)
        markSelected(profile)
    }

    private fun markSelected(profile: Profile?) {
        val accent = SettingsUi.accentColor(this)
        for ((p, card) in cards) {
            val on = p.name == profile?.name
            card.isSelected = on
            card.background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor((accent and 0x00FFFFFF) or ((if (on) 0x1C else 0x08) shl 24))
                setStroke(dp(if (on) 2 else 1), if (on) accent else (accent and 0x00FFFFFF) or (0x30 shl 24))
            }
        }
    }

    private fun done() {
        prefs.welcomeDone = true
        finish()
    }

    // Rời màn chào (Xong, back) là đã xem: lần sau mở app vào thẳng Cài đặt, ở đó vẫn có hai nút bật bàn phím
    override fun onPause() {
        super.onPause()
        if (isFinishing) prefs.welcomeDone = true
    }

    private fun textColor(attr: Int): Int {
        val a = obtainStyledAttributes(intArrayOf(attr))
        try { return a.getColor(0, 0) } finally { a.recycle() }
    }

    private fun primary() = textColor(android.R.attr.textColorPrimary)
    private fun secondary() = textColor(android.R.attr.textColorSecondary)
    private fun accent() = SettingsUi.accentColor(this)

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(primary())
        textSize = 26f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun heading(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(primary())
        textSize = 18f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(secondary())
        textSize = 15f
        setLineSpacing(0f, 1.15f)
    }

    private fun status() = TextView(this).apply {
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(4), dp(2), 0, 0)
    }

    private fun textButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setTextColor(SettingsUi.accentColor(context))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

    companion object {
        private const val STATE_STEP = "welcome_step"

        /**
         * Lần đầu mở app thì hiện màn chào. Người dùng bản cũ đã bật TeleHex thì coi như đã xem, không hiện.
         * @return true nếu đã mở màn chào.
         */
        fun showIfNeeded(activity: Activity, prefs: Prefs): Boolean {
            if (prefs.welcomeDone) return false
            if (ImeStatus.enabled(activity)) { prefs.welcomeDone = true; return false }
            activity.startActivity(Intent(activity, WelcomeActivity::class.java))
            return true
        }
    }
}

/** Bàn phím chỉ để xem trong thẻ chọn profile: không nhận chạm, chạm vào thẻ là chọn. */
private class PreviewKeyboardView(context: Context) : KeyboardView(context) {
    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = false
}
