package com.telehex.keyboard.ime

import android.view.MotionEvent
import kotlin.math.abs

enum class Gesture { TAP, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT, LONG_PRESS }

/**
 * Nhận diện cử chỉ theo từng pointer: ghi điểm `down`, quyết định khi `up`.
 * Ngưỡng [threshold] (px) và phải theo hướng trội (|dy| > |dx| hoặc ngược lại).
 *
 * Hai ngón: khi có 2 pointer cùng lúc, các sự kiện `up` được giữ lại tới khi cả hai nhả.
 * Nếu cả hai cùng vuốt một hướng (trái/phải) hoặc cùng giữ yên, đó là cử chỉ hai ngón và
 * các phím riêng lẻ không được gõ; ngược lại (gõ nhanh hai ngón cái) từng phím được gõ theo thứ tự.
 */
class GestureDetector(
    var threshold: Float,
    var longPressMs: Long = 450L,
) {
    class Pointer(val key: Key?, val x: Float, val y: Float, val t: Long) {
        var lastX = x
        var lastY = y
    }

    private class Finished(val id: Int, val key: Key?, val gesture: Gesture, val dx: Float, val dy: Float, val dt: Long)

    interface Listener {
        fun onDown(pointerId: Int, key: Key?)
        fun onMove(pointerId: Int, key: Key?, dx: Float, dy: Float, dt: Long)
        fun onUp(pointerId: Int, key: Key?, gesture: Gesture, dx: Float, dy: Float, dt: Long)
        fun onCancel(pointerId: Int)
        /** Có pointer thứ hai chạm xuống: các cử chỉ một ngón đang dở nên huỷ. */
        fun onMultiTouchStart() {}
        /** Cử chỉ hai ngón: [Gesture.SWIPE_LEFT], [Gesture.SWIPE_RIGHT] hoặc [Gesture.LONG_PRESS]. */
        fun onTwoFinger(gesture: Gesture) {}
    }

    private val pointers = HashMap<Int, Pointer>()
    private var multi = false
    private val finished = ArrayList<Finished>(2)
    private var sequence = 0

    val activeCount: Int get() = pointers.size

    /** Bỏ theo dõi một pointer (view đã tự xử lý nó, ví dụ kéo đổi chỗ phím): `up` của nó bị bỏ qua. */
    fun drop(pointerId: Int) { pointers.remove(pointerId) }

    /** Kết thúc luồng chạm cũ khi đổi lớp, đóng bàn phím hoặc Android bắt đầu luồng mới. */
    fun cancel(listener: Listener) {
        val ids = (pointers.keys + finished.map { it.id }).toSet()
        pointers.clear(); finished.clear(); multi = false
        sequence++
        for (id in ids) listener.onCancel(id)
    }

    fun onTouch(event: MotionEvent, keyAt: (Float, Float) -> Key?, listener: Listener): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) cancel(listener)
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val x = event.getX(i); val y = event.getY(i)
                val key = keyAt(x, y)
                pointers[id] = Pointer(key, x, y, event.eventTime)
                if (pointers.size >= 2 && !multi) { multi = true; listener.onMultiTouchStart() }
                listener.onDown(id, key)
            }
            MotionEvent.ACTION_MOVE -> {
                if (multi) return true
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val p = pointers[id] ?: continue
                    p.lastX = event.getX(i); p.lastY = event.getY(i)
                    listener.onMove(id, p.key, p.lastX - p.x, p.lastY - p.y, event.eventTime - p.t)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val p = pointers.remove(id) ?: return true
                val dx = event.getX(i) - p.x
                val dy = event.getY(i) - p.y
                val dt = event.eventTime - p.t
                val g = classify(dx, dy, dt)
                if (!multi) { listener.onUp(id, p.key, g, dx, dy, dt); return true }
                finished.add(Finished(id, p.key, g, dx, dy, dt))
                if (pointers.isEmpty()) resolveMulti(listener)
            }
            MotionEvent.ACTION_CANCEL -> {
                cancel(listener)
            }
        }
        return true
    }

    private fun resolveMulti(listener: Listener) {
        // Callback có thể đổi lớp và huỷ luồng chạm. Tách danh sách trước khi gọi ra ngoài,
        // rồi bỏ các phím còn lại nếu lớp/cử chỉ đã đổi trong callback đầu tiên.
        val resolved = finished.toList()
        multi = false
        val current = sequence
        val gestures = resolved.map { it.gesture }.toSet()
        val two = resolved.size >= 2 && gestures.size == 1 &&
            gestures.first().let { it == Gesture.SWIPE_LEFT || it == Gesture.SWIPE_RIGHT || it == Gesture.LONG_PRESS }
        if (two) {
            for (f in resolved) {
                if (sequence != current) return
                finished.remove(f)
                listener.onCancel(f.id)
            }
            if (sequence == current) listener.onTwoFinger(gestures.first())
        } else {
            for (f in resolved) {
                if (sequence != current) break
                finished.remove(f)
                listener.onUp(f.id, f.key, f.gesture, f.dx, f.dy, f.dt)
            }
        }
    }

    fun classify(dx: Float, dy: Float, dt: Long): Gesture {
        val ax = abs(dx); val ay = abs(dy)
        return when {
            ay > threshold && ay > ax -> if (dy < 0) Gesture.SWIPE_UP else Gesture.SWIPE_DOWN
            ax > threshold && ax > ay -> if (dx < 0) Gesture.SWIPE_LEFT else Gesture.SWIPE_RIGHT
            dt >= longPressMs -> Gesture.LONG_PRESS
            else -> Gesture.TAP
        }
    }
}
