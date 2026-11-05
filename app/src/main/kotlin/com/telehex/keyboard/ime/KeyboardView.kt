package com.telehex.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.telehex.keyboard.layout.HexGeometry

/**
 * Bàn phím lục giác vẽ bằng Canvas với `Path` dựng sẵn cho mỗi phím, cache lại khi kích thước đổi.
 */
class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onKeyDown(key: Key)
        fun onKeyMove(key: Key, dx: Float, dy: Float, dt: Long)
        fun onKeyUp(key: Key, gesture: Gesture, dx: Float, dy: Float, dt: Long)
        fun onKeyCancel(key: Key)
    }

    var listener: Listener? = null

    var theme: Theme = Theme.LIGHT
        set(value) { field = value; invalidate() }

    var keys: List<Key> = emptyList()
        set(value) { field = value; rebuild(); invalidate() }

    /** Phím đang mờ đi (ví dụ chế độ con trỏ). */
    var dimmed: Boolean = false
        set(value) { field = value; invalidate() }

    var hapticEnabled: Boolean = true

    private class Drawn(val key: Key, val path: Path, val center: PointF)

    private var geometry: HexGeometry? = null
    private var drawn: List<Drawn> = emptyList()
    private val cellToKey = HashMap<Pair<Int, Int>, Key>()
    private val pressed = HashMap<Int, Key>()

    private val padPx = dp(8f)
    private val topPx = dp(6f)
    private val bottomPx = dp(6f)
    private val gapPx = dp(3f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val gestures = GestureDetector(threshold = dp(24f))

    private val gestureListener = object : GestureDetector.Listener {
        override fun onDown(pointerId: Int, key: Key?) {
            key ?: return
            pressed[pointerId] = key
            if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
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
            listener?.onKeyUp(key, gesture, dx, dy, dt)
        }

        override fun onCancel(pointerId: Int) {
            pressed.remove(pointerId)?.let { listener?.onKeyCancel(it) }
            invalidate()
        }
    }

    /** Chiều cao lưới hiện tại (px), dùng cho các bảng thay thế lưới (emoji, dialpad). */
    val gridHeightPx: Int get() = (topPx + (geometry?.gridHeight ?: 0f) + bottomPx).toInt()

    fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val g = HexGeometry(w.toFloat(), padPx, topPx, gapPx)
        val h = (topPx + g.gridHeight + bottomPx).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        geometry = HexGeometry(w.toFloat(), padPx, topPx, gapPx)
        rebuild()
    }

    private fun rebuild() {
        val g = geometry ?: return
        cellToKey.clear()
        drawn = keys.map { key ->
            for (cell in key.cells) cellToKey[cell] = key
            if (key.isSpace) Drawn(key, g.spacePath(), g.spaceCenter())
            else {
                val (r, c) = key.cells.first()
                Drawn(key, g.hexPath(r, c), g.center(r, c))
            }
        }
    }

    fun keyAt(x: Float, y: Float): Key? {
        val g = geometry ?: return null
        val cell = g.cellAt(x, y) ?: return null
        return cellToKey[cell]
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val pressedKeys = pressed.values
        val labelSize = sp(20f)
        val smallSize = sp(14f)
        for (d in drawn) {
            val k = d.key
            fill.color = theme.bgFor(k.kind)
            if (dimmed) fill.alpha = 110
            canvas.drawPath(d.path, fill)
            if (k in pressedKeys) {
                fill.color = theme.pressedOverlay
                canvas.drawPath(d.path, fill)
            }
            val fg = theme.fgFor(k.kind)
            labelPaint.color = fg
            labelPaint.textSize = when {
                k.kind == KeyKind.FUNCTION && k.label.length > 1 -> smallSize
                k.kind == KeyKind.ENTER && k.label.length > 1 -> smallSize
                k.kind == KeyKind.SYMBOL -> labelSize
                else -> labelSize
            }
            val cx = d.center.x
            val cy = d.center.y
            val hasSub = k.sub.isNotEmpty()
            val hasHint = k.hint.isNotEmpty()
            if (k.isSpace) {
                subPaint.color = fg; subPaint.textSize = sp(11f)
                canvas.drawText(k.sub, cx, cy + sp(4f), subPaint)
                continue
            }
            // Nhãn chính; nếu có nhãn phụ (dấu) thì nhích lên một chút
            val labelY = cy + labelPaint.textSize * 0.36f - (if (hasSub) sp(4f) else 0f)
            canvas.drawText(k.label, cx, labelY, labelPaint)
            if (hasSub) {
                subPaint.color = fg; subPaint.textSize = sp(11f)
                canvas.drawText(k.sub, cx, labelY + sp(12f), subPaint)
            }
            if (hasHint) {
                hintPaint.color = theme.hintFg; hintPaint.textSize = sp(9f)
                canvas.drawText(k.hint, cx, cy - labelPaint.textSize * 0.7f - sp(1f), hintPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean =
        gestures.onTouch(event, ::keyAt, gestureListener)
}
