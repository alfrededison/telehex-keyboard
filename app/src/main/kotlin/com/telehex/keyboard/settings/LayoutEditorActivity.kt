package com.telehex.keyboard.settings

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowInsets
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
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
        val t = Theme.resolve(this, prefs.themeMode)
        editor = LayoutEditorView(this).apply {
            theme = t
            hintOf = prefs::hintOf
            onLayoutChanged = { l -> status.text = l.validate() ?: "Bố cục hợp lệ" }
            layout = prefs.layout
        }
        col.add(status)
        root.addView(editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        col.add(Button(this).apply {
            text = "Lưu"
            setOnClickListener {
                val err = editor.layout.validate()
                if (err != null) { Toast.makeText(context, err, Toast.LENGTH_LONG).show(); return@setOnClickListener }
                prefs.layout = editor.layout
                Toast.makeText(context, "Đã lưu bố cục", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        col.add(Button(this).apply {
            text = "Khôi phục mặc định"
            setOnClickListener { editor.layout = LayoutModel.DEFAULT }
        })
        col.add(Button(this).apply {
            text = "Lưu thành preset…"
            setOnClickListener {
                val input = EditText(context).apply { hint = "Tên preset" }
                AlertDialog.Builder(context).setTitle("Lưu preset").setView(input)
                    .setPositiveButton("Lưu") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotEmpty()) prefs.presets = prefs.presets + (name to editor.layout)
                    }
                    .setNegativeButton("Huỷ", null).show()
            }
        })
        col.add(Button(this).apply {
            text = "Nạp preset…"
            setOnClickListener {
                val names = prefs.presets.keys.sorted()
                if (names.isEmpty()) { Toast.makeText(context, "Chưa có preset nào", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                AlertDialog.Builder(context).setTitle("Nạp preset")
                    .setItems(names.toTypedArray()) { _, i -> editor.layout = prefs.presets.getValue(names[i]) }
                    .setNeutralButton("Xoá preset…") { _, _ ->
                        AlertDialog.Builder(context).setTitle("Xoá preset")
                            .setItems(names.toTypedArray()) { _, i -> prefs.presets = prefs.presets - names[i] }
                            .show()
                    }
                    .show()
            }
        })
    }

    private fun LinearLayout.add(v: android.view.View) =
        addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

    private fun dp(v: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
}
