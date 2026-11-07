package com.telehex.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.media.AudioManager
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
import com.telehex.telex.predict.GlideDecoder.Point
import kotlin.math.hypot

/**
 * Bàn phím lục giác vẽ bằng Canvas với `Path` dựng sẵn cho mỗi phím, cache lại khi kích thước đổi.
 */
open class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        fun onKeyDown(key: Key)
        fun onKeyMove(key: Key, dx: Float, dy: Float, dt: Long)
        fun onKeyUp(key: Key, gesture: Gesture, dx: Float, dy: Float, dt: Long)
        fun onKeyCancel(key: Key)
        fun onMultiTouchStart() {}
        fun onTwoFinger(gesture: Gesture) {}
        fun onGlide(points: List<Point>, centers: Map<Char, Point>) {}
        /** Giữ đủ lâu: [key] nhấc lên, sẵn sàng kéo sang phím khác. */
        fun onKeyLift(key: Key) {}
        /** Kéo [from] thả lên [to]: đổi chỗ hai phím. Không phím nào được gõ. */
        fun onKeySwap(from: Key, to: Key) {}
    }

    var listener: Listener? = null

    /** Chỉ bật ở lớp chữ tiếng Việt, ngoài ô mật khẩu/tìm emoji. */
    var canGlide: (() -> Boolean)? = null
    private var glide: GlideTrace? = null
    private var glidePointer = -1
    private var glideLast: Point? = null
    private val glidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun cancelGlide() {
        glide = null
        glidePointer = -1
        glideLast = null
        invalidate()
    }

    var theme: Theme = Theme.LIGHT
        set(value) { field = value; invalidate() }

    var keys: List<Key> = emptyList()
        set(value) { field = value; rebuild(); invalidate() }

    /** Nhãn chữ cái viết hoa (chữ sắp gõ sẽ hoa) hay thường, như Gboard. */
    var shifted: Boolean = true
        set(value) { if (field != value) { field = value; invalidate() } }

    /** Đang khoá chữ hoa (vuốt lên phím cách): phím cách ghi "⇪ HOA" thay cho "cách". */
    var capsLock: Boolean = false
        set(value) { if (field != value) { field = value; invalidate() } }

    /** Chế độ EN (Telex tắt): phím cách ghi thêm "EN". */
    var english: Boolean = false
        set(value) { if (field != value) { field = value; invalidate() } }

    /** Phím đang mờ đi (ví dụ chế độ con trỏ). */
    var dimmed: Boolean = false
        set(value) { field = value; invalidate() }

    /** 0 = tắt, 1 = dồn trái, 2 = dồn phải (lưới ~80%). */
    var oneHanded: Int = 0
        set(value) { field = value; if (width > 0) { geometry = makeGeometry(width.toFloat()); rebuild(); invalidate() } }

    private fun makeGeometry(w: Float) = HexGeometry(w, padPx, topPx, gapPx, if (oneHanded == 0) 1f else 0.8f, oneHanded == 2)

    var hapticEnabled: Boolean = true
    var soundEnabled: Boolean = false
    private val audio by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

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

    private val downPoints = HashMap<Int, FloatArray>()

    private val gestureListener = object : GestureDetector.Listener {
        override fun onDown(pointerId: Int, key: Key?) {
            key ?: return
            pressed[pointerId] = key
            lastDown?.let { downPoints[pointerId] = it }
            if (gestures.activeCount == 1 && canLift?.invoke(key) == true) {
                drag = Drag(pointerId, key, lastDown?.get(0) ?: 0f, lastDown?.get(1) ?: 0f)
                postDelayed(liftKey, dragHoldMs)
            }
            if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            if (soundEnabled) audio.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.5f)
            listener?.onKeyDown(key)
            if (gestures.activeCount == 1 && key.isLetter && canGlide?.invoke() == true) {
                val down = lastDown
                val w = geometry?.w
                if (down != null && w != null && w > 0) {
                    val p = Point(down[0] / w, down[1] / w)
                    glide = GlideTrace(p, key.code[0])
                    glidePointer = pointerId
                    glideLast = p
                }
            }
            invalidate()
        }

        override fun onMove(pointerId: Int, key: Key?, dx: Float, dy: Float, dt: Long) {
            key ?: return
            listener?.onKeyMove(key, dx, dy, dt)
        }

        override fun onUp(pointerId: Int, key: Key?, gesture: Gesture, dx: Float, dy: Float, dt: Long) {
            pressed.remove(pointerId)
            invalidate()
            val down = downPoints.remove(pointerId)
            key ?: return
            if (gesture == Gesture.TAP && down != null) learnOffset(key, down[0], down[1])
            listener?.onKeyUp(key, gesture, dx, dy, dt)
        }

        override fun onCancel(pointerId: Int) {
            downPoints.remove(pointerId)
            pressed.remove(pointerId)?.let { listener?.onKeyCancel(it) }
            invalidate()
        }

        override fun onMultiTouchStart() { cancelDrag(); cancelGlide(); listener?.onMultiTouchStart() }
        override fun onTwoFinger(gesture: Gesture) { listener?.onTwoFinger(gesture) }
    }

    /**
     * Giữ lâu một phím rồi kéo để đổi chỗ ngay trên bàn phím: trả true cho phím nhấc lên được (phím ký tự).
     * null = tắt.
     */
    var canLift: ((Key) -> Boolean)? = null

    /** Phím thả lên được (đổi chỗ với phím đang kéo), kể cả phím chức năng. */
    var canDrop: (Key) -> Boolean = { true }

    /** Giữ yên bao lâu (ms) thì phím nhấc lên. */
    var dragHoldMs: Long = 800L

    /**
     * Phím đang giữ chờ nhấc ([lifted] = false) hoặc đã nhấc và đang kéo. [left]: ngón đã rời phím gốc;
     * chưa rời thì nhả ra vẫn là long-press bình thường của phím đó.
     */
    private class Drag(val pointerId: Int, val key: Key, val downX: Float, val downY: Float) {
        var lifted = false
        var left = false
        var x = downX
        var y = downY
        var over: Key? = null
    }

    private var drag: Drag? = null

    /**
     * Đã huỷ một phím đang kéo vì ngón thứ hai chạm xuống: bỏ qua mọi sự kiện tới khi nhấc hết ngón, để ngón
     * thứ hai không thành một lần chạm một ngón mới (gõ phím, giữ phím cách di con trỏ, nhấc phím khác).
     */
    private var swallowUntilUp = false

    private val liftKey = Runnable {
        val d = drag ?: return@Runnable
        if (gestures.activeCount != 1) { drag = null; return@Runnable }
        d.lifted = true
        cancelGlide()
        parent?.requestDisallowInterceptTouchEvent(true)
        if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        listener?.onKeyLift(d.key)
        invalidate()
    }

    /** Bỏ phím đang chờ nhấc hoặc đang kéo; không đổi chỗ gì. */
    private fun cancelDrag() {
        removeCallbacks(liftKey)
        if (drag?.lifted == true) invalidate()
        drag = null
    }

    /**
     * Phím đang chờ nhấc: vuốt đi hay chạm thêm ngón thì huỷ. Phím đã nhấc: tự xử lý pointer của nó thay cho
     * [gestures]. @return true nếu đã xử lý sự kiện (không chuyển cho [gestures]).
     */
    private fun handleDrag(event: MotionEvent): Boolean {
        val d = drag ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Ngón thứ hai chạm xuống: huỷ kéo. Chưa nhấc thì để [gestures] xử lý hai ngón như thường;
                // đã nhấc thì phím đó không được gõ và cả cử chỉ bị bỏ
                cancelDrag()
                if (!d.lifted) return false
                abandonPointer(d)
                swallowUntilUp = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val i = event.findPointerIndex(d.pointerId)
                if (i < 0) return false
                val x = event.getX(i); val y = event.getY(i)
                if (!d.lifted) {
                    // Vuốt đi trước khi kịp nhấc: là cử chỉ vuốt, không kéo
                    if (hypot(x - d.downX, y - d.downY) > gestures.threshold) cancelDrag()
                    return false
                }
                d.x = x; d.y = y
                val k = keyAt(x, y)
                if (k != d.key) d.left = true
                d.over = k?.takeIf { it != d.key && canDrop(it) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) != d.pointerId) return false
                if (!d.lifted || !d.left) { cancelDrag(); return false }
                val to = d.over
                abandonPointer(d)
                cancelDrag()
                if (to != null) listener?.onKeySwap(d.key, to)
                return true
            }
            MotionEvent.ACTION_CANCEL -> { cancelDrag(); return false }
        }
        return false
    }

    /** Pointer của phím đã nhấc không còn là một lần gõ: [gestures] quên nó, bỏ tô "đang nhấn". */
    private fun abandonPointer(d: Drag) {
        gestures.drop(d.pointerId)
        downPoints.remove(d.pointerId)
        pressed.remove(d.pointerId)?.let { listener?.onKeyCancel(it) }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelDrag()
        cancelGlide()
        super.onDetachedFromWindow()
    }

    /** Ngưỡng vuốt (px) và long-press (ms), đặt từ Cài đặt. */
    fun setThresholds(swipePx: Float, longPressMs: Long) {
        gestures.threshold = swipePx
        gestures.longPressMs = longPressMs
    }

    /** Khoảng cách từ tâm phím cách tới mép trái lưới (px); âm. */
    fun spaceLeftEdgeDx(): Float {
        val c = centerOf(keys.firstOrNull { it.isSpace } ?: return -Float.MAX_VALUE) ?: return -Float.MAX_VALUE
        return (geometry?.pad ?: padPx) - c.x
    }

    /** Chiều cao lưới hiện tại (px), dùng cho các bảng thay thế lưới (emoji, dialpad). */
    val gridHeightPx: Int get() = (topPx + (geometry?.gridHeight ?: 0f) + bottomPx).toInt()

    /** Chiều cao lưới sẽ có với bề rộng [widthPx], không cần đã đo. */
    fun expectedHeight(widthPx: Int): Int =
        (topPx + makeGeometry(widthPx.toFloat()).gridHeight + bottomPx).toInt()

    fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val g = makeGeometry(w.toFloat())
        val h = (topPx + g.gridHeight + bottomPx).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        geometry = makeGeometry(w.toFloat())
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

    /** Vùng chạm động: phím chữ hợp lệ tiếp theo được nới vùng. null = tắt. */
    var validNextLetter: ((Char) -> Boolean)? = null

    /** Học độ lệch chạm theo phím (px): mã phím → (dx, dy). null = tắt. */
    var touchOffsets: MutableMap<String, FloatArray>? = null
    var onTouchOffsetsChanged: (() -> Unit)? = null

    fun keyAt(x: Float, y: Float): Key? {
        val g = geometry ?: return null
        var px = x; var py = y
        val offsets = touchOffsets
        if (offsets != null && offsets.isNotEmpty()) {
            // Bù độ lệch chung trước, rồi tinh chỉnh theo phím gần nhất
            var sx = 0f; var sy = 0f
            for (o in offsets.values) { sx += o[0]; sy += o[1] }
            px -= sx / offsets.size; py -= sy / offsets.size
            val first = g.cellAt(px, py)?.let { cellToKey[it] }
            offsets[first?.code]?.let { o -> px = x - o[0]; py = y - o[1] }
        }
        val near = g.nearestCells(px, py)
        if (near.isEmpty()) return null
        val best = cellToKey[near[0].first] ?: return null
        val check = validNextLetter
        if (check != null && near.size == 2 && near[1].second < near[0].second * 1.8f) {
            val second = cellToKey[near[1].first]
            if (second != null && second.isLetter && best.isLetter &&
                !check(best.code[0]) && check(second.code[0])) return second
        }
        return best
    }

    private fun learnOffset(key: Key, x: Float, y: Float) {
        val offsets = touchOffsets ?: return
        if (!key.isLetter) return
        val c = centerOf(key) ?: return
        val o = offsets.getOrPut(key.code) { floatArrayOf(0f, 0f) }
        val alpha = 0.08f
        val dx = (x - c.x).coerceIn(-w40(), w40()); val dy = (y - c.y).coerceIn(-w40(), w40())
        o[0] += alpha * (dx - o[0]); o[1] += alpha * (dy - o[1])
        onTouchOffsetsChanged?.invoke()
    }

    private fun w40(): Float = (geometry?.w ?: dp(50f)) * 0.4f

    /** Các phím được tô "đang nhấn" thêm từ ngoài (editor). */
    var highlighted: Set<Key> = emptySet()
        set(value) { field = value; invalidate() }

    /** Tâm của một phím đã dựng (px), hoặc null. */
    fun centerOf(key: Key): PointF? = drawn.firstOrNull { it.key == key }?.center

    /** Hook vẽ thêm lên trên lưới (ví dụ phím đang kéo). */
    protected open fun drawOverlay(canvas: Canvas) {}

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(theme.background)
        val lifted = drag?.takeIf { it.lifted }
        val pressedKeys = pressed.values + highlighted + listOfNotNull(lifted?.over)
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
                val spaceLabel = if (capsLock) "⇪ HOA" else k.sub
                canvas.drawText(if (english) "$spaceLabel · EN" else spaceLabel, cx, cy + sp(4f), subPaint)
                continue
            }
            // Nhãn chính; nếu có nhãn phụ hoặc ký tự vuốt xuống ở dưới thì nhích lên một chút
            val labelY = cy + labelPaint.textSize * 0.36f - (if (hasSub || hasHint) sp(4f) else 0f)
            canvas.drawText(if (k.isLetter && !shifted) k.code else k.label, cx, labelY, labelPaint)
            if (hasSub) {
                subPaint.color = fg; subPaint.textSize = sp(11f)
                canvas.drawText(k.sub, cx, labelY + sp(12f), subPaint)
            } else if (hasHint) {
                // Vuốt xuống thì ký tự nằm ở dưới, đúng hướng ngón tay
                hintPaint.color = theme.hintFg; hintPaint.textSize = sp(11f)
                canvas.drawText(k.hint, cx, labelY + sp(14f), hintPaint)
            }
        }
        if (lifted != null) drawFloatingKey(canvas, lifted.key, lifted.x, lifted.y)
        glide?.takeIf { it.active }?.let { trace ->
            val w = geometry?.w ?: return@let
            val path = Path()
            trace.points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(p.x * w, p.y * w) else path.lineTo(p.x * w, p.y * w)
            }
            glidePaint.color = theme.chipActiveBg
            glidePaint.alpha = 190
            glidePaint.strokeWidth = dp(5f)
            canvas.drawPath(path, glidePaint)
        }
        drawOverlay(canvas)
    }

    /** Phím đang kéo vẽ nổi phía trên ngón tay để ngón không che nhãn. */
    protected fun drawFloatingKey(canvas: Canvas, key: Key, x: Float, y: Float) {
        val cy = y - dp(40f)
        fill.color = theme.bgFor(key.kind)
        canvas.drawCircle(x, cy, dp(26f), fill)
        labelPaint.color = theme.fgFor(key.kind)
        labelPaint.textSize = sp(28f)
        canvas.drawText(if (key.isLetter && !shifted) key.code else key.label, x, cy + labelPaint.textSize * 0.36f, labelPaint)
    }

    private var lastDown: FloatArray? = null

    private fun handleGlide(event: MotionEvent): Boolean {
        val trace = glide ?: return false
        val a = event.actionMasked
        if (a == MotionEvent.ACTION_POINTER_DOWN || a == MotionEvent.ACTION_CANCEL) {
            val consumed = trace.active
            cancelGlide()
            if (consumed && a == MotionEvent.ACTION_POINTER_DOWN) swallowUntilUp = true
            return consumed
        }
        if (a != MotionEvent.ACTION_MOVE && a != MotionEvent.ACTION_UP) return false
        val i = event.findPointerIndex(glidePointer)
        val w = geometry?.w ?: return false
        if (i < 0 || w <= 0) return false
        val wasActive = trace.active
        fun sample(x: Float, y: Float) {
            val point = Point(x / w, y / w)
            val previous = glideLast ?: point
            // Nối các mẫu xa nhau để không bỏ phím khi Android gộp sự kiện MOVE.
            val steps = (previous.distance(point) / 0.15f).toInt().coerceIn(1, 128)
            for (step in 1..steps) {
                val t = step.toFloat() / steps
                val p = Point(previous.x + (point.x - previous.x) * t, previous.y + (point.y - previous.y) * t)
                // Dùng hình học thật, không dùng vùng chạm động dành cho gõ Telex từng phím.
                val key = geometry?.cellAt(p.x * w, p.y * w)?.let { cellToKey[it] }
                trace.add(p, key?.takeIf { it.isLetter }?.code?.first())
            }
            glideLast = point
        }
        for (h in 0 until event.historySize) sample(event.getHistoricalX(i, h), event.getHistoricalY(i, h))
        sample(event.getX(i), event.getY(i))
        if (trace.active && !wasActive) {
            cancelDrag()
            gestures.drop(glidePointer)
            downPoints.remove(glidePointer)
            pressed.remove(glidePointer)?.let { listener?.onKeyCancel(it) }
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val consumed = trace.active
        if (a == MotionEvent.ACTION_UP) {
            val points = trace.points.toList()
            val centers = drawn.filter { it.key.isLetter }.associate {
                it.key.code[0] to Point(it.center.x / w, it.center.y / w)
            }
            cancelGlide()
            if (consumed) listener?.onGlide(points, centers)
        }
        if (consumed) invalidate()
        return consumed
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val a = event.actionMasked
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_POINTER_DOWN) {
            val i = event.actionIndex
            lastDown = floatArrayOf(event.getX(i), event.getY(i))
        }
        if (a == MotionEvent.ACTION_DOWN) swallowUntilUp = false // cử chỉ mới: không để cờ cũ kẹt lại
        if (swallowUntilUp) {
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) swallowUntilUp = false
            return true
        }
        if (handleDrag(event)) return true
        if (handleGlide(event)) return true
        return gestures.onTouch(event, ::keyAt, gestureListener)
    }
}
