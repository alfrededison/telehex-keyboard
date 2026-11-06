package com.telehex.keyboard.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.telehex.keyboard.data.Prefs
import com.telehex.keyboard.ime.Key
import com.telehex.keyboard.ime.KeyKind
import com.telehex.keyboard.ime.KeyPage
import com.telehex.keyboard.ime.KeyboardView
import com.telehex.keyboard.ime.Mode
import com.telehex.keyboard.ime.Theme
import com.telehex.keyboard.layout.CellGrid
import com.telehex.keyboard.layout.GridCell
import com.telehex.keyboard.layout.KeyCode
import com.telehex.keyboard.layout.KeyLayers
import com.telehex.keyboard.layout.LayoutModel
import com.telehex.keyboard.layout.SymbolPages

/** Lớp đang sắp xếp: mỗi lớp đổi chỗ độc lập, hai lớp còn lại đứng yên theo ô. */
enum class EditLayer { LETTERS, SWIPE, SYMBOLS }

/**
 * Editor vẽ đúng lưới thật; kéo một phím thả lên phím khác để đổi chỗ trong lớp đang chọn.
 * Lớp vuốt xuống: chạm một phím (không kéo đi) để đổi ký tự của ô đó ([onTapSwipe]).
 * Lớp 123: giữ ngón trên phím 1/2 lúc đang kéo để sang trang kia; chạm 1/2 để xem trang kia.
 */
class LayoutEditorView(context: Context) : KeyboardView(context) {
    var layout: LayoutModel = LayoutModel.DEFAULT
        private set
    var swipe: CellGrid = KeyLayers.DEFAULT_SWIPE
        private set
    var symbols: List<CellGrid> = SymbolPages.DEFAULT
        private set
    var layer: EditLayer = EditLayer.LETTERS
        set(value) { field = value; symbolPage = 1; rebuildKeys() }
    var onChanged: (() -> Unit)? = null
    /** Chạm (không kéo) một phím ở lớp vuốt xuống: nhận phím và ô của nó. */
    var onTapSwipe: ((Key, Int, Int) -> Unit)? = null

