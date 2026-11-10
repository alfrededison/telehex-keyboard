package com.telehex.keyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.telehex.keyboard.R
import com.telehex.keyboard.layout.KeyGeometry
import kotlin.math.min

/**
 * Các phím sửa văn bản của [EditPanel], vẽ thành lưới lục giác giống bàn phím chính, mỗi phím một icon.
 *
 * ```
 *   ABC   ↑   Chọn từ  Chọn hết
 *     ←   →    Cắt    Chép
 *   Đầu   ↓   Cuối    Dán
 *     ↶   ↷  Profile  ⌫
 * ```
 * Bốn mũi tên xếp thành hình thoi; nhóm chọn / clipboard tô màu nhấn; nút đổi profile tô màu phím dấu thanh.
 * Giữ mũi tên hoặc ⌫ để lặp.
 */
class EditPadView(context: Context, private val theme: Theme) : View(context) {

    var onAction: ((String) -> Unit)? = null
    var hapticEnabled = true
    var soundEnabled = false
    /** Giữ bao lâu (ms) thì mũi tên / ⌫ bắt đầu lặp; lấy từ ngưỡng long-press trong Cài đặt. */
    var longPressMs = 400L

    private class Pad(val action: String, val icon: Int, val caption: String, val kind: KeyKind, val r: Int, val c: Int, val repeat: Boolean = false)

    private val pads = listOf(
        Pad("abc", R.drawable.ic_edit_keyboard, "ABC", KeyKind.ENTER, 0, 0),
        Pad("up", R.drawable.ic_edit_arrow_up, "", KeyKind.CONSONANT, 0, 1, repeat = true),
        Pad("selectWord", R.drawable.ic_edit_select_word, "Chọn từ", KeyKind.VOWEL, 0, 2),
        Pad("selectAll", R.drawable.ic_edit_select_all, "Chọn hết", KeyKind.VOWEL, 0, 3),
        Pad("left", R.drawable.ic_edit_arrow_left, "", KeyKind.CONSONANT, 1, 0, repeat = true),
        Pad("right", R.drawable.ic_edit_arrow_right, "", KeyKind.CONSONANT, 1, 1, repeat = true),
        Pad("cut", R.drawable.ic_edit_cut, "Cắt", KeyKind.VOWEL, 1, 2),
        Pad("copy", R.drawable.ic_edit_copy, "Chép", KeyKind.VOWEL, 1, 3),
        Pad("home", R.drawable.ic_edit_line_start, "Đầu", KeyKind.CONSONANT, 2, 0),
        Pad("down", R.drawable.ic_edit_arrow_down, "", KeyKind.CONSONANT, 2, 1, repeat = true),
        Pad("end", R.drawable.ic_edit_line_end, "Cuối", KeyKind.CONSONANT, 2, 2),
        Pad("paste", R.drawable.ic_edit_paste, "Dán", KeyKind.VOWEL, 2, 3),
        Pad("undo", R.drawable.ic_edit_undo, "Hoàn tác", KeyKind.FUNCTION, 3, 0),
        Pad("redo", R.drawable.ic_edit_redo, "Làm lại", KeyKind.FUNCTION, 3, 1),
        Pad("profile", R.drawable.ic_edit_profile, "Profile", KeyKind.TONE, 3, 2),
        Pad("backspace", R.drawable.ic_edit_backspace, "", KeyKind.FUNCTION, 3, 3, repeat = true),
    )

    private val icons: Map<Pad, Drawable> = pads.associateWith { p ->
        context.getDrawable(p.icon)!!.mutate().apply { setTint(theme.fgFor(p.kind)) }
    }

    private class Drawn(val path: Path, val center: PointF)
    private var drawn: Map<Pad, Drawn> = emptyMap()
    private var keyW = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val gap = dp(3f)

