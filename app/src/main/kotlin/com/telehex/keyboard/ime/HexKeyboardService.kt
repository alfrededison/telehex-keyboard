package com.telehex.keyboard.ime

import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import com.telehex.keyboard.data.Prefs
import com.telehex.keyboard.layout.KeyCode
import com.telehex.keyboard.settings.SettingsActivity
import com.telehex.telex.TelexEngine
import kotlin.math.ceil

class HexKeyboardService : InputMethodService(), KeyboardView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var engine: TelexEngine
    private lateinit var root: LinearLayout
    private lateinit var candidateBar: CandidateBar
    private lateinit var keyboardView: KeyboardView
    private var emojiPanel: EmojiPanel? = null

    private var sink: TextSink = ComposingSink()
    private var mode = Mode.ABC
    private var symbolPage = 1
    private var telexEnabled = true
    private var enterLabel = "↵"
    private var candStart = -1
    private var candEnd = -1

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.registerListener(this)
        engine = TelexEngine(prefs.telexConfig())
    }

    override fun onDestroy() {
        prefs.unregisterListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        engine.config = prefs.telexConfig()
        if (::keyboardView.isInitialized) applyTheme()
        refreshKeys()
    }

    // ---- View ----

    override fun onCreateInputView(): View {
        candidateBar = CandidateBar(this).apply { onPick = { tone -> engine.setTone(tone); updateComposing() } }
        keyboardView = KeyboardView(this).apply { listener = this@HexKeyboardService }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(candidateBar)
            addView(keyboardView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            setOnApplyWindowInsetsListener { v, insets ->
                val bottom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                    insets.getInsets(WindowInsets.Type.systemBars()).bottom
                else @Suppress("DEPRECATION") insets.systemWindowInsetBottom
                v.setPadding(0, 0, 0, bottom + dp(prefs.extraBottomDp))
                insets
            }
        }
        applyTheme()
        refreshKeys()
        return root
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun applyTheme() {
        val t = Theme.resolve(this, prefs.themeMode)
        keyboardView.theme = t
        keyboardView.hapticEnabled = prefs.haptic
        keyboardView.soundEnabled = prefs.sound
        candidateBar.theme = t
        root.setBackgroundColor(t.background)
        emojiPanel = null // dựng lại theo theme khi mở lần sau
    }

    /** Thay lưới phím bằng một bảng (emoji, dialpad...) có cùng chiều cao; null = về lưới phím. */
    private fun showPanel(panel: View?) {
        val index = root.indexOfChild(keyboardView).takeIf { it >= 0 } ?: (root.childCount - 1)
        if (root.childCount > 1) root.removeViewAt(index)
        val h = keyboardView.gridHeightPx.takeIf { it > 0 } ?: LinearLayout.LayoutParams.WRAP_CONTENT
        if (panel == null) {
            mode = if (mode == Mode.EMOJI) Mode.ABC else mode
            root.addView(keyboardView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            refreshKeys()
        } else {
            root.addView(panel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h))
        }
    }

    private fun openSettings() {
        commitSyllable()
        startActivity(
            Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun openEmoji() {
        val panel = emojiPanel ?: EmojiPanel(this, Theme.resolve(this, prefs.themeMode)).also { p ->
            p.listener = object : EmojiPanel.Listener {
                override fun onEmoji(emoji: String) {
                    commitSyllable(emoji)
                    prefs.recentEmoji = listOf(emoji) + prefs.recentEmoji.filter { it != emoji }
                    p.recent = prefs.recentEmoji
                }
                override fun onAbc() { showPanel(null) }
                override fun onBackspace() { deleteBackward() }
            }
            emojiPanel = p
        }
        mode = Mode.EMOJI
        panel.recent = prefs.recentEmoji
        panel.open()
        showPanel(panel)
    }

    private fun refreshKeys() {
        if (!::keyboardView.isInitialized) return
        keyboardView.keys = KeyPage.build(prefs.layout, mode, symbolPage, engine.config, prefs::hintOf, enterLabel)
    }

    // ---- Lifecycle của ô nhập ----

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        engine.reset()
        candStart = -1; candEnd = -1
        val pkg = attribute?.packageName ?: ""
        sink = if (pkg in prefs.fallbackPackages) CommitSink() else ComposingSink()
        val inputType = attribute?.inputType ?: 0
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isPassword = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) || (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        telexEnabled = !isPassword
        mode = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> Mode.SYMBOLS
            else -> Mode.ABC
        }
        symbolPage = 1
        enterLabel = enterLabelFor(attribute)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyTheme()
        if (root.indexOfChild(keyboardView) < 0) showPanel(null)
        refreshKeys()
        updateCandidates()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        currentInputConnection?.let { sink.detach(it) }
        engine.reset()
        if (::candidateBar.isInitialized) candidateBar.clear()
    }

    private fun enterLabelFor(info: EditorInfo?): String {
        info ?: return "↵"
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return "↵"
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> "Tìm"
            EditorInfo.IME_ACTION_SEND -> "Gửi"
            EditorInfo.IME_ACTION_GO -> "Đi"
            EditorInfo.IME_ACTION_NEXT -> "Tiếp"
            EditorInfo.IME_ACTION_DONE -> "Xong"
            else -> "↵"
        }
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        candStart = candidatesStart; candEnd = candidatesEnd
        if (engine.isEmpty) return
        val ic = currentInputConnection ?: return
        if (!sink.matchesCursor(ic, engine.text(), newSelStart, newSelEnd, candidatesStart, candidatesEnd)) {
            // Người dùng di chuyển con trỏ: huỷ âm tiết đang soạn, giữ nguyên chữ.
            sink.detach(ic)
            engine.reset()
            updateCandidates()
        }
    }

    // ---- Soạn thảo ----

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        if (engine.isEmpty) sink.clearComposing(ic) else sink.setComposing(ic, engine.text())
        updateCandidates()
    }

    private fun updateCandidates() {
        if (!::candidateBar.isInitialized) return
        candidateBar.show(engine.candidates())
    }

    /** Chốt âm tiết đang soạn rồi chèn thêm [extra]. */
    private fun commitSyllable(extra: String = "") {
        val ic = currentInputConnection ?: return
        val text = engine.text() + extra
        engine.reset()
        if (text.isNotEmpty()) sink.commit(ic, text) else sink.detach(ic)
        updateCandidates()
    }

    private fun typeLetter(ch: Char, upper: Boolean) {
        if (!telexEnabled) {
            currentInputConnection?.commitText((if (upper) ch.uppercaseChar() else ch).toString(), 1)
            return
        }
        engine.press(ch, upper)
        updateComposing()
    }

    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
        if (engine.backspace()) { updateComposing(); return }
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) { ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(16, 0) ?: return
        if (before.isEmpty()) return
        ic.deleteSurroundingText(lastGraphemeLength(before), 0)
    }

    /** Xoá theo grapheme để emoji nhiều code point (❤️) xoá trong một lần. */
    private fun lastGraphemeLength(text: CharSequence): Int {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text.toString())
        val end = text.length
        val start = it.preceding(end)
        return if (start == BreakIterator.DONE) 1 else end - start
    }

    private fun deleteWords(n: Int) {
        val ic = currentInputConnection ?: return
        var left = n.coerceAtLeast(1)
        ic.beginBatchEdit()
        if (!engine.isEmpty) {
            engine.reset()
            sink.clearComposing(ic)
            left--
        }
        repeat(left) {
            val before = ic.getTextBeforeCursor(64, 0) ?: return@repeat
            var i = before.length
            while (i > 0 && before[i - 1].isWhitespace()) i--
            while (i > 0 && !before[i - 1].isWhitespace()) i--
            val count = before.length - i
            if (count > 0) ic.deleteSurroundingText(count, 0)
        }
        ic.endBatchEdit()
        updateCandidates()
    }

    private fun pressEnter() {
        commitSyllable()
        if (!sendDefaultEditorAction(true)) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    // ---- Phím ----

    override fun onKeyDown(key: Key) {}
    override fun onKeyMove(key: Key, dx: Float, dy: Float, dt: Long) {}
    override fun onKeyCancel(key: Key) {}

    override fun onKeyUp(key: Key, gesture: Gesture, dx: Float, dy: Float, dt: Long) {
        val code = key.code
        when {
            code == KeyCode.SPACE -> when (gesture) {
                Gesture.SWIPE_LEFT -> {
                    val keyW = keyboardView.dp(50f)
                    val dist = -dx
                    if (dist <= keyW * 1.2f) deleteBackward()
                    else deleteWords(ceil(((dist - keyW * 1.2f) / keyW).toDouble()).toInt())
                }
                Gesture.TAP, Gesture.LONG_PRESS -> commitSyllable(" ")
                else -> {}
            }
            code == KeyCode.ENTER -> pressEnter()
            code == KeyCode.NUMBERS -> {
                mode = if (mode == Mode.SYMBOLS) Mode.ABC else Mode.SYMBOLS
                symbolPage = 1
                refreshKeys()
            }
            code == KeyCode.PAGE -> { symbolPage = if (symbolPage == 2) 1 else 2; refreshKeys() }
            code == KeyCode.EMOJI -> openEmoji()
            code == KeyCode.SETTINGS -> openSettings()
            code == KeyCode.BACKSPACE -> deleteBackward()
            code == "," || code == "." ->
                commitSyllable(if (gesture == Gesture.SWIPE_DOWN && key.hint.isNotEmpty()) key.hint else code)
            key.isLetter && mode == Mode.ABC -> when (gesture) {
                Gesture.SWIPE_DOWN -> if (key.hint.isNotEmpty()) commitSyllable(key.hint) else typeLetter(code[0], false)
                Gesture.SWIPE_UP -> typeLetter(code[0], true)
                else -> typeLetter(code[0], false)
            }
            else -> commitSyllable(code) // ký tự ở lớp ký tự
        }
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE
    }

    @Suppress("unused")
    private fun ic(): InputConnection? = currentInputConnection
}