    private var symbolPage = 1
    private var dragging: Key? = null
    private var dragFrom: GridCell? = null
    private var downKey: Key? = null
    private var dragX = 0f
    private var dragY = 0f
    private val flipPage = Runnable { if (dragging != null) { togglePage(); performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP) } }
    private var flipPending = false

    fun load(layout: LayoutModel, swipe: CellGrid, symbols: List<CellGrid>) {
        this.layout = layout; this.swipe = swipe; this.symbols = symbols
        reconcileLayers(); changed()
    }

    fun setLetters(l: LayoutModel) { layout = l; reconcileLayers(); changed() }

    fun setSwipe(g: CellGrid) { swipe = g; reconcileLayers(); changed() }

    fun setSwipeAt(r: Int, c: Int, value: String) = setSwipe(swipe.with(r, c, value))

    fun setSymbols(p: List<CellGrid>) { symbols = p; reconcileLayers(); changed() }

    /** Kéo phím chức năng ở lớp chữ có thể che một ô của lớp khác: dời giá trị bị che sang ô trống. */
    private fun reconcileLayers() {
        swipe = KeyLayers.reconcile(layout, listOf(swipe), KeyLayers::swipeUsable).single()
        symbols = KeyLayers.reconcile(layout, symbols, KeyLayers::symbolUsable)
    }

    private fun changed() { rebuildKeys(); onChanged?.invoke() }

    private fun togglePage() { symbolPage = if (symbolPage == 2) 1 else 2; rebuildKeys() }

    private fun rebuildKeys() {
        val telex = com.telehex.telex.TelexConfig()
        keys = when (layer) {
            EditLayer.LETTERS -> KeyPage.build(layout, Mode.ABC, 1, telex, swipe, symbols, "↵")
            // Ký tự vuốt xuống làm nhãn chính, chữ nhỏ bên dưới để biết đang ở phím nào
            EditLayer.SWIPE -> KeyPage.build(layout, Mode.ABC, 1, telex, swipe, symbols, "↵").map { k ->
                if (hasSwipe(k)) k.copy(label = k.hint.ifEmpty { "·" }, sub = k.label, hint = "") else k
            }
            EditLayer.SYMBOLS -> KeyPage.build(layout, Mode.SYMBOLS, symbolPage, telex, swipe, symbols, "↵")
        }
    }

    private fun hasSwipe(k: Key) = k.isLetter || k.code == "," || k.code == "."

    private fun gridCell(k: Key): GridCell {
        val (r, c) = k.cells.first()
        return GridCell(if (layer == EditLayer.SYMBOLS) symbolPage - 1 else 0, r, c)
    }

    private fun movable(k: Key?): Boolean {
        if (k == null) return false
        return when (layer) {
            EditLayer.LETTERS -> !k.isSpace && k.code != KeyCode.ENTER
            EditLayer.SWIPE -> hasSwipe(k)
            EditLayer.SYMBOLS -> k.kind == KeyKind.SYMBOL
        }
    }

    private fun drop(from: Key, fromCell: GridCell, to: Key) {
        val toCell = gridCell(to)
        if (toCell == fromCell) return
        when (layer) {
            EditLayer.LETTERS -> setLetters(layout.withSwapped(from.cells.first(), to.cells.first()))
            EditLayer.SWIPE -> setSwipe(KeyLayers.swap(listOf(swipe), fromCell, toCell).single())
            EditLayer.SYMBOLS -> setSymbols(KeyLayers.swap(symbols, fromCell, toCell))
        }
    }

    private fun cancelFlip() { if (flipPending) { removeCallbacks(flipPage); flipPending = false } }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val k = keyAt(event.x, event.y)
                downKey = k
                if (k == null || !movable(k)) return true
                dragging = k; dragFrom = gridCell(k); dragX = event.x; dragY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                highlighted = setOf(k)
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging == null) return true
                dragX = event.x; dragY = event.y
                val over = keyAt(event.x, event.y)
                if (layer == EditLayer.SYMBOLS && over?.code == KeyCode.PAGE) {
                    if (!flipPending) { flipPending = true; postDelayed(flipPage, FLIP_MS) }
                } else cancelFlip()
                highlighted = setOfNotNull(dragging, over.takeIf { movable(it) })
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelFlip()
                val from = dragging
                val fromCell = dragFrom
                dragging = null; dragFrom = null
                highlighted = emptySet()
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val to = keyAt(event.x, event.y)
                    if (from != null && fromCell != null) {
                        if (layer == EditLayer.SWIPE && to != null && gridCell(to) == fromCell) {
                            val (r, c) = to.cells.first()
                            onTapSwipe?.invoke(to, r, c)
                        } else if (movable(to)) drop(from, fromCell, to!!)
                    } else if (layer == EditLayer.SYMBOLS && to?.code == KeyCode.PAGE && downKey?.code == KeyCode.PAGE) {
                        togglePage()
                    }
                }
                downKey = null
                invalidate()
            }
        }
        return true
    }

    override fun drawOverlay(canvas: Canvas) {
        val d = dragging ?: return
        val paint = labelPaint()
        paint.color = theme.fgFor(d.kind)
        paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28f, resources.displayMetrics)
        val r = dp(26f)
        val fill = fillPaint()
        fill.color = theme.bgFor(d.kind)
        canvas.drawCircle(dragX, dragY - dp(40f), r, fill)
        canvas.drawText(d.label, dragX, dragY - dp(40f) + paint.textSize * 0.36f, paint)
    }

    companion object {
        private const val FLIP_MS = 500L
    }
}

class LayoutEditorActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var editor: LayoutEditorView
    private lateinit var status: TextView
    private lateinit var help: TextView
    private lateinit var presetList: LinearLayout
    private lateinit var presetSection: LinearLayout
    private lateinit var resetLayer: Button
    private lateinit var saveButton: Button
    private lateinit var savedLayout: LayoutModel
    private lateinit var savedSwipe: CellGrid
    private lateinit var savedSymbols: List<CellGrid>
    private val presetButtons = ArrayList<Pair<Button, Pair<String, LayoutModel>>>()

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        SettingsUi.applyTheme(this, prefs)
        super.onCreate(savedInstanceState)
        // Nút ở trên, lưới phím ở đáy màn hình đúng chỗ bàn phím thật sẽ hiện
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(12)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ScrollView(context).apply { addView(col) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            // Không edge-to-edge (Android < 15) thì decor đã chừa thanh hệ thống và listener có thể không được gọi
            setPadding(0, 0, 0, dp(prefs.extraBottomDp))
            setOnApplyWindowInsetsListener { v, insets ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    v.setPadding(0, bars.top, 0, bars.bottom + dp(prefs.extraBottomDp))
                } else {
                    v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom + dp(prefs.extraBottomDp))
                }
                insets
            }
        }
        setContentView(root)

        savedLayout = prefs.layout
        savedSwipe = prefs.swipeGrid
        savedSymbols = prefs.symbolPages

        editor = LayoutEditorView(this).apply {
            theme = Theme.resolve(context, prefs.themeMode, prefs.accent)
            load(savedLayout, savedSwipe, savedSymbols)
        }

        val startLayer = runCatching { EditLayer.valueOf(intent.getStringExtra(EXTRA_LAYER) ?: "") }.getOrDefault(EditLayer.LETTERS)
        val layers = listOf(EditLayer.LETTERS to "Chữ", EditLayer.SWIPE to "Vuốt xuống", EditLayer.SYMBOLS to "123")
        col.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            for (pair in layers) addView(RadioButton(context).apply {
                id = View.generateViewId(); text = pair.second; isChecked = pair.first == startLayer
                setOnCheckedChangeListener { _, on -> if (on) showLayer(pair.first) }
            }, RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        help = TextView(this)
        col.add(help, dp(4), 0)
        status = TextView(this)
        col.add(status)

        saveButton = settingsButton(this, "Lưu") {
            val err = editor.layout.validate()
            if (err != null) { Toast.makeText(context, err, Toast.LENGTH_LONG).show(); return@settingsButton }
            prefs.layout = editor.layout
            prefs.swipeGrid = editor.swipe
            prefs.symbolPages = editor.symbols
            Toast.makeText(context, "Đã lưu bố cục", Toast.LENGTH_SHORT).show()
            finish()
        }
        col.add(saveButton, dp(4), dp(4))
        resetLayer = col.addButton("Về mặc định (lớp này)") {
            when (editor.layer) {
                EditLayer.SWIPE -> editor.setSwipe(KeyLayers.DEFAULT_SWIPE)
                EditLayer.SYMBOLS -> editor.setSymbols(SymbolPages.DEFAULT)
                EditLayer.LETTERS -> editor.setLetters(LayoutModel.DEFAULT)
            }
        }

        presetList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        presetSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addButton("Lưu thành preset…") { askPresetName() }
            add(TextView(context).apply {
                text = "Preset chữ (chạm để nạp vào lưới, bấm Lưu để dùng)"
                setTypeface(typeface, Typeface.BOLD)
            }, topMargin = dp(16))
            add(presetList)
        }
        col.add(presetSection)
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        showPresets()
        editor.onChanged = ::onEditorChanged
        editor.onTapSwipe = { key, r, c ->
            // Ở lớp này nhãn phụ (sub) là chữ của phím
            SwipeCharPicker.show(this, key.sub.ifEmpty { key.code }, editor.swipe[r, c], KeyLayers.DEFAULT_SWIPE[r, c]) { v ->
                editor.setSwipeAt(r, c, v)
            }
        }
        onEditorChanged()
        showLayer(startLayer)
    }

    private fun showLayer(l: EditLayer) {
        editor.layer = l
        help.text = when (l) {
            EditLayer.LETTERS -> "Kéo một phím thả lên phím khác để đổi chỗ. Phím cách và ↵ cố định. Ký tự vuốt xuống và lớp 123 đứng yên theo ô."
            EditLayer.SWIPE -> "Chữ to là ký tự vuốt xuống, chữ nhỏ là phím. Chạm một phím để chọn ký tự, ký hiệu, emoji hoặc đoạn văn bản ngắn (tối đa ${SwipeCharPicker.MAX_LEN} ký tự); kéo sang phím khác để đổi chỗ."
            EditLayer.SYMBOLS -> "Kéo ký tự thả lên ký tự khác để đổi chỗ. Đang kéo thì giữ ngón trên phím 1/2 để sang trang kia; chạm 1/2 để xem trang kia."
        }
        presetSection.visibility = if (l == EditLayer.LETTERS) View.VISIBLE else View.GONE
        resetLayer.visibility = if (l == EditLayer.LETTERS) View.GONE else View.VISIBLE
    }

    /** Lưu sáng khi bất kỳ lớp nào khác bản đang dùng; ✓ theo preset trùng lớp chữ. */
    private fun onEditorChanged() {
        val l = editor.layout
        status.text = l.validate() ?: "Bố cục hợp lệ"
        val changed = l.rows != savedLayout.rows || editor.swipe != savedSwipe || editor.symbols != savedSymbols
        saveButton.isEnabled = changed
        saveButton.alpha = if (changed) 1f else 0.4f
        markChecked(l)
        guardBack(changed)
    }

    private fun hasUnsaved() = saveButton.isEnabled

    /**
     * Back đi qua OnBackInvokedDispatcher khi có predictive back (Android 16+ vì targetSdk 36); khi đó
     * onBackPressed không được gọi. Chỉ đăng ký khi có thay đổi chưa lưu, để back thường vẫn có hiệu ứng.
     */
    private var backCallback: Any? = null

    private fun guardBack(on: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val registered = backCallback as? android.window.OnBackInvokedCallback
        if (on && registered == null) {
            val cb = android.window.OnBackInvokedCallback { confirmDiscard() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            backCallback = cb
        } else if (!on && registered != null) {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(registered)
            backCallback = null
        }
    }

    /**
     * Back khi chưa có predictive back (Android ≤ 15): hệ thống vẫn gọi hàm này. Lint báo GestureBackNavigation
     * cho mọi override, nhưng Android 16+ đã có [guardBack]; app không dùng AndroidX nên không có OnBackPressedDispatcher.
     */
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Chỉ cho Android ≤ 15; Android 16+ dùng guardBack")
    override fun onBackPressed() {
        if (hasUnsaved()) confirmDiscard() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    /** Mũi tên ← trên thanh tiêu đề. */
    override fun onNavigateUp(): Boolean {
        if (hasUnsaved()) { confirmDiscard(); return true }
        return super.onNavigateUp()
    }

    private fun confirmDiscard() {
        AlertDialog.Builder(this)
            .setMessage("Bố cục có thay đổi chưa lưu.")
            .setPositiveButton("Lưu") { _, _ -> saveButton.performClick() }
            .setNegativeButton("Bỏ thay đổi") { _, _ -> finish() }
            .setNeutralButton("Ở lại", null)
            .show()
    }

    private fun askPresetName() {
        val input = EditText(this).apply { hint = "Tên preset"; isSingleLine = true }
        val dialog = AlertDialog.Builder(this).setTitle("Lưu preset").setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val name = input.text.toString().trim()
                if (LayoutModel.BUILT_IN.any { it.first.equals(name, ignoreCase = true) }) {
                    Toast.makeText(this, "\"$name\" là preset có sẵn, chọn tên khác", Toast.LENGTH_LONG).show()
                } else if (name.isNotEmpty()) {
                    prefs.presets = prefs.presets + (name to editor.layout)
                    showPresets()
                }
            }
            .setNegativeButton("Huỷ", null).create()
        // Mở bàn phím ngay vào ô tên, không cần chạm ô trước
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        input.requestFocus()
    }

    /**
     * Preset có sẵn rồi preset đã lưu (chỉ lớp chữ), dấu ✓ ở preset trùng lưới đang sửa.
     * Chỉ đọc lại preset khi mở màn, lưu hoặc xoá preset; lưới đổi thì chỉ cập nhật nhãn ([markChecked]).
     */
    private fun showPresets() {
        presetList.removeAllViews()
        presetButtons.clear()
        for ((name, l) in LayoutModel.BUILT_IN) presetList.add(presetRow(name, l, deletable = false))
        for ((name, l) in prefs.presets.toSortedMap()) presetList.add(presetRow(name, l, deletable = true))
    }

    /** Không trùng preset nào thì bỏ hết ✓. */
    private fun markChecked(grid: LayoutModel) {
        for ((b, entry) in presetButtons) b.text = label(entry.first, entry.second, grid)
    }

    private fun label(name: String, layout: LayoutModel, grid: LayoutModel) = if (layout.rows == grid.rows) "✓ $name" else name

    private fun presetRow(name: String, layout: LayoutModel, deletable: Boolean): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val b = settingsButton(context, label(name, layout, editor.layout)) { editor.setLetters(layout) }
            presetButtons.add(b to (name to layout))
            addView(b, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (deletable) addView(settingsButton(context, "Xoá") {
                AlertDialog.Builder(context).setMessage("Xoá preset \"$name\"?")
                    .setPositiveButton("Xoá") { _, _ ->
                        prefs.presets = prefs.presets - name
                        showPresets()
                    }
                    .setNegativeButton("Huỷ", null).show()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.marginStart = dp(8)
            })
            setPadding(0, dp(4), 0, dp(4))
        }

    companion object {
        /** Tên [EditLayer] mở sẵn khi vào màn. */
        const val EXTRA_LAYER = "layer"
    }
}
