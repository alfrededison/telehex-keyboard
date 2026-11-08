package com.telehex.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.telehex.keyboard.R
import com.telehex.telex.TelexEngine

/**
 * Thanh trên bàn phím (~52dp): gợi ý cuộn ngang (biến thể dấu, hoàn thành, thanh chỉnh sửa) và nút ⌫
 * cố định ở góc phải. Giữ ⌫ để xoá liên tục.
 */
@SuppressLint("ClickableViewAccessibility") // ⌫ vẫn có setOnClickListener cho trình đọc màn hình
class CandidateBar(context: Context) : LinearLayout(context) {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val p = dp(6)
        setPadding(p, 0, p, 0)
    }
    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    private val backspaceBg = GradientDrawable().apply { cornerRadius = dp(19).toFloat(); setColor(Color.TRANSPARENT) }
    private val backspace = ImageView(context).apply {
        setImageResource(R.drawable.ic_edit_backspace)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = "Xoá"
        background = backspaceBg
    }
    private val more = TextView(context).apply {
        text = "…"
        textSize = 24f
        gravity = Gravity.CENTER
        contentDescription = "Xem thêm gợi ý"
        visibility = GONE
        setOnClickListener { onMore?.invoke() }
    }

    var onMore: (() -> Unit)? = null
        set(value) { field = value; more.visibility = if (value == null) GONE else VISIBLE }

    var theme: Theme = Theme.LIGHT
        set(value) { field = value; applyColors() }

    var onPick: ((Int) -> Unit)? = null

    /** Nút ⌫ ở góc phải: gọi một lần khi chạm xuống, rồi lặp lại khi giữ. */
    var onBackspace: (() -> Unit)? = null
    var hapticEnabled: Boolean = true
    var soundEnabled: Boolean = false
    private val audio by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    /** Đang giữ ⌫; [stopBackspace] gọi từ trong [onBackspace] cũng chặn được lần lặp kế tiếp. */
    private var repeating = false
    private val backspaceRepeat = object : Runnable {
        override fun run() {
            if (!repeating) return
            fireBackspace()
            if (repeating) postDelayed(this, REPEAT_MS)
        }
    }

    init {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        addView(more, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(backspace, LinearLayout.LayoutParams(dp(52), dp(38)).apply { marginEnd = dp(6) })
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        backspace.setOnClickListener { onBackspace?.invoke() } // trình đọc màn hình (performClick)
        backspace.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    backspaceBg.setColor(theme.pressedOverlay)
                    repeating = true
                    fireBackspace()
                    if (repeating) postDelayed(backspaceRepeat, REPEAT_DELAY_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopBackspace()
            }
            true
        }
        applyColors()
    }

    private fun fireBackspace() {
        if (hapticEnabled) backspace.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (soundEnabled) audio.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE, 0.5f)
        onBackspace?.invoke()
    }

    /** Dừng xoá liên tục dù ngón tay còn giữ ⌫. */
    fun stopBackspace() {
        repeating = false
        removeCallbacks(backspaceRepeat)
        backspaceBg.setColor(Color.TRANSPARENT)
    }

    override fun onDetachedFromWindow() {
        stopBackspace()
        super.onDetachedFromWindow()
    }

    private fun applyColors() {
        setBackgroundColor(theme.barBg)
        backspace.imageTintList = ColorStateList.valueOf(theme.chipFg)
        more.setTextColor(theme.chipFg)
    }

    private fun scrollToStart() = scroll.scrollTo(0, 0)

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    fun show(
        candidates: List<TelexEngine.Candidate>,
        completions: List<String> = emptyList(),
        emoji: List<String> = emptyList(),
        onPickText: (String) -> Unit = {},
        onPickEmoji: (String) -> Unit = {},
    ) {
        clear()
        for (c in completions) chip(c, selected = false) { onPickText(c) }
        for (e in emoji) chip(e, selected = false) { onPickEmoji(e) }
        for (c in candidates) {
            val tv = TextView(context).apply {
                text = c.text
                textSize = 17f
                gravity = Gravity.CENTER
                val px = dp(12)
                setPadding(px, 0, px, 0)
                minWidth = dp(44)
                setTextColor(if (c.selected) theme.chipActiveFg else theme.chipFg)
                background = GradientDrawable().apply {
                    cornerRadius = dp(19).toFloat()
                    setColor(if (c.selected) theme.chipActiveBg else theme.chipBg)
                }
                setOnClickListener { onPick?.invoke(c.tone) }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
            lp.marginEnd = dp(6)
            row.addView(tv, lp)
        }
        scrollToStart()
    }

    fun clear() { row.removeAllViews(); onMore = null }

    /** Gợi ý âm tiết tiếp theo sau khi chạm cách; giữ một ô để xoá khỏi từ điển cá nhân. */
    fun showPredictions(next: List<String>, emoji: List<String>, onPick: (String) -> Unit, onPickEmoji: (String) -> Unit, onForget: (String) -> Unit) {
        clear()
        for (n in next) chip(n, selected = false, onLongClick = { onForget(n) }) { onPick(n) }
        for (e in emoji) chip(e, selected = false) { onPickEmoji(e) }
        scrollToStart()
    }

    /** Chữ đang gõ (ô đầu, giữ nguyên) + các gợi ý sửa; chạm gợi ý để thay. */
    fun showSuggestions(typed: String, fixes: List<String>, onTyped: () -> Unit = {}, onPick: (String) -> Unit) {
        clear()
        chip(typed, selected = true) { onTyped() }
        for (f in fixes) chip(f, selected = false) { onPick(f) }
        scrollToStart()
    }

    private fun chip(text: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
        val tv = TextView(context).apply {
            this.text = text
            textSize = 17f
            gravity = Gravity.CENTER
            val px = dp(12)
            setPadding(px, 0, px, 0)
            minWidth = dp(44)
            setTextColor(if (selected) theme.chipActiveFg else theme.chipFg)
            background = GradientDrawable().apply {
                cornerRadius = dp(19).toFloat()
                setColor(if (selected) theme.chipActiveBg else theme.chipBg)
            }
            setOnClickListener { onClick() }
            if (onLongClick != null) setOnLongClickListener { onLongClick(); true }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
        lp.marginEnd = dp(6)
        row.addView(tv, lp)
    }

    /** Một nút trên thanh chỉnh sửa: nhãn, gợi ý cử chỉ (dòng nhỏ), hành động. */
    class EditAction(val label: String, val gestureHint: String, val enabled: Boolean = true, val action: () -> Unit)

    /** Thanh chỉnh sửa thay cho gợi ý khi có vùng chọn hoặc đang ở chế độ con trỏ. */
    fun showEditBar(actions: List<EditAction>) {
        clear()
        for (a in actions) {
            val tv = TextView(context).apply {
                text = if (a.gestureHint.isEmpty()) a.label else "${a.label}\n${a.gestureHint}"
                textSize = 12f
                setLineSpacing(0f, 0.95f)
                gravity = Gravity.CENTER
                val px = dp(8)
                setPadding(px, 0, px, 0)
                minWidth = dp(52)
                alpha = if (a.enabled) 1f else 0.4f
                setTextColor(theme.chipFg)
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(theme.chipBg) }
                isEnabled = a.enabled
                setOnClickListener { a.action() }
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
            lp.marginEnd = dp(6)
            row.addView(tv, lp)
        }
        scrollToStart()
    }

    private companion object {
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_MS = 50L
    }
}
