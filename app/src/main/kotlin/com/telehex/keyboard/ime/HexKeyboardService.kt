package com.telehex.keyboard.ime

import android.content.ClipboardManager
import android.content.Context
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

class HexKeyboardService : InputMethodService(), KeyboardView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var engine: TelexEngine
    private lateinit var root: LinearLayout
    private lateinit var candidateBar: CandidateBar
    private lateinit var keyboardView: KeyboardView
    private var emojiPanel: EmojiPanel? = null
    private var dialpad: DialpadView? = null

    private val history = EditHistory()
    private lateinit var spaceGestures: SpaceGestures

    private var sink: TextSink = ComposingSink()
    private var mode = Mode.ABC
    private var symbolPage = 1
    private var telexEnabled = true
    private var enterLabel = "↵"
    private var candStart = -1
    private var candEnd = -1
    private var selStart = 0
    private var selEnd = 0
    private var cursorMode = false
    private var numberOnly = false
    private var numberSigned = true
    private var numberDecimal = true
    private var autoDial = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.registerListener(this)
        engine = TelexEngine(prefs.telexConfig())
        spaceGestures = SpaceGestures(spaceHost)
    }

    override fun onDestroy() {
        prefs.unregisterListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Prefs.KEY_RECENT_EMOJI || key == Prefs.KEY_PRESETS) return
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
    private fun dpF(v: Int): Float = v * resources.displayMetrics.density

    private fun applyTheme() {
        val t = Theme.resolve(this, prefs.themeMode)
        keyboardView.theme = t
        keyboardView.hapticEnabled = prefs.haptic
        keyboardView.soundEnabled = prefs.sound
        keyboardView.setThresholds(dpF(prefs.swipeThresholdDp), prefs.longPressMs.toLong())
        candidateBar.theme = t
        root.setBackgroundColor(t.background)
        emojiPanel = null // dựng lại theo theme khi mở lần sau
        dialpad = null
    }

    private fun refreshKeys() {
        if (!::keyboardView.isInitialized) return
        keyboardView.keys = KeyPage.build(prefs.layout, mode, symbolPage, engine.config, prefs::hintOf, enterLabel)
    }

    /** Thay lưới phím bằng một bảng (emoji, dialpad) có cùng chiều cao; null = về lưới phím. */
    private fun showPanel(panel: View?) {
        while (root.childCount > 1) root.removeViewAt(1)
        if (panel == null) {
            root.addView(keyboardView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            refreshKeys()
        } else {
            root.addView(panel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, panelHeight()))
        }
    }

    /** Chiều cao lưới phím, tính được cả khi lưới chưa từng được đo (mở thẳng dialpad). */
    private fun panelHeight(): Int {
        val w = root.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        return keyboardView.expectedHeight(w)
    }

    private fun openAbc() {
        mode = Mode.ABC
        showPanel(null)
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
                override fun onAbc() { openAbc() }
                override fun onBackspace() { deleteBackward() }
            }
            emojiPanel = p
        }
        mode = Mode.EMOJI
        panel.recent = prefs.recentEmoji
        panel.open()
        showPanel(panel)
    }

    private fun openDial() {
        val t = Theme.resolve(this, prefs.themeMode)
        val h = panelHeight()
        val panel = dialpad?.takeIf { it.heightPx == h } ?: DialpadView(this, t, h).also { d ->
            d.listener = this
            d.onBackspaceRepeat = { deleteBackward() }
            dialpad = d
        }
        panel.hapticEnabled = prefs.haptic
        panel.soundEnabled = prefs.sound
        panel.enterLabel = enterLabel
        panel.allowMinus = !numberOnly || numberSigned
        panel.allowDot = !numberOnly || numberDecimal
        mode = Mode.DIAL
        showPanel(panel)
    }

    // ---- Lifecycle của ô nhập ----

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        engine.reset()
        history.clear()
        candStart = -1; candEnd = -1
        selStart = attribute?.initialSelStart ?: 0
        selEnd = attribute?.initialSelEnd ?: 0
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
        numberOnly = cls == InputType.TYPE_CLASS_NUMBER
        numberSigned = inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0
        numberDecimal = inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0
        autoDial = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE || cls == InputType.TYPE_CLASS_DATETIME
        mode = if (autoDial) Mode.DIAL else Mode.ABC
        symbolPage = 1
        enterLabel = enterLabelFor(attribute)
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        applyTheme()
        if (mode == Mode.DIAL) openDial() else { mode = Mode.ABC; showPanel(null) }
        updateBar()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        spaceGestures.cancel()
        currentInputConnection?.let { sink.detach(it) }
        engine.reset()
        history.clear()
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
        selStart = newSelStart; selEnd = newSelEnd
        if (!engine.isEmpty) {
            val ic = currentInputConnection
            // Update có thể là của lần commit trước đến trễ (candidatesStart = -1 dù ta đã gửi composing mới),
            // nên khi không khớp vẫn đối chiếu văn bản thật trước con trỏ trước khi huỷ.
            if (ic != null && !sink.matchesCursor(ic, engine.text(), newSelStart, newSelEnd, candidatesStart, candidatesEnd) &&
                !textBeforeCursorMatches(ic, engine.text())) {
                // Người dùng di chuyển con trỏ: huỷ âm tiết đang soạn, giữ nguyên chữ.
                sink.detach(ic)
                engine.reset()
                history.clear()
            }
        }
        updateBar()
    }

    private fun hasSelection(): Boolean = selStart != selEnd

    private fun textBeforeCursorMatches(ic: InputConnection, composing: String): Boolean {
        if (selStart != selEnd) return false
        val before = ic.getTextBeforeCursor(composing.length, 0) ?: return false
        return before.toString() == composing
    }

    // ---- Soạn thảo ----

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        if (engine.isEmpty) sink.clearComposing(ic) else sink.setComposing(ic, engine.text())
        updateBar()
    }

    /** Thanh trên bàn phím: biến thể dấu khi đang soạn; thanh chỉnh sửa khi có vùng chọn / chế độ con trỏ. */
    private fun updateBar() {
        if (!::candidateBar.isInitialized) return
        if ((hasSelection() || cursorMode) && engine.isEmpty) {
            val sel = hasSelection()
            candidateBar.showEditBar(
                listOf(
                    CandidateBar.EditAction("↶ Undo", "2 ngón ←", history.canUndo) { undo() },
                    CandidateBar.EditAction("↷ Redo", "2 ngón →", history.canRedo) { redo() },
                    CandidateBar.EditAction("Cắt", "chọn + vuốt ←", sel) { cut() },
                    CandidateBar.EditAction("Chép", "chọn + vuốt ↑", sel) { copy() },
                    CandidateBar.EditAction("Dán", "vuốt →") { paste() },
                    CandidateBar.EditAction("Chọn hết", "2 ngón giữ") { selectAll() },
                )
            )
        } else {
            candidateBar.show(engine.candidates())
        }
    }

    /** Chốt âm tiết đang soạn rồi chèn thêm [extra]. */
    private fun commitSyllable(extra: String = "") {
        val ic = currentInputConnection ?: return
        val text = engine.text() + extra
        engine.reset()
        if (text.isNotEmpty()) {
            if (hasSelection()) history.record(EditHistory.Op.Delete(ic.getSelectedText(0)?.toString() ?: ""))
            sink.commit(ic, text)
            history.record(EditHistory.Op.Insert(text))
        } else sink.detach(ic)
        updateBar()
    }

    private fun typeLetter(ch: Char, upper: Boolean) {
        if (!telexEnabled) {
            commitSyllable((if (upper) ch.uppercaseChar() else ch).toString())
            return
        }
        engine.press(ch, upper)
        updateComposing()
    }

    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
        if (engine.backspace()) { updateComposing(); return }
        if (hasSelection()) { deleteSelection(); return }
        val before = ic.getTextBeforeCursor(16, 0)
        if (before.isNullOrEmpty()) {
            // Ô nhập không trả văn bản (terminal, chip, một số WebView): gửi phím DEL.
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }
        val n = lastGraphemeLength(before)
        ic.deleteSurroundingText(n, 0)
        history.record(EditHistory.Op.Delete(before.subSequence(before.length - n, before.length).toString()))
    }

    private fun deleteSelection() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)?.toString() ?: ""
        ic.commitText("", 1)
        history.record(EditHistory.Op.Delete(selected))
        updateBar()
    }

    /** Xoá theo grapheme để emoji nhiều code point (❤️) xoá trong một lần. */
    private fun lastGraphemeLength(text: CharSequence): Int {
        val it = BreakIterator.getCharacterInstance()
        it.setText(text.toString())
        val end = text.length
        val start = it.preceding(end)
        return if (start == BreakIterator.DONE) 1 else end - start
    }

    private fun pressEnter() {
        commitSyllable()
        if (!sendDefaultEditorAction(true)) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    // ---- Chỉnh sửa: undo/redo, cut/copy/paste ----

    private fun undo() {
        val ic = currentInputConnection ?: return
        commitSyllable()
        if (history.undo(ic)) keyboardView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        updateBar()
    }

    private fun redo() {
        val ic = currentInputConnection ?: return
        commitSyllable()
        history.redo(ic)
        updateBar()
    }

    private fun copy() {
        val ic = currentInputConnection ?: return
        if (!hasSelection()) return
        if (!ic.performContextMenuAction(android.R.id.copy)) {
            val text = ic.getSelectedText(0) ?: return
            clipboard().setPrimaryClip(android.content.ClipData.newPlainText("text", text))
        }
    }

    private fun cut() {
        val ic = currentInputConnection ?: return
        if (!hasSelection()) return
        val selected = ic.getSelectedText(0)?.toString() ?: ""
        if (!ic.performContextMenuAction(android.R.id.cut)) {
            clipboard().setPrimaryClip(android.content.ClipData.newPlainText("text", selected))
            ic.commitText("", 1)
        }
        history.record(EditHistory.Op.Delete(selected))
        updateBar()
    }

    private fun paste() {
        val ic = currentInputConnection ?: return
        commitSyllable()
        val clip = clipboard().primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        if (text.isEmpty()) return
        if (hasSelection()) history.record(EditHistory.Op.Delete(ic.getSelectedText(0)?.toString() ?: ""))
        ic.commitText(text, 1)
        history.record(EditHistory.Op.Insert(text))
        updateBar()
    }

    private fun selectAll() {
        val ic = currentInputConnection ?: return
        commitSyllable()
        ic.performContextMenuAction(android.R.id.selectAll)
    }

    private fun clipboard() = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // ---- Cử chỉ phím cách ----

    private val spaceHost = object : SpaceGestures.Host {
        override val holdMs: Long get() = prefs.holdMs.toLong()
        override val stepPx: Float get() = dpF(prefs.stepDp)
        override val swipePx: Float get() = dpF(prefs.swipeThresholdDp)
        override val doubleTapMs: Long get() = prefs.doubleTapMs.toLong()
        override val leftEdgeDx: Float get() = if (mode == Mode.DIAL) -Float.MAX_VALUE else keyboardView.spaceLeftEdgeDx()

        override fun hasSelection(): Boolean = this@HexKeyboardService.hasSelection()
        override fun cursorPosition(): Int = selEnd
        override fun textBeforeCursor(n: Int): CharSequence = currentInputConnection?.getTextBeforeCursor(n, 0) ?: ""
        override fun setSelection(start: Int, end: Int) { currentInputConnection?.setSelection(start, end) }
        override fun detachComposing() {
            val ic = currentInputConnection ?: return
            if (!engine.isEmpty) { history.record(EditHistory.Op.Insert(engine.text())); sink.detach(ic); engine.reset(); updateBar() }
        }
        override fun deleteBackward() = this@HexKeyboardService.deleteBackward()
        override fun deleteSelection() = this@HexKeyboardService.deleteSelection()
        override fun commitSpace() = commitSyllable(" ")
        override fun undoLastSpace() {
            val ic = currentInputConnection ?: return
            if (ic.getTextBeforeCursor(1, 0)?.toString() == " ") ic.deleteSurroundingText(1, 0)
        }
        override fun moveCursor(dxChars: Int, dyLines: Int) {
            repeat(kotlin.math.abs(dxChars)) { sendDownUpKeyEvents(if (dxChars < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT) }
            repeat(kotlin.math.abs(dyLines)) { sendDownUpKeyEvents(if (dyLines < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN) }
        }
        override fun copy() = this@HexKeyboardService.copy()
        override fun cut() = this@HexKeyboardService.cut()
        override fun paste() = this@HexKeyboardService.paste()
        override fun setCursorMode(on: Boolean) {
            cursorMode = on
            keyboardView.dimmed = on
            updateBar()
        }
        override fun haptic() { if (prefs.haptic) keyboardView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) }
        override fun onSelectionGestureEnd() = updateBar()
    }

    // ---- Phím ----

    override fun onKeyDown(key: Key) {
        if (key.isSpace) spaceGestures.onDown(android.os.SystemClock.uptimeMillis())
    }

    override fun onKeyMove(key: Key, dx: Float, dy: Float, dt: Long) {
        if (key.isSpace) spaceGestures.onMove(dx, dy)
    }

    override fun onKeyCancel(key: Key) {
        if (key.isSpace) spaceGestures.cancel()
    }

    override fun onMultiTouchStart() { spaceGestures.cancel() }

    override fun onTwoFinger(gesture: Gesture) {
        when (gesture) {
            Gesture.SWIPE_LEFT -> undo()
            Gesture.SWIPE_RIGHT -> redo()
            Gesture.LONG_PRESS -> selectAll()
            else -> {}
        }
    }

    override fun onKeyUp(key: Key, gesture: Gesture, dx: Float, dy: Float, dt: Long) {
        val code = key.code
        when {
            code == KeyCode.SPACE -> spaceGestures.onUp(gesture, android.os.SystemClock.uptimeMillis())
            code == KeyCode.ENTER -> pressEnter()
            code == KeyCode.NUMBERS -> {
                if (gesture == Gesture.LONG_PRESS) { commitSyllable(); openDial(); return }
                mode = if (mode == Mode.SYMBOLS) Mode.ABC else Mode.SYMBOLS
                symbolPage = 1
                refreshKeys()
            }
            code == KeyCode.PAGE -> { symbolPage = if (symbolPage == 2) 1 else 2; refreshKeys() }
            code == KeyCode.EMOJI -> openEmoji()
            code == KeyCode.SETTINGS -> openSettings()
            code == KeyCode.ABC -> openAbc()
            code == KeyCode.BACKSPACE -> deleteBackward()
            code == "," || code == "." ->
                commitSyllable(if (gesture == Gesture.SWIPE_DOWN && key.hint.isNotEmpty()) key.hint else code)
            mode == Mode.DIAL -> commitSyllable(if (gesture == Gesture.SWIPE_DOWN && key.hint.isNotEmpty()) key.hint else code)
            key.isLetter && mode == Mode.ABC -> when (gesture) {
                Gesture.SWIPE_DOWN -> if (key.hint.isNotEmpty()) commitSyllable(key.hint) // hint rỗng: không chèn gì
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