    private val audio by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val handler = Handler(Looper.getMainLooper())
    private var pressed: Pad? = null
    private var repeated = false
    private val repeatRunnable = object : Runnable {
        override fun run() {
            val p = pressed ?: return
            repeated = true
            fire(p, feedback = true)
            handler.postDelayed(this, 50)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // 4 cột lệch nửa ô xen kẽ: rộng 4.5·W, cao 3·P + H = 3.75·W (P = 0.75·H, H = W·2/√3 ≈ 1.155·W)
        val usableW = w - 2 * gap
        val usableH = h - 2 * gap
        keyW = min(usableW / 4.5f, usableH / (3 * 0.75f * 2 / KeyGeometry.SQRT3 + 2 / KeyGeometry.SQRT3))
        val hexH = keyW * 2 / KeyGeometry.SQRT3
        val rowP = 0.75f * hexH
        val ox = (w - 4.5f * keyW) / 2
        val oy = (h - (3 * rowP + hexH)) / 2
        drawn = pads.associateWith { p ->
            val cx = ox + ((if (p.r % 2 == 1) 0.5f else 0f) + p.c + 0.5f) * keyW
            val cy = oy + p.r * rowP + hexH / 2
            Drawn(hexPath(cx, cy), PointF(cx, cy))
        }
    }

    private fun hexPath(cx: Float, cy: Float): Path {
        val iw = keyW - gap
        val ih = iw * 2f / KeyGeometry.SQRT3
        return Path().apply {
            moveTo(cx, cy - ih / 2)
            lineTo(cx + iw / 2, cy - ih / 4)
            lineTo(cx + iw / 2, cy + ih / 4)
            lineTo(cx, cy + ih / 2)
            lineTo(cx - iw / 2, cy + ih / 4)
            lineTo(cx - iw / 2, cy - ih / 4)
            close()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val captionMax = keyW * 0.82f
        for (p in pads) {
            val d = drawn[p] ?: continue
            fill.color = theme.bgFor(p.kind)
            canvas.drawPath(d.path, fill)
            if (p == pressed) {
                fill.color = theme.pressedOverlay
                canvas.drawPath(d.path, fill)
            }
            val hasCaption = p.caption.isNotEmpty()
            val size = (keyW * (if (hasCaption) 0.36f else 0.44f)).toInt()
            val iconCy = d.center.y - (if (hasCaption) keyW * 0.1f else 0f)
            val left = (d.center.x - size / 2f).toInt()
            val top = (iconCy - size / 2f).toInt()
            icons[p]!!.apply { setBounds(left, top, left + size, top + size); draw(canvas) }
            if (hasCaption) {
                captionPaint.color = theme.fgFor(p.kind)
                captionPaint.textSize = sp(10f)
                val tw = captionPaint.measureText(p.caption)
                if (tw > captionMax) captionPaint.textSize *= captionMax / tw
                canvas.drawText(p.caption, d.center.x, iconCy + size / 2f + sp(11f), captionPaint)
            }
        }
    }

    /** Ô có tâm gần nhất (Voronoi của lưới lục giác), trong bán kính một ô. */
    private fun padAt(x: Float, y: Float): Pad? {
        var best: Pad? = null
        var bestD = keyW * keyW
        for ((p, d) in drawn) {
            val dx = x - d.center.x; val dy = y - d.center.y
            val dist = dx * dx + dy * dy
            if (dist < bestD) { bestD = dist; best = p }
        }
        return best
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val p = padAt(event.x, event.y) ?: return false
                pressed = p
                repeated = false
                feedback()
                if (p.repeat) handler.postDelayed(repeatRunnable, longPressMs)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val p = pressed ?: return true
                if (padAt(event.x, event.y) != p) cancel()
            }
            MotionEvent.ACTION_UP -> {
                val p = pressed
                cancel()
                if (p != null && !repeated) fire(p, feedback = false)
            }
            // Ngón thứ hai chạm vào thì bỏ phím đang nhấn, để lặp không chạy mãi khi nhấc ngón đầu
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> cancel()
        }
        return true
    }

    private fun cancel() {
        handler.removeCallbacks(repeatRunnable)
        pressed = null
        invalidate()
    }

    private fun fire(p: Pad, feedback: Boolean) {
        if (feedback) feedback()
        onAction?.invoke(p.action)
    }

    private fun feedback() {
        if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (soundEnabled) audio.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.5f)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancel()
    }

    private fun dp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)
}
