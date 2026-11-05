package com.telehex.keyboard.ime

import android.view.MotionEvent

enum class Gesture { TAP, SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT, LONG_PRESS }

/**
 * Nhận diện cử chỉ theo từng pointer: ghi điểm `down`, quyết định khi `up`.
 * Ngưỡng [threshold] (px) và phải theo hướng trội (|dy| > |dx| hoặc ngược lại).
 */
class GestureDetector(
    private val threshold: Float,
    private val longPressMs: Long = 450L,
) {
    class Pointer(val key: Key?, val x: Float, val y: Float, val t: Long) {
        var lastX = x
        var lastY = y
    }

    interface Listener {
        fun onDown(pointerId: Int, key: Key?)
        fun onMove(pointerId: Int, key: Key?, dx: Float, dy: Float, dt: Long)
        fun onUp(pointerId: Int, key: Key?, gesture: Gesture, dx: Float, dy: Float, dt: Long)
        fun onCancel(pointerId: Int)
    }

    private val pointers = HashMap<Int, Pointer>()

    val activeCount: Int get() = pointers.size

    fun onTouch(event: MotionEvent, keyAt: (Float, Float) -> Key?, listener: Listener): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val id = event.getPointerId(i)
                val x = event.getX(i); val y = event.getY(i)
                val key = keyAt(x, y)
                pointers[id] = Pointer(key, x, y, event.eventTime)
                listener.onDown(id, key)
            }
            MotionEvent.ACTION_MOVE -> {
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
                listener.onUp(id, p.key, classify(dx, dy, dt), dx, dy, dt)
            }
            MotionEvent.ACTION_CANCEL -> {
                for (id in pointers.keys.toList()) listener.onCancel(id)
                pointers.clear()
            }
        }
        return true
    }

    fun classify(dx: Float, dy: Float, dt: Long): Gesture {
        val ax = kotlin.math.abs(dx); val ay = kotlin.math.abs(dy)
        return when {
            ay > threshold && ay > ax -> if (dy < 0) Gesture.SWIPE_UP else Gesture.SWIPE_DOWN
            ax > threshold && ax > ay -> if (dx < 0) Gesture.SWIPE_LEFT else Gesture.SWIPE_RIGHT
            dt >= longPressMs -> Gesture.LONG_PRESS
            else -> Gesture.TAP
        }
    }
}
