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
import com.telehex.keyboard.data.ClipboardStore
import com.telehex.keyboard.data.PredictionStore
import com.telehex.keyboard.data.Prefs
import com.telehex.keyboard.layout.KeyCode
import com.telehex.keyboard.settings.SettingsActivity
import com.telehex.telex.Corrector
import com.telehex.telex.TelexDecomposer
import com.telehex.telex.TelexEngine

class HexKeyboardService : InputMethodService(), KeyboardView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var engine: TelexEngine
    private lateinit var root: LinearLayout
    private lateinit var toast: ActionToast
    private lateinit var candidateBar: CandidateBar
    private lateinit var keyboardView: KeyboardView
    private var emojiPanel: EmojiPanel? = null
    private var dialpad: DialpadView? = null
    private var editPanel: EditPanel? = null
    private lateinit var clipboardStore: ClipboardStore
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onClipChanged() }
    /** Chế độ tìm emoji: chữ gõ vào thanh tìm thay vì ô nhập. */
    private var emojiSearch = false

    private val history = EditHistory()
    private lateinit var spaceGestures: SpaceGestures
    private var corrector: Corrector? = null
    private lateinit var predictions: PredictionStore
    /** Các âm tiết vừa chốt trong ô nhập hiện tại (ngữ cảnh dự đoán), gần nhất ở cuối. */
    private val recent = ArrayDeque<String>()
    private var noLearning = false
    private var personalDirty = false
    private val savePersonal = Runnable { if (personalDirty) { personalDirty = false; predictions.savePersonal() } }
    private var touchOffsets: MutableMap<String, FloatArray> = HashMap()
    private var offsetsDirty = false
    private val saveOffsets = Runnable {
        if (offsetsDirty) { offsetsDirty = false; prefs.touchOffsets = touchOffsets.mapValues { floatArrayOf(it.value[0] / density(), it.value[1] / density()) } }
    }

    private fun density(): Float = resources.displayMetrics.density

    private fun rebuildCorrector() {
        corrector = Corrector(engine.config, prefs.layout.letterNeighbors())
    }

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
    /** Chữ đầu của âm tiết đang soạn được viết hoa chỉ vì đầu câu. */
    private var autoCappedFirst = false
    private var numberSigned = true
    private var numberDecimal = true
    private var autoDial = false

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.registerListener(this)
        engine = TelexEngine(prefs.telexConfig())
        spaceGestures = SpaceGestures(spaceHost)
        rebuildCorrector()
        predictions = PredictionStore(this)
        clipboardStore = ClipboardStore(this)
        clipboard().addPrimaryClipChangedListener(clipListener)
        touchOffsets = prefs.touchOffsets.mapValues { floatArrayOf(it.value[0] * density(), it.value[1] * density()) }.toMutableMap()
    }

    override fun onDestroy() {
        prefs.unregisterListener(this)
        clipboard().removePrimaryClipChangedListener(clipListener)
        savePersonal.run()
        super.onDestroy()
    }

    /** Lưu clipboard history; bỏ qua nội dung nhạy cảm và khi đang ở ô mật khẩu. */
    private fun onClipChanged() {
        if (!prefs.clipboardHistory || !telexEnabled) return
        val clip = clipboard().primaryClip ?: return
        if (clip.itemCount == 0) return
        val desc = clip.description
        if (Build.VERSION.SDK_INT >= 33 && desc.extras?.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE) == true) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
        clipboardStore.add(text)
    }

    private fun openEditPanel() {
        commitSyllable()
        val t = Theme.resolve(this, prefs.themeMode)
        val panel = editPanel ?: EditPanel(this, t).also { p ->
            p.listener = object : EditPanel.Listener {
                override fun onAction(action: String) = editAction(action)
                override fun onPasteItem(text: String) { pasteText(text) }
                override fun onPinItem(text: String) { clipboardStore.togglePin(text); p.showClipboard(clipboardStore.all()) }
                override fun onDeleteItem(text: String) { clipboardStore.remove(text); p.showClipboard(clipboardStore.all()) }
            }
            editPanel = p
        }
        panel.showClipboard(clipboardStore.all())
        mode = Mode.EDIT
        showPanel(panel)
    }

    private fun editAction(action: String) {
        when (action) {
            "abc" -> openAbc()
            "up" -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
            "down" -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
            "left" -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            "right" -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            "home" -> sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_HOME)
            "end" -> sendDownUpKeyEvents(KeyEvent.KEYCODE_MOVE_END)
            "selectWord" -> selectWordAtCursor()
            "selectAll" -> selectAll()
            "cut" -> cut()
            "copy" -> copy()
            "paste" -> paste()
            "undo" -> undo()
            "redo" -> redo()
            "backspace" -> deleteBackward()
            "voice" -> switchToVoiceIme()
        }
    }

    private fun selectWordAtCursor() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(32, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(32, 0)?.toString() ?: ""
        var i = before.length
        while (i > 0 && !before[i - 1].isWhitespace()) i--
        var j = 0
        while (j < after.length && !after[j].isWhitespace()) j++
        if (before.length - i + j == 0) return
        ic.setSelection(selStart - (before.length - i), selEnd + j)
    }

    /** Nhập giọng nói qua IME khác (ví dụ FUTO Voice Input chạy offline): chuyển sang IME có subtype "voice". */
    private fun switchToVoiceIme() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        for (ime in imm.enabledInputMethodList) {
            for (st in imm.getEnabledInputMethodSubtypeList(ime, true)) {
                if (st.mode == "voice") {
                    if (Build.VERSION.SDK_INT >= 28) switchInputMethod(ime.id, st)
                    else {
                        val token = window.window?.attributes?.token ?: return
                        @Suppress("DEPRECATION") imm.setInputMethodAndSubtype(token, ime.id, st)
                    }
                    return
                }
            }
        }
        android.widget.Toast.makeText(this, "Chưa có bàn phím giọng nói nào được bật (ví dụ FUTO Voice Input).", android.widget.Toast.LENGTH_LONG).show()
    }

    private fun pasteText(text: String) {
        val ic = currentInputConnection ?: return
        commitSyllable()
        if (hasSelection()) history.record(EditHistory.Op.Delete(ic.getSelectedText(0)?.toString() ?: ""))
        ic.commitText(text, 1)
        history.record(EditHistory.Op.Insert(text))
        flash("Đã dán")
        updateBar()
    }

    // ---- Tìm emoji ----

    private fun startEmojiSearch() {
        emojiSearch = true
        engine.reset()
        openAbc()
        updateBar()
    }

    private fun stopEmojiSearch() {
        emojiSearch = false
        engine.reset()
        updateBar()
    }

    private fun insertEmoji(emoji: String) {
        val e = EmojiData.get(this).withSkinTone(emoji, prefs.skinTone)
        commitSyllable(e)
        prefs.recentEmoji = listOf(emoji) + prefs.recentEmoji.filter { it != emoji }
        emojiPanel?.recent = prefs.recentEmoji
    }

    private fun incognito(): Boolean = noLearning || prefs.incognito || !telexEnabled

    /** Ngữ cảnh từ vài chục ký tự trước con trỏ, để gợi ý đúng ngay khi mở bàn phím. */
    private fun seedContext() {
        recent.clear()
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(80, 0)?.toString() ?: return
        val tail = before.substringAfterLast('\n').substringAfterLast('.').substringAfterLast('!').substringAfterLast('?')
        for (tok in tail.split(Regex("\\s+")).filter { it.isNotEmpty() && it.all { c -> c.isLetter() } }.takeLast(2)) recent.addLast(tok)
    }

    /** @param autoCapped chữ hoa đầu do bàn phím tự thêm (đầu câu): học như chữ thường, đừng coi là tên riêng. */
    private fun pushContext(raw: String, autoCapped: Boolean = false) {
        if (raw.isEmpty() || !raw.all { it.isLetter() }) return
        val syllable = if (autoCapped) raw.replaceFirstChar { it.lowercaseChar() } else raw
        if (!incognito()) {
            predictions.predictor.personal.learn(recent.toList(), syllable)
            personalDirty = true
            keyboardView.removeCallbacks(savePersonal); keyboardView.postDelayed(savePersonal, 5000)
        }
        recent.addLast(syllable)
        while (recent.size > 2) recent.removeFirst()
    }

    /** Đầu câu: chưa có gì trước con trỏ, hoặc sau dấu kết câu. */
    private fun atSentenceStart(): Boolean {
        val before = currentInputConnection?.getTextBeforeCursor(3, 0)?.toString() ?: return true
        val t = before.trimEnd()
        return t.isEmpty() || t.last() in ".!?\n"
    }

    private fun casedPrediction(s: String): String = when {
        atSentenceStart() || predictions.predictor.personal.usuallyCapitalized(s) ->
            s.replaceFirstChar { it.titlecase() }
        else -> s
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == Prefs.KEY_RECENT_EMOJI || key == Prefs.KEY_PRESETS) return
        if (key == Prefs.KEY_TOUCH_OFFSETS) {
            // Người dùng xoá dữ liệu độ lệch trong Cài đặt: bỏ bản trong bộ nhớ, đừng ghi đè lại
            if (prefs.touchOffsets.isEmpty()) { touchOffsets.clear(); offsetsDirty = false; if (::keyboardView.isInitialized) keyboardView.removeCallbacks(saveOffsets) }
            return
        }
        if (key == Prefs.KEY_MODELS_VERSION) {
            personalDirty = false
            predictions = PredictionStore(this)
            clipboardStore = ClipboardStore(this)
            return
        }
        engine.config = prefs.telexConfig()
        rebuildCorrector()
        if (key == Prefs.KEY_LEARN_OFFSETS && !prefs.learnTouchOffsets) touchOffsets.clear()
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
        toast = ActionToast(this)
        val frame = android.widget.FrameLayout(this).apply {
            addView(root)
            // Ngay dưới thanh gợi ý, giữa bề ngang: không che chữ đang soạn
            addView(toast, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL,
            ).apply { topMargin = dp(60) })
        }
        applyTheme()
        refreshKeys()
        return frame
    }

    /** Phản hồi trực quan cho hành động không tự thấy rõ trên bàn phím (hoàn tác, dán, chép…). */
    private fun flash(message: String) { if (::toast.isInitialized) toast.show(message) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dpF(v: Int): Float = v * resources.displayMetrics.density

    private fun applyTheme() {
        val t = Theme.resolve(this, prefs.themeMode)
        keyboardView.theme = t
        keyboardView.hapticEnabled = prefs.haptic
        keyboardView.soundEnabled = prefs.sound
        keyboardView.setThresholds(dpF(prefs.swipeThresholdDp), prefs.longPressMs.toLong())
        keyboardView.oneHanded = prefs.oneHanded
        keyboardView.touchOffsets = if (prefs.learnTouchOffsets) touchOffsets else null
        keyboardView.onTouchOffsetsChanged = {
            offsetsDirty = true
            keyboardView.removeCallbacks(saveOffsets); keyboardView.postDelayed(saveOffsets, 3000)
        }
        keyboardView.validNextLetter = if (prefs.dynamicHitZones) { ch -> mode != Mode.ABC || telexEnabled.not() || engine.copy().apply { press(ch) }.isSuspicious().not() } else null
        candidateBar.theme = t
        toast.theme = t
        root.setBackgroundColor(t.background)
        emojiPanel = null // dựng lại theo theme khi mở lần sau
        dialpad = null
        editPanel = null
    }

    private fun refreshKeys() {
        if (!::keyboardView.isInitialized) return
        keyboardView.keys = KeyPage.build(prefs.layout, mode, symbolPage, engine.config, prefs::hintOf, enterLabel)
        updateShift()
    }

    /** Nhãn chữ hoa/thường theo chữ sắp gõ. */
    private fun updateShift() {
        if (::keyboardView.isInitialized) keyboardView.shifted = autoCaps()
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
        val panel = emojiPanel ?: EmojiPanel(this, Theme.resolve(this, prefs.themeMode), EmojiData.get(this)).also { p ->
            p.listener = object : EmojiPanel.Listener {
                override fun onEmoji(emoji: String) { insertEmoji(emoji) }
                override fun onAbc() { openAbc() }
                override fun onBackspace() { deleteBackward() }
                override fun onSearch() { startEmojiSearch() }
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
        emojiSearch = false
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
        noLearning = isPassword || (attribute?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
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
        seedContext()
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
                seedContext()
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
        if (emojiSearch) { updateBar(); return }
        val ic = currentInputConnection ?: return
        if (engine.isEmpty) sink.clearComposing(ic) else sink.setComposing(ic, engine.text(), engine.isSuspicious())
        updateBar()
    }

    /** Thanh trên bàn phím: biến thể dấu khi đang soạn; thanh chỉnh sửa khi có vùng chọn / chế độ con trỏ. */
    private fun updateBar() {
        updateShift()
        if (!::candidateBar.isInitialized) return
        if (emojiSearch) {
            val q = engine.text()
            val results = if (q.isEmpty()) prefs.recentEmoji.take(20) else EmojiData.get(this).search(q, 30)
            candidateBar.showSuggestions("🔍 " + q.ifEmpty { "tìm emoji…" }, results) { insertEmoji(it); stopEmojiSearch() }
            return
        }
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
        } else if (!engine.isEmpty && engine.hasVowel() && !engine.isValid()) {
            // Âm tiết chưa hợp lệ: giữ đúng chữ đang gõ ở ô đầu, kèm gợi ý sửa (biến thể dấu lúc này vô nghĩa).
            val typed = engine.text()
            val fixes = corrector?.suggest(engine.rawText(), engine.rawCaps()) ?: emptyList()
            candidateBar.showSuggestions(typed, fixes) { replaceComposing(it) }
        } else if (!engine.isEmpty) {
            val typed = engine.text()
            val completions = if (prefs.predictions) predictions.predictor.next(recent.toList(), typed, 2).map { casedPrediction(it) } else emptyList()
            val emoji = emojiFor(typed)
            candidateBar.show(engine.candidates(), completions, emoji, onPickText = { replaceComposing(it) }, onPickEmoji = { commitSyllable(it) })
        } else if (prefs.predictions && mode == Mode.ABC && telexEnabled && afterSpace()) {
            val next = predictions.predictor.next(recent.toList(), "", 3).map { casedPrediction(it) }
            val emoji = recent.lastOrNull()?.let { emojiFor(it) } ?: emptyList()
            if (next.isEmpty() && emoji.isEmpty()) showWordAtCursorSuggestions()
            else candidateBar.showPredictions(next, emoji, onPick = { pick ->
                // Gợi ý được viết hoa vì đầu câu (không phải tên riêng đã học) thì học như chữ thường
                val auto = atSentenceStart() && !predictions.predictor.personal.usuallyCapitalized(pick)
                commitSyllable("$pick "); pushContext(pick, auto)
            }, onPickEmoji = { commitSyllable(it) }, onForget = { forget(it) })
        } else {
            showWordAtCursorSuggestions()
        }
    }

    private fun emojiFor(word: String): List<String> {
        val curated = EmojiKeywords.forWord(word, recent.lastOrNull()?.takeIf { it != word })
        if (curated.isNotEmpty()) return curated
        return if (word.length >= 3) EmojiData.get(this).exactKeyword(word, 2) else emptyList()
    }

    private fun afterSpace(): Boolean {
        val before = currentInputConnection?.getTextBeforeCursor(1, 0)?.toString() ?: return false
        return before.isEmpty() || before.last().isWhitespace()
    }

    /** Giữ một gợi ý học được để xoá khỏi từ điển cá nhân. */
    private fun forget(text: String) {
        predictions.predictor.personal.forget(text)
        personalDirty = true
        savePersonal.run()
        updateBar()
    }

    /** Thay âm tiết đang gõ bằng gợi ý người dùng chạm, rồi thêm khoảng trắng như khi chạm cách. */
    private fun replaceComposing(text: String) {
        val ic = currentInputConnection ?: return
        val auto = autoCappedFirst
        engine.reset()
        sink.commit(ic, "$text ")
        history.record(EditHistory.Op.Insert("$text "))
        pushContext(text, auto) // học cả từ gợi ý người dùng đã chọn
        updateBar()
    }

    /** Con trỏ nằm trong một từ đã gõ: hiện phương án sửa cho từ đó (kiểu Gboard). */
    private fun showWordAtCursorSuggestions() {
        val ic = currentInputConnection ?: run { candidateBar.clear(); return }
        if (hasSelection() || cursorMode || mode != Mode.ABC) { candidateBar.clear(); return }
        val before = ic.getTextBeforeCursor(24, 0)?.toString() ?: ""
        val after = ic.getTextAfterCursor(24, 0)?.toString() ?: ""
        var i = before.length
        while (i > 0 && before[i - 1].isLetter()) i--
        var j = 0
        while (j < after.length && after[j].isLetter()) j++
        val head = before.substring(i); val tail = after.substring(0, j)
        val word = head + tail
        if (word.isEmpty() || head.isEmpty()) { candidateBar.clear(); return }
        val (keys, caps) = TelexDecomposer.keysOf(word, engine.config) ?: run { candidateBar.clear(); return }
        val fixes = corrector?.suggest(keys, caps, 4) ?: emptyList()
        if (fixes.isEmpty()) { candidateBar.clear(); return }
        candidateBar.showSuggestions(word, fixes) { fix ->
            val c = currentInputConnection ?: return@showSuggestions
            c.beginBatchEdit()
            c.deleteSurroundingText(head.length, tail.length)
            c.commitText(fix, 1)
            c.endBatchEdit()
            history.record(EditHistory.Op.Delete(word)); history.record(EditHistory.Op.Insert(fix))
        }
    }

    /** Chốt âm tiết đang soạn rồi chèn thêm [extra]. */
    private fun commitSyllable(extra: String = "") {
        if (emojiSearch && (extra == " " || extra == "\n" || extra.isEmpty())) {
            if (extra.isEmpty() || extra == "\n") stopEmojiSearch() else { engine.reset(); updateBar() }
            return
        }
        if (emojiSearch) stopEmojiSearch()
        val ic = currentInputConnection ?: return
        val syllable = engine.text()
        var text = syllable + extra
        engine.reset()
        var shortcutHead = 0
        if (extra == " " && prefs.shortcuts.isNotEmpty()) {
            // Gõ tắt: so khớp token trước con trỏ (cả phần đã commit như "@@") + âm tiết đang soạn
            // getTextBeforeCursor có cả phần đang soạn (composing) nên bỏ nó đi trước
            val before = (ic.getTextBeforeCursor(32 + syllable.length, 0)?.toString() ?: "").removeSuffix(syllable)
            val head = before.takeLastWhile { !it.isWhitespace() }
            val token = head + syllable
            // "Ko" (tự viết hoa đầu câu) khớp "ko" thì giữ chữ hoa: "Không"
            val expansion = prefs.shortcuts[token]
                ?: prefs.shortcuts[token.lowercase()]?.let { e -> if (token.first().isUpperCase()) e.replaceFirstChar { it.titlecase() } else e }
            if (expansion != null) {
                shortcutHead = head.length
                text = expansion + " "
            }
        }
        if (text.isNotEmpty()) {
            if (hasSelection()) history.record(EditHistory.Op.Delete(ic.getSelectedText(0)?.toString() ?: ""))
            if (shortcutHead > 0) {
                ic.beginBatchEdit()
                sink.clearComposing(ic) // bỏ âm tiết đang soạn khỏi ô nhập
                val head = ic.getTextBeforeCursor(shortcutHead, 0)?.toString() ?: ""
                ic.deleteSurroundingText(shortcutHead, 0)
                history.record(EditHistory.Op.Delete(head))
                ic.commitText(text, 1)
                ic.endBatchEdit()
            } else sink.commit(ic, text)
            history.record(EditHistory.Op.Insert(text))
        } else sink.detach(ic)
        if (syllable.isNotEmpty() && shortcutHead == 0) pushContext(syllable, autoCappedFirst)
        if (extra.isNotEmpty() && extra.last() in ".!?\n") recent.clear()
        updateBar()
    }

    /**
     * Như Gboard: vuốt lên đảo hoa/thường so với trạng thái hiện tại (nhãn phím cho biết trạng thái),
     * nên đầu câu vuốt lên ra chữ thường.
     */
    private fun typeLetter(ch: Char, swipedUp: Boolean) {
        val auto = autoCaps()
        val upper = auto != swipedUp
        if (engine.isEmpty) autoCappedFirst = auto && !swipedUp
        if (!telexEnabled) {
            commitSyllable((if (upper) ch.uppercaseChar() else ch).toString())
            return
        }
        engine.press(ch, upper)
        updateComposing()
    }

    /**
     * Phím chữ sắp gõ có tự viết hoa không. Theo cờ CAP_* của ô nhập. Ô không đặt cờ nào thường là
     * cố ý (username, tìm kiếm, mã), nên chỉ coi như viết hoa đầu câu khi là ô tin nhắn / nhiều dòng.
     */
    private fun autoCaps(): Boolean {
        if (!prefs.autoCapitalize || !telexEnabled || emojiSearch || mode != Mode.ABC) return false
        val type = currentInputEditorInfo?.inputType ?: return false
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val capFlags = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        var req = type and capFlags
        if (req == 0) {
            val v = type and InputType.TYPE_MASK_VARIATION
            val message = v == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE || v == InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE ||
                (v == InputType.TYPE_TEXT_VARIATION_NORMAL && type and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0)
            if (!message) return false
            req = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        // Đang giữa âm tiết: chỉ CAP_CHARACTERS mới viết hoa tiếp
        if (!engine.isEmpty) req = req and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        if (req == 0) return false
        return (currentInputConnection?.getCursorCapsMode(req) ?: 0) != 0
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

    /**
     * Ô có hành động (Gửi, Tìm…) thì làm hành động đó; ngược lại chèn "\n" như Gboard.
     * Không gửi phím ENTER: Messenger và nhiều app chat coi phím ENTER cứng là "gửi".
     * Chỉ ô TYPE_NULL (terminal) mới cần phím thật.
     */
    private fun pressEnter() {
        commitSyllable() // cũng thoát tìm emoji
        if (sendDefaultEditorAction(true)) return
        if ((currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL) == InputType.TYPE_NULL) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        else commitSyllable("\n")
    }

    // ---- Chỉnh sửa: undo/redo, cut/copy/paste ----

    private fun undo() {
        val ic = currentInputConnection ?: return
        commitSyllable()
        if (history.undo(ic)) {
            keyboardView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            flash("↶ Đã hoàn tác")
        } else flash("Không còn gì để hoàn tác")
        updateBar()
    }

    private fun redo() {
        val ic = currentInputConnection ?: return
        commitSyllable()
        flash(if (history.redo(ic)) "↷ Đã làm lại" else "Không còn gì để làm lại")
        updateBar()
    }

    private fun copy() {
        val ic = currentInputConnection ?: return
        if (!hasSelection()) return
        if (!ic.performContextMenuAction(android.R.id.copy)) {
            val text = ic.getSelectedText(0) ?: return
            clipboard().setPrimaryClip(android.content.ClipData.newPlainText("text", text))
        }
        flash("Đã chép")
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
        flash("✂ Đã cắt")
        updateBar()
    }

    private fun paste() {
        val ic = currentInputConnection ?: return
        commitSyllable()
        val text = clipboard().primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrEmpty()) { flash("Clipboard trống"); return }
        if (hasSelection()) history.record(EditHistory.Op.Delete(ic.getSelectedText(0)?.toString() ?: ""))
        ic.commitText(text, 1)
        history.record(EditHistory.Op.Insert(text))
        flash("Đã dán")
        updateBar()
    }

    private fun selectAll() {
        val ic = currentInputConnection ?: return
        commitSyllable()
        if (ic.performContextMenuAction(android.R.id.selectAll)) flash("Đã chọn hết")
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
        override fun undoLastSpace(): Boolean {
            val ic = currentInputConnection ?: return false
            if (ic.getTextBeforeCursor(1, 0)?.toString() != " ") return false
            ic.deleteSurroundingText(1, 0)
            history.trimLastInsert(1)
            return true
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
        if (key.isSpace) spaceGestures.onDown(android.os.SystemClock.uptimeMillis()) else spaceGestures.noteOtherKey()
    }

    override fun onKeyMove(key: Key, dx: Float, dy: Float, dt: Long) {
        if (key.isSpace) spaceGestures.onMove(dx, dy)
    }

    override fun onKeyCancel(key: Key) {
        if (key.isSpace) spaceGestures.cancel()
    }

    override fun onMultiTouchStart() { spaceGestures.onMultiTouch() }

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
            code == KeyCode.SETTINGS -> if (gesture == Gesture.LONG_PRESS) openEditPanel() else openSettings()
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
