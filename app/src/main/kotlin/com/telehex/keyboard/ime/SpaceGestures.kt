package com.telehex.keyboard.ime

import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Máy trạng thái cho các cử chỉ bắt đầu từ **phím cách** (mục 9.2):
 *
 * - chạm: khoảng trắng; vuốt trái ngắn: xoá 1 ký tự; vuốt trái dài: chọn-để-xoá theo từ
 *   (bôi đen xem trước, nhả thì xoá, kéo ngược để bớt, về gần điểm đầu thì huỷ, quá mép trái
 *   thì tới đầu dòng);
 * - giữ ≥ holdMs rồi kéo: di con trỏ (ngang theo ký tự, dọc theo dòng);
 * - chạm đúp, giữ lần hai rồi kéo: chọn văn bản;
 * - khi có vùng chọn: vuốt lên = chép, vuốt trái = cắt; vuốt phải = dán;
 * - không có vùng chọn: vuốt lên = đổi hoa/thường (khoá chữ hoa, hoặc bỏ tự viết hoa).
 */
class SpaceGestures(private val host: Host) {

    interface Host {
        val holdMs: Long
        val stepPx: Float
        val swipePx: Float
        val doubleTapMs: Long
        /** dx (âm) tại đó ngón tay đã ra quá mép trái bàn phím. */
        val leftEdgeDx: Float

        fun hasSelection(): Boolean
        fun cursorPosition(): Int
        fun textBeforeCursor(n: Int): CharSequence
        fun setSelection(start: Int, end: Int)
        fun detachComposing()
        fun deleteBackward()
        fun deleteSelection()
        fun commitSpace()
        /** Xoá khoảng trắng của lần chạm đầu (chạm đúp). @return true nếu đã xoá. */
        fun undoLastSpace(): Boolean
        fun moveCursor(dxChars: Int, dyLines: Int)
        fun copy()
        fun cut()
        fun paste()
        fun toggleCaps()
        fun toggleEnglish()
        fun setCursorMode(on: Boolean)
        fun haptic()
        fun onSelectionGestureEnd()
    }

    private enum class State { IDLE, PENDING, CURSOR, WORD_SELECT, DOUBLE_PENDING, SELECT }

    private var state = State.IDLE
    private val handler = Handler(Looper.getMainLooper())
    private var lastTapUpTime = 0L
    private var downTime = 0L
    private var moved = false

    // Con trỏ
    private var cursorStepsX = 0
    private var cursorStepsY = 0

    // Chọn-để-xoá / chọn
    private var anchor = 0
    /** Văn bản trước con trỏ lúc bắt đầu chọn; getTextBeforeCursor đổi theo vùng chọn nên chỉ đọc một lần. */
    private var anchorText: CharSequence = ""
    private var selectedWords = 0
    private var toLineStart = false
    private var selectChars = 0

    private val holdRunnable = Runnable {
        if (state == State.PENDING && !moved) {
            state = State.CURSOR
            cursorStepsX = 0; cursorStepsY = 0
            host.setCursorMode(true)
            host.haptic()
        }
    }

    fun onDown(now: Long) {
        cancelTimer()
        moved = false
        downTime = now
        state = if (now - lastTapUpTime <= host.doubleTapMs) State.DOUBLE_PENDING else State.PENDING
        if (state == State.PENDING) handler.postDelayed(holdRunnable, host.holdMs)
    }

    fun onMove(dx: Float, dy: Float) {
        val ax = abs(dx); val ay = abs(dy)
        if (!moved && (ax > host.swipePx || ay > host.swipePx)) moved = true
        when (state) {
            State.PENDING -> {
                if (!moved) return
                cancelTimer()
                // Có vùng chọn thì vuốt trái (dài hay ngắn) là cắt khi nhả, không chọn-để-xoá theo từ
                if (dx < 0 && ax > ay && ax > wordThreshold() && !host.hasSelection()) enterWordSelect(dx)
            }
            State.WORD_SELECT -> updateWordSelect(dx)
            State.CURSOR -> {
                val sx = (dx / (host.stepPx / 2f)).roundToInt()
                val sy = (dy / host.stepPx).roundToInt()
                if (sx != cursorStepsX) { host.moveCursor(sx - cursorStepsX, 0); cursorStepsX = sx; host.haptic() }
                if (sy != cursorStepsY) { host.moveCursor(0, sy - cursorStepsY); cursorStepsY = sy; host.haptic() }
            }
            State.DOUBLE_PENDING -> {
                if (!moved) return
                // Chọn chỉ kéo ngang; vuốt dọc ngay sau chạm cách là cử chỉ thường (vuốt lên = đổi hoa/thường)
                if (ay > ax) { state = State.PENDING; return }
                state = State.SELECT
                val removed = host.undoLastSpace()
                host.detachComposing()
                // onUpdateSelection đến trễ nên tự trừ ký tự vừa xoá
                anchor = host.cursorPosition() - (if (removed) 1 else 0)
                selectChars = 0
                host.setCursorMode(true)
                updateSelect(dx)
            }
            State.SELECT -> updateSelect(dx)
            State.IDLE -> {}
        }
    }

