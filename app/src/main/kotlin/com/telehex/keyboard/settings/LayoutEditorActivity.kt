package com.telehex.keyboard.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
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
import com.telehex.keyboard.layout.Profile
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
        symbols = KeyLayers.reconcileSymbols(layout, symbols)
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
        drawFloatingKey(canvas, dragging ?: return, dragX, dragY)
    }

    companion object {
        private const val FLIP_MS = 500L
    }
}

class LayoutEditorActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var editor: LayoutEditorView
    private lateinit var profileButton: Button
    private lateinit var status: TextView
    private lateinit var help: TextView
    private lateinit var saveButton: Button
    /** Như đã lưu: profile người dùng và profile đang dùng. Lưới khác [active] là có thay đổi chưa lưu. */
    private lateinit var user: List<Profile>
    private lateinit var active: Profile

    /** Đang tự ghi profile ([commit]): đừng coi là thay đổi từ ngoài. */
    private var writing = false

    /**
     * Bàn phím cũng ghi profile (giữ rồi kéo đổi chỗ phím, kể cả khi đang gõ tên profile ở màn này): đọc lại để
     * lần lưu sau không ghi đè bằng danh sách cũ. Giữ tham chiếu mạnh vì SharedPreferences chỉ giữ yếu.
     */
    private val profilesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!writing && (key == Prefs.KEY_PROFILES || key == Prefs.KEY_ACTIVE_PROFILE)) syncFromPrefs()
    }

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

        user = prefs.userProfiles
        active = prefs.activeProfile
        editor = LayoutEditorView(this).apply { theme = Theme.resolve(context, prefs.themeMode, prefs.accent) }

        col.add(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            profileButton = settingsButton(context, "") {
                showProfilePicker(context, allProfiles(), active.name) { p -> confirmUnsaved { commit(user, p) } }
            }
            addView(profileButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(settingsButton(context, "⋮") { showMenu(this) }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginStart = dp(8) })
        }, dp(4), 0)
        status = TextView(this)
        col.add(status, dp(4), dp(8))

        val layers = listOf(EditLayer.LETTERS to "Chữ", EditLayer.SWIPE to "Vuốt xuống", EditLayer.SYMBOLS to "123")
        col.add(RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            for (pair in layers) addView(RadioButton(context).apply {
                id = View.generateViewId(); text = pair.second; isChecked = pair.first == EditLayer.LETTERS
                setOnCheckedChangeListener { _, on -> if (on) showLayer(pair.first) }
            }, RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
        help = TextView(this)
        col.add(help, dp(4), 0)

        saveButton = col.addButton("") { save() }
        col.addButton("Về mặc định (lớp này)") {
            // Mặc định theo kiểu lưới đang sửa (tổ ong hay truyền thống)
            val spec = editor.layout.spec
            when (editor.layer) {
                EditLayer.SWIPE -> editor.setSwipe(KeyLayers.defaultSwipe(spec))
                EditLayer.SYMBOLS -> editor.setSymbols(SymbolPages.defaultFor(spec))
                EditLayer.LETTERS -> editor.setLetters(LayoutModel.defaultFor(spec))
            }
        }
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        editor.onChanged = ::onEditorChanged
        editor.onTapSwipe = { key, r, c ->
            // Ở lớp này nhãn phụ (sub) là chữ của phím
            SwipeCharPicker.show(this, key.sub.ifEmpty { key.code }, editor.swipe[r, c], KeyLayers.defaultSwipe(editor.layout.spec)[r, c]) { v ->
                editor.setSwipeAt(r, c, v)
            }
        }
        load(active)
        showLayer(EditLayer.LETTERS)
        prefs.registerListener(profilesListener)
    }

    override fun onDestroy() {
        prefs.unregisterListener(profilesListener)
        super.onDestroy()
    }

    /** Profile đổi từ ngoài: nhận danh sách mới; chưa sửa gì thì nạp lưới mới, đang sửa dở thì giữ lưới đang sửa. */
    private fun syncFromPrefs() {
        val unsaved = hasUnsaved()
        user = prefs.userProfiles
        active = prefs.activeProfile
        if (unsaved) onEditorChanged() else load(active)
    }

    private fun showLayer(l: EditLayer) {
        editor.layer = l
        help.text = when (l) {
            EditLayer.LETTERS -> "Kéo một phím thả lên phím khác để đổi chỗ. Phím cách và ↵ cố định. Ký tự vuốt xuống và lớp 123 đứng yên theo ô."
            EditLayer.SWIPE -> "Chữ to là ký tự vuốt xuống, chữ nhỏ là phím. Chạm một phím để chọn ký tự, ký hiệu, emoji hoặc đoạn văn bản ngắn (tối đa ${SwipeCharPicker.MAX_LEN} ký tự); kéo sang phím khác để đổi chỗ."
            EditLayer.SYMBOLS -> "Kéo ký tự thả lên ký tự khác để đổi chỗ. Đang kéo thì giữ ngón trên phím 1/2 để sang trang kia; chạm 1/2 để xem trang kia."
        }
    }

    private fun allProfiles() = Profile.BUILT_IN + user

    private fun editing(name: String) = Profile(name, editor.layout, editor.swipe, editor.symbols)

    private fun hasUnsaved() = !editing(active.name).sameLayers(active)

    private fun load(p: Profile) = editor.load(p.letters, p.swipe, p.symbols)

    /** Ghi profile và dùng [newActive] cho bàn phím ngay. [keepEdits]: giữ lưới đang sửa (đổi tên), không thì nạp [newActive]. */
    private fun commit(newUser: List<Profile>, newActive: Profile, keepEdits: Boolean = false) {
        writing = true
        try { prefs.saveProfiles(newUser, newActive) } finally { writing = false }
        user = newUser
        active = newActive
        if (keepEdits) onEditorChanged() else load(newActive)
    }

    /** Thanh profile: ● khi có thay đổi chưa lưu, 🔒 ở profile có sẵn; nút Lưu sáng khi có thay đổi. */
    private fun onEditorChanged() {
        val dirty = hasUnsaved()
        val err = editor.layout.validate()
        profileButton.text = buildString {
            if (dirty) append("● ")
            append(active.name)
            if (active.isBuiltIn) append(" 🔒")
            append("  ▾")
        }
        status.text = when {
            err != null -> err
            dirty && active.isBuiltIn -> "Có thay đổi chưa lưu. Profile có sẵn không sửa được, lưu sẽ tạo profile mới."
            dirty -> "Có thay đổi chưa lưu"
            else -> "Đang dùng trên bàn phím"
        }
        saveButton.text = if (active.isBuiltIn) "Lưu thành profile mới…" else "Lưu"
        saveButton.isEnabled = dirty
        saveButton.alpha = if (dirty) 1f else 0.4f
        guardBack(dirty)
    }

    /**
     * Lưu lưới vào profile đang dùng (profile có sẵn thì hỏi tên profile mới), rồi chạy [then]. Báo đã lưu chỉ khi
     * không có [then]: [then] (chuyển profile, thoát) đổi ngay thứ vừa báo.
     */
    private fun save(then: (() -> Unit)? = null) {
        editor.layout.validate()?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show(); return }
        if (active.isBuiltIn) {
            askName("Lưu thành profile mới", Profile.uniqueName(active.name, allProfiles().map { it.name })) { n ->
                val p = editing(n)
                commit(user + p, p)
                if (then == null) Toast.makeText(this, "Đã tạo và dùng \"$n\"", Toast.LENGTH_SHORT).show() else then()
            }
        } else {
            val p = editing(active.name)
            commit(user.map { if (it.name == p.name) p else it }, p)
            if (then == null) Toast.makeText(this, "Đã lưu \"${p.name}\"", Toast.LENGTH_SHORT).show() else then()
        }
    }

    private fun showMenu(anchor: View) {
        val builtIn = active.isBuiltIn
        val menu = PopupMenu(this, anchor)
        val items = listOf(
            Triple("Tạo mới…", true) { confirmUnsaved(::createProfile) },
            Triple("Nhân bản…", true, ::duplicateProfile),
            Triple("Đổi tên…", !builtIn, ::renameProfile),
            Triple("Xoá", !builtIn, ::deleteProfile),
            Triple("Bỏ thay đổi", hasUnsaved()) { load(active) },
        )
        items.forEachIndexed { i, (label, enabled, _) -> menu.menu.add(0, i, i, label).isEnabled = enabled }
        menu.setOnMenuItemClickListener { items[it.itemId].third(); true }
        menu.show()
    }

    private fun createProfile() {
        askName("Tạo profile", Profile.uniqueName("Profile mới", allProfiles().map { it.name })) { n ->
            // Profile mới cùng kiểu lưới với profile đang dùng
            val p = Profile.builtInFor(active.spec).named(n)
            commit(user + p, p)
        }
    }

    /** Chép lưới đang sửa (kể cả thay đổi chưa lưu) sang profile mới rồi dùng nó; profile gốc giữ nguyên. */
    private fun duplicateProfile() {
        editor.layout.validate()?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show(); return }
        // Cắt tên gốc để cả hậu tố lẫn số thêm vào khi trùng (" 2") vẫn lọt [MAX_NAME]
        val base = "${active.name.take(MAX_NAME - COPY_SUFFIX.length - 3).trimEnd()}$COPY_SUFFIX"
        askName("Nhân bản", Profile.uniqueName(base, allProfiles().map { it.name })) { n ->
            val p = editing(n)
            commit(user + p, p)
        }
    }

    private fun renameProfile() {
        val old = active.name
        askName("Đổi tên", old, except = old) { n ->
            val r = active.named(n)
            commit(user.map { if (it.name == old) r else it }, r, keepEdits = true)
        }
    }

    private fun deleteProfile() {
        AlertDialog.Builder(this)
            .setMessage("Xoá profile \"${active.name}\"? Bàn phím chuyển về \"${Profile.DEFAULT.name}\"." +
                if (hasUnsaved()) " Thay đổi chưa lưu cũng mất." else "")
            .setPositiveButton("Xoá") { _, _ -> commit(user.filter { it.name != active.name }, Profile.DEFAULT) }
            .setNegativeButton("Huỷ", null).show()
    }

    /** Hỏi tên profile; không đóng hộp khi tên trống hoặc trùng (không phân biệt hoa thường, trừ [except]). */
    private fun askName(title: String, initial: String, except: String? = null, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Tên profile"; isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(MAX_NAME))
            setText(initial); selectAll()
        }
        val box = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        val dialog = AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton("OK", null).setNegativeButton("Huỷ", null).create()
        // Mở bàn phím ngay vào ô tên, không cần chạm ô trước
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = input.text.toString().trim()
            when {
                n.isEmpty() -> input.error = "Nhập tên"
                Profile.nameTaken(n, allProfiles().map { it.name }.filter { it != except }) -> input.error = "Đã có profile tên này"
                else -> { dialog.dismiss(); onOk(n) }
            }
        }
        input.requestFocus()
    }

    /**
     * Back đi qua OnBackInvokedDispatcher khi có predictive back (Android 16+ vì targetSdk 36); khi đó
     * onBackPressed không được gọi. Chỉ đăng ký khi có thay đổi chưa lưu, để back thường vẫn có hiệu ứng.
     */
    private var backCallback: Any? = null

    private fun guardBack(on: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val registered = backCallback as? android.window.OnBackInvokedCallback
        if (on && registered == null) {
            val cb = android.window.OnBackInvokedCallback { confirmUnsaved(::finish) }
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
        if (hasUnsaved()) confirmUnsaved(::finish) else @Suppress("DEPRECATION") super.onBackPressed()
    }

    /** Mũi tên ← trên thanh tiêu đề. */
    override fun onNavigateUp(): Boolean {
        if (hasUnsaved()) { confirmUnsaved(::finish); return true }
        return super.onNavigateUp()
    }

    /** Có thay đổi chưa lưu thì hỏi Lưu / Bỏ thay đổi / Ở lại trước khi [proceed]. */
    private fun confirmUnsaved(proceed: () -> Unit) {
        if (!hasUnsaved()) { proceed(); return }
        AlertDialog.Builder(this)
            .setMessage("\"${active.name}\" có thay đổi chưa lưu.")
            .setPositiveButton(if (active.isBuiltIn) "Lưu thành profile mới…" else "Lưu") { _, _ -> save(proceed) }
            .setNegativeButton("Bỏ thay đổi") { _, _ -> proceed() }
            .setNeutralButton("Ở lại", null)
            .show()
    }

    companion object {
        private const val MAX_NAME = 40
        private const val COPY_SUFFIX = " (bản sao)"
    }
}

/** Hộp chọn profile: profile có sẵn (🔒) rồi profile của bạn, chấm chọn ở profile đang dùng. */
internal fun showProfilePicker(context: Context, all: List<Profile>, active: String, onPick: (Profile) -> Unit) {
    val labels = all.map { if (it.isBuiltIn) "${it.name} 🔒" else it.name }.toTypedArray()
    AlertDialog.Builder(context).setTitle("Chọn profile")
        .setSingleChoiceItems(labels, all.indexOfFirst { it.name == active }) { d, i ->
            d.dismiss()
            if (all[i].name != active) onPick(all[i])
        }
        .setNegativeButton("Huỷ", null).show()
}
