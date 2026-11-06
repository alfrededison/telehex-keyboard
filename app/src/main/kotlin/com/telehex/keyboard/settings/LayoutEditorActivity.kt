package com.telehex.keyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.telehex.keyboard.data.Prefs
import com.telehex.keyboard.ime.Key
import com.telehex.keyboard.ime.KeyPage
import com.telehex.keyboard.ime.KeyboardView
import com.telehex.keyboard.ime.Mode
import com.telehex.keyboard.ime.Theme
import com.telehex.keyboard.layout.KeyCode
import com.telehex.keyboard.layout.LayoutModel

/** Editor vẽ đúng lưới thật; kéo một phím thả lên phím khác để đổi chỗ. Phím cách và Enter cố định. */
class LayoutEditorView(context: Context) : KeyboardView(context) {
    var layout: LayoutModel = LayoutModel.DEFAULT
        set(value) { field = value; onLayoutChanged?.invoke(value); rebuildKeys() }
    var onLayoutChanged: ((LayoutModel) -> Unit)? = null
    var hintOf: (String) -> String = { "" }

    private var dragging: Key? = null
    private var dragX = 0f
    private var dragY = 0f

    fun rebuildKeys() {
        keys = KeyPage.build(layout, Mode.ABC, 1, com.telehex.telex.TelexConfig(), hintOf, "↵")
    }

    private fun movable(k: Key?): Boolean = k != null && !k.isSpace && k.code != KeyCode.ENTER

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val k = keyAt(event.x, event.y)
                if (!movable(k)) return true
                dragging = k; dragX = event.x; dragY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                highlighted = setOf(k!!)
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging == null) return true
                dragX = event.x; dragY = event.y
                val over = keyAt(event.x, event.y)
                highlighted = setOfNotNull(dragging, over.takeIf { movable(it) })
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val from = dragging ?: return true
                dragging = null
                highlighted = emptySet()
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val to = keyAt(event.x, event.y)
                    if (movable(to) && to != from) {
                        layout = layout.withSwapped(from.cells.first(), to!!.cells.first())
                    }
                }
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
}

class LayoutEditorActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var editor: LayoutEditorView
    private lateinit var status: TextView
    private lateinit var presetList: LinearLayout
    private lateinit var saveButton: Button
    /** Bố cục đang dùng (đã lưu). */
    private lateinit var saved: LayoutModel
    private val presetButtons = ArrayList<Pair<Button, Pair<String, LayoutModel>>>()

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
                    @Suppress("DEPRECATION")
                    v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom + dp(prefs.extraBottomDp))
                }
                insets
            }
        }
        setContentView(root)

        col.add(TextView(this).apply {
            text = "Kéo một phím thả lên phím khác để đổi chỗ. Phím cách và ↵ cố định. Số ở lớp ký tự tự đi theo chữ (Q→1 … P→0)."
        })
        status = TextView(this)
        presetList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        saved = prefs.layout
        // Lưu chỉ sáng khi lưới khác bố cục đang dùng
        saveButton = settingsButton(this, "Lưu") {
            val err = editor.layout.validate()
            if (err != null) { Toast.makeText(context, err, Toast.LENGTH_LONG).show(); return@settingsButton }
            prefs.layout = editor.layout
            Toast.makeText(context, "Đã lưu bố cục", Toast.LENGTH_SHORT).show()
            finish()
        }
        val t = Theme.resolve(this, prefs.themeMode, prefs.accent)
        editor = LayoutEditorView(this).apply {
            theme = t
            hintOf = prefs::hintOf
            onLayoutChanged = { l ->
                status.text = l.validate() ?: "Bố cục hợp lệ"
                updateSaveButton(l)
                markChecked(l)
            }
            layout = saved
        }
        showPresets()
        col.add(status)
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        col.add(saveButton, dp(4), dp(4))
        col.addButton("Lưu thành preset…") { askPresetName() }
        col.add(TextView(this).apply {
            text = "Preset (chạm để nạp vào lưới, bấm Lưu để dùng)"
            setTypeface(typeface, Typeface.BOLD)
        }, topMargin = dp(16))
        col.add(presetList)
    }

    private fun updateSaveButton(l: LayoutModel) {
        val changed = l.rows != saved.rows
        saveButton.isEnabled = changed
        saveButton.alpha = if (changed) 1f else 0.4f
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
     * Preset có sẵn rồi preset đã lưu, dấu ✓ ở preset trùng lưới đang sửa.
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
            val b = settingsButton(context, label(name, layout, editor.layout)) { editor.layout = layout }
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
}