    fun onUp(gesture: Gesture, now: Long) {
        cancelTimer()
        val s = state
        state = State.IDLE
        when (s) {
            State.WORD_SELECT -> {
                if (selectedWords > 0 || toLineStart) host.deleteSelection()
                else host.setSelection(anchor, anchor)
                host.haptic()
            }
            State.CURSOR -> host.setCursorMode(false)
            State.SELECT -> { host.setCursorMode(false); host.onSelectionGestureEnd() }
            State.PENDING, State.DOUBLE_PENDING -> when (gesture) {
                Gesture.TAP -> { host.commitSpace(); lastTapUpTime = now }
                Gesture.SWIPE_LEFT -> if (host.hasSelection()) host.cut() else host.deleteBackward()
                Gesture.SWIPE_RIGHT -> host.paste()
                Gesture.SWIPE_UP -> if (host.hasSelection()) host.copy() else host.toggleCaps()
                Gesture.SWIPE_DOWN -> host.toggleEnglish()
                else -> {}
            }
            State.IDLE -> {}
        }
        if (gesture != Gesture.TAP) lastTapUpTime = 0L
    }

    fun cancel() {
        cancelTimer()
        when (state) {
            State.WORD_SELECT -> host.setSelection(anchor, anchor)
            State.CURSOR, State.SELECT -> host.setCursorMode(false)
            else -> {}
        }
        state = State.IDLE
    }

    /**
     * Có ngón thứ hai chạm xuống (gõ nhanh hai ngón cái hoặc cử chỉ hai ngón): dừng các trạng thái
     * đang kéo, nhưng giữ PENDING để chạm phím cách vẫn ra khoảng trắng khi nhả.
     */
    fun onMultiTouch() {
        cancelTimer()
        if (state == State.CURSOR || state == State.WORD_SELECT || state == State.SELECT) cancel()
    }

    /** Phím khác vừa được gõ: lần chạm cách tiếp theo không còn là chạm đúp. */
    fun noteOtherKey() { lastTapUpTime = 0L }

    private fun cancelTimer() = handler.removeCallbacks(holdRunnable)

    private fun wordThreshold(): Float = host.stepPx * 1.2f

    private fun enterWordSelect(dx: Float) {
        state = State.WORD_SELECT
        host.detachComposing()
        anchor = host.cursorPosition()
        anchorText = host.textBeforeCursor(2000)
        selectedWords = 0
        toLineStart = false
        updateWordSelect(dx)
    }

    private fun updateWordSelect(dx: Float) {
        val past = dx <= host.leftEdgeDx
        val words = if (-dx < wordThreshold()) 0 else ((-dx - wordThreshold()) / host.stepPx).toInt() + 1
        if (past == toLineStart && words == selectedWords) return
        toLineStart = past
        selectedWords = words
        val before = anchorText
        val ws = wordStart(before, words)
        // Quá mép trái: ít nhất tới đầu dòng hiện tại, không nhảy thêm dòng nếu số từ đã kéo đi xa hơn
        val start = if (past) minOf(ws, lineStart(before)) else ws
        host.setSelection(anchor - (before.length - start), anchor)
        host.haptic()
    }

    private fun updateSelect(dx: Float) {
        val chars = (dx / (host.stepPx / 2f)).roundToInt()
        if (chars == selectChars) return
        selectChars = chars
        val a = anchor; val b = anchor + chars
        host.setSelection(minOf(a, b).coerceAtLeast(0), maxOf(a, b).coerceAtLeast(0))
        host.haptic()
    }

    companion object {
        /**
         * Chỉ số bắt đầu của [words] bước từ cuối chuỗi (bỏ khoảng trắng cuối). Mỗi dấu xuống dòng
         * là một bước riêng, nên kéo tiếp sẽ lên dòng trên mà không phải nhả tay.
         */
        fun wordStart(text: CharSequence, words: Int): Int {
            var i = text.length
            repeat(words) {
                while (i > 0 && text[i - 1].isWhitespace() && text[i - 1] != '\n') i--
                if (i > 0 && text[i - 1] == '\n') i--
                else while (i > 0 && !text[i - 1].isWhitespace()) i--
            }
            return i
        }

        /**
         * Đích của "kéo quá mép trái": đầu dòng chứa con trỏ (cuối [text]). Nếu con trỏ đã ở ngay
         * đầu dòng thì lấy đầu dòng trên, kèm dấu xuống dòng.
         */
        fun lineStart(text: CharSequence): Int {
            var i = text.length
            while (i > 0 && text[i - 1] != '\n') i--
            if (i == text.length && i > 0) {
                i--
                while (i > 0 && text[i - 1] != '\n') i--
            }
            return i
        }
    }
}
