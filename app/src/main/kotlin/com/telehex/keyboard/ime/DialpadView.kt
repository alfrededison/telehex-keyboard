package com.telehex.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.telehex.keyboard.layout.KeyCode

/**
 * Dialpad: phím chữ nhật bo góc như các bàn phím số khác (không dùng lục giác).
 *
 * ```
 *  [ 1 ] [ 2 ] [ 3 ] [ - ]
 *  [ 4 ] [ 5 ] [ 6 ] [cách]
 *  [ 7 ] [ 8 ] [ 9 ] [ ⌫ ]
 *  [ABC] [ 0 ] [ . ] [ ↵ ]
 * ```
 * Vuốt xuống trên số ra ký tự phụ (hiện nhỏ phía trên số). Giữ ⌫ để xoá liên tục.
 */
class DialpadView(context: Context, private val theme: Theme, val heightPx: Int) : View(context) {

    var listener: KeyboardView.Listener? = null
    var onBackspaceRepeat: (() -> Unit)? = null
    var hapticEnabled = true
    var soundEnabled = false
    var enterLabel = "↵"
        set(value) { field = value; rebuild() }

    /** Ẩn các phím không hợp lệ với ô số thuần (không phải phone). */
    var allowMinus = true
        set(value) { field = value; rebuild() }
    var allowDot = true
        set(value) { field = value; rebuild() }

    private val audio by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val handler = Handler(Looper.getMainLooper())
    private var repeating = false
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (!repeating) return
            onBackspaceRepeat?.invoke()
            if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            handler.postDelayed(this, 50)
        }
    }

    private class Cell(val key: Key, val rect: RectF)
    private var cells: List<Cell> = emptyList()
    private val pressed = HashMap<Int, Key>()
    private val gestures = GestureDetector(threshold = dp(24f))

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val rows = listOf(
        listOf("1", "2", "3", "-"),
        listOf("4", "5", "6", KeyCode.SPACE),
        listOf("7", "8", "9", KeyCode.BACKSPACE),
        listOf(KeyCode.ABC, "0", ".", KeyCode.ENTER),
    )

    private val gestureListener = object : GestureDetector.Listener {
        override fun onDown(pointerId: Int, key: Key?) {
            key ?: return
            pressed[pointerId] = key
            if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (soundEnabled) audio.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.5f)
            if (key.code == KeyCode.BACKSPACE) {
                repeating = true
                handler.postDelayed(repeatRunnable, 400)
            }
            listener?.onKeyDown(key)
            invalidate()
        }

        override fun onMove(pointerId: Int, key: Key?, dx: Float, dy: Float, dt: Long) {
            key ?: return
            listener?.onKeyMove(key, dx, dy, dt)
        }

        override fun onUp(pointerId: Int, key: Key?, gesture: Gesture, dx: Float, dy: Float, dt: Long) {
            pressed.remove(pointerId)
            invalidate()
            key ?: return
            if (key.code == KeyCode.BACKSPACE) {
                val wasRepeating = repeating && dt >= 400
                stopRepeat()
                if (wasRepeating) return // đã xoá trong lúc giữ
            }
            listener?.onKeyUp(key, gesture, dx, dy, dt)
        }

        override fun onCancel(pointerId: Int) {
            stopRepeat()
            pressed.remove(pointerId)?.let { listener?.onKeyCancel(it) }
            invalidate()
        }
    }

    private fun stopRepeat() { repeating = false; handler.removeCallbacks(repeatRunnable) }

    init { rebuild() }

    private fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), heightPx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

    private fun rebuild() {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val pad = dp(8f); val gap = dp(6f)
        val weights = floatArrayOf(90f, 90f, 90f, 86f)
        val totalW = w - 2 * pad - 3 * gap
        val colW = weights.map { totalW * it / weights.sum() }
        val rowH = (h - 2 * pad - 3 * gap) / 4f
        val out = ArrayList<Cell>(16)
        var y = pad
        for ((r, row) in rows.withIndex()) {
            var x = pad
            for ((c, code) in row.withIndex()) {
                val rect = RectF(x, y, x + colW[c], y + rowH)
                x += colW[c] + gap
                val key = when (code) {
                    KeyCode.SPACE -> Key(code, listOf(r to c), "", sub = "cách", kind = KeyKind.SPACE)
                    KeyCode.BACKSPACE -> Key(code, listOf(r to c), "⌫", kind = KeyKind.FUNCTION)
                    KeyCode.ENTER -> Key(code, listOf(r to c), enterLabel, kind = KeyKind.ENTER)
                    KeyCode.ABC -> Key(code, listOf(r to c), "ABC", kind = KeyKind.FUNCTION)
                    "-" -> if (allowMinus) Key(code, listOf(r to c), "-", kind = KeyKind.FUNCTION) else null
                    "." -> if (allowDot) Key(code, listOf(r to c), ".", hint = ",", kind = KeyKind.CONSONANT) else null
                    else -> Key(code, listOf(r to c), code, hint = SUB[code] ?: "", kind = KeyKind.CONSONANT)
                }
                if (key != null) out.add(Cell(key, rect))
            }
            y += rowH + gap
        }
        cells = out
        invalidate()
    }

    private fun keyAt(x: Float, y: Float): Key? = cells.firstOrNull { it.rect.contains(x, y) }?.key

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val radius = dp(12f)
        for (cell in cells) {
            val k = cell.key
            fill.color = theme.bgFor(k.kind)
            canvas.drawRoundRect(cell.rect, radius, radius, fill)
            if (k in pressed.values) { fill.color = theme.pressedOverlay; canvas.drawRoundRect(cell.rect, radius, radius, fill) }
            val fg = theme.fgFor(k.kind)
            val cx = cell.rect.centerX(); val cy = cell.rect.centerY()
            if (k.isSpace) {
                hintPaint.color = fg; hintPaint.textSize = sp(12f)
                canvas.drawText("cách", cx, cy + sp(4f), hintPaint)
                continue
            }
            labelPaint.color = fg
            labelPaint.textSize = if (k.label.length > 1 && k.kind != KeyKind.CONSONANT) sp(15f) else sp(26f)
            canvas.drawText(k.label, cx, cy + labelPaint.textSize * 0.36f, labelPaint)
            if (k.hint.isNotEmpty()) {
                hintPaint.color = theme.hintFg; hintPaint.textSize = sp(11f)
                canvas.drawText(k.hint, cx, cell.rect.top + sp(13f), hintPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean = gestures.onTouch(event, ::keyAt, gestureListener)

    override fun onDetachedFromWindow() { stopRepeat(); super.onDetachedFromWindow() }

    companion object {
        /** Ký tự phụ khi vuốt xuống trên số. */
        val SUB: Map<String, String> = mapOf(
            "1" to "(", "2" to ")", "3" to "/", "4" to "*", "5" to "#",
            "6" to "-", "7" to ";", "8" to ":", "9" to "=", "0" to "+",
        )
    }
}
