@file:Suppress("DEPRECATION")

package com.telehex.keyboard.ime

import android.content.Context
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import com.telehex.keyboard.layout.KeyCode
import com.telehex.keyboard.layout.Profile
import com.telehex.telex.TelexConfig
import java.util.concurrent.FutureTask

class KeyboardLifecycleTest : InstrumentationTestCase() {
    private class TestView(context: Context) : KeyboardView(context) {
        fun detach() = onDetachedFromWindow()
    }

    private fun onMain(block: () -> Unit) {
        val task = FutureTask { block() }
        instrumentation.runOnMainSync(task)
        task.get()
    }

    private inner class Session {
        val view = TestView(instrumentation.targetContext)
        val typed = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        var onKey: ((Key) -> Unit)? = null
        private val start = SystemClock.uptimeMillis()
        private var elapsed = 0L

        init {
            show(Mode.ABC)
            view.layout(0, 0, 1080, view.expectedHeight(1080))
            view.hapticEnabled = false
            view.listener = object : KeyboardView.Listener {
                override fun onKeyDown(key: Key) {}
                override fun onKeyMove(key: Key, dx: Float, dy: Float, dt: Long) {}
                override fun onKeyUp(key: Key, gesture: Gesture, dx: Float, dy: Float, dt: Long) {
                    typed += key.code
                    onKey?.invoke(key)
                }
                override fun onKeyCancel(key: Key) { cancelled += key.code }
            }
        }

        fun show(mode: Mode) {
            val p = Profile.DEFAULT
            view.keys = KeyPage.build(p.letters, mode, 1, TelexConfig(), p.swipe, p.symbols, "↵")
        }

        fun event(action: Int, vararg contacts: Pair<Int, String>) {
            val properties = contacts.map { (id, _) ->
                MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER }
            }.toTypedArray()
            val coords = contacts.map { (_, code) ->
                val center = view.centerOf(view.keys.first { it.code == code })!!
                MotionEvent.PointerCoords().apply { x = center.x; y = center.y; pressure = 1f; size = .1f }
            }.toTypedArray()
            elapsed += 20
            val event = MotionEvent.obtain(start, start + elapsed, action, contacts.size, properties, coords,
                0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { view.onTouchEvent(event) } finally { event.recycle() }
        }

        fun tap(code: String) {
            event(MotionEvent.ACTION_DOWN, 0 to code)
            event(MotionEvent.ACTION_UP, 0 to code)
        }

        fun queueSettings() {
            event(MotionEvent.ACTION_DOWN, 0 to KeyCode.SETTINGS)
            event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                0 to KeyCode.SETTINGS, 1 to "n")
            event(MotionEvent.ACTION_POINTER_UP, 0 to KeyCode.SETTINGS, 1 to "n")
            assertTrue(typed.isEmpty())
        }
    }

    fun testLayerChangesDiscardQueuedSettingsBeforeFirstAnh() = onMain {
        val s = Session()
        s.queueSettings()
        s.show(Mode.SYMBOLS)
        s.show(Mode.ABC)
        // A late release from the old keyboard must not replay its settings tap.
        s.event(MotionEvent.ACTION_UP, 1 to "n")
        for (code in listOf("a", "n", "h")) s.tap(code)
        assertEquals(listOf("a", "n", "h"), s.typed)
        assertTrue(s.cancelled.containsAll(listOf(KeyCode.SETTINGS, "n")))
    }

    fun testFreshDownDiscardsAnUnfinishedOldTouchStream() = onMain {
        val s = Session()
        s.queueSettings()
        for (code in listOf("a", "n", "h")) s.tap(code)
        assertEquals(listOf("a", "n", "h"), s.typed)
    }

    fun testDetachDiscardsTheOldFunctionTap() = onMain {
        val s = Session()
        s.event(MotionEvent.ACTION_DOWN, 0 to KeyCode.SETTINGS)
        s.view.detach()
        s.event(MotionEvent.ACTION_UP, 0 to KeyCode.SETTINGS)
        for (code in listOf("a", "n", "h")) s.tap(code)
        assertEquals(listOf("a", "n", "h"), s.typed)
    }

    fun testPageSwitchStopsOtherQueuedFunctionsAndCancelsSpace() = onMain {
        for (other in listOf(KeyCode.SETTINGS, KeyCode.SPACE)) {
            val s = Session()
            s.onKey = { if (it.code == KeyCode.NUMBERS) s.show(Mode.SYMBOLS) }
            s.event(MotionEvent.ACTION_DOWN, 0 to KeyCode.NUMBERS)
            s.event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                0 to KeyCode.NUMBERS, 1 to other)
            s.event(MotionEvent.ACTION_POINTER_UP, 0 to KeyCode.NUMBERS, 1 to other)
            s.event(MotionEvent.ACTION_UP, 1 to other)
            assertEquals(listOf(KeyCode.NUMBERS), s.typed)
            assertTrue("Pending $other must be cancelled", other in s.cancelled)
        }
    }

    fun testNormalOverlappingTypingAndIdenticalKeyRefreshArePreserved() = onMain {
        val s = Session()
        s.event(MotionEvent.ACTION_DOWN, 0 to "a")
        s.show(Mode.ABC)
        s.event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 0 to "a", 1 to "n")
        s.event(MotionEvent.ACTION_POINTER_UP, 0 to "a", 1 to "n")
        s.event(MotionEvent.ACTION_UP, 1 to "n")
        s.tap("h")
        assertEquals(listOf("a", "n", "h"), s.typed)
    }

    fun testCancelledStreamIgnoresAdditionalPointersUntilFreshDown() = onMain {
        val s = Session()
        s.event(MotionEvent.ACTION_DOWN, 0 to "a")
        s.show(Mode.SYMBOLS)
        s.show(Mode.ABC)
        // A second finger in the cancelled stream must not become a fresh settings tap.
        s.event(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            0 to "a", 1 to KeyCode.SETTINGS)
        s.event(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            0 to "a", 1 to KeyCode.SETTINGS)
        s.event(MotionEvent.ACTION_UP, 0 to "a")
        for (code in listOf("a", "n", "h")) s.tap(code)
        assertEquals(listOf("a", "n", "h"), s.typed)
    }
}
