@file:Suppress("DEPRECATION")

package com.telehex.keyboard.ime

import android.test.InstrumentationTestCase
import android.os.SystemClock
import android.view.MotionEvent
import com.telehex.keyboard.layout.HexGeometry
import com.telehex.keyboard.layout.KeyCode
import com.telehex.keyboard.layout.Profile
import com.telehex.telex.TelexConfig
import java.util.concurrent.FutureTask

class KeyboardHitTest : InstrumentationTestCase() {
    private fun onMain(block: () -> Unit) {
        val task = FutureTask { block() }
        instrumentation.runOnMainSync(task)
        task.get()
    }

    fun testSwappedKeyCentersIgnoreLearnedOffsets() {
        onMain {
            for (function in listOf(KeyCode.SETTINGS, KeyCode.NUMBERS)) {
                val original = Profile.DEFAULT
                val profile = original.swapLetters(original.letters.find("a")!!, original.letters.find(function)!!)
                for (hand in 0..2) {
                    val view = KeyboardView(instrumentation.targetContext)
                    view.oneHanded = hand
                    view.keys = KeyPage.build(profile.letters, Mode.ABC, 1, TelexConfig(), profile.swipe, profile.symbols, "↵")
                    view.layout(0, 0, 1080, view.expectedHeight(1080))
                    val g = HexGeometry(1080f, view.dp(8f), view.dp(6f), view.dp(3f), if (hand == 0) 1f else .8f, hand == 2)
                    for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) {
                        view.touchOffsets = view.keys.filter { it.isLetter }.associate {
                            it.code to floatArrayOf(sx * g.w * .4f, sy * g.w * .4f)
                        }.toMutableMap()
                        for (key in view.keys) {
                            val center = view.centerOf(key)!!
                            assertEquals("${key.code}, $function, hand=$hand, offset=$sx/$sy", key.code, view.keyAt(center.x, center.y)?.code)
                        }
                    }
                }
            }
        }
    }

    fun testNearbyLetterSwapsCannotActivateFunctionsAtTheirEdges() = onMain {
        for ((a, b) in listOf("t" to "f", "i" to "p")) {
            val profile = Profile.DEFAULT.swapLetters(Profile.DEFAULT.letters.find(a)!!, Profile.DEFAULT.letters.find(b)!!)
            val view = KeyboardView(instrumentation.targetContext)
            view.keys = KeyPage.build(profile.letters, Mode.ABC, 1, TelexConfig(), profile.swipe, profile.symbols, "↵")
            view.layout(0, 0, 1080, view.expectedHeight(1080))
            val g = HexGeometry(1080f, view.dp(8f), view.dp(6f), view.dp(3f))
            for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) {
                view.touchOffsets = view.keys.filter { it.isLetter }.associate {
                    it.code to floatArrayOf(sx * g.w * .4f, sy * g.w * .4f)
                }.toMutableMap()
                for (r in 0..4) for (c in 0..6) {
                    val physical = view.keys.first { r to c in it.cells }
                    for (dx in listOf(-.3f, 0f, .3f)) for (dy in listOf(-.3f, 0f, .3f)) {
                        val actual = view.keyAt(g.centerX(r, c) + dx * g.w, g.centerY(r) + dy * g.w)!!
                        if (physical.isLetter) assertTrue("${physical.code} became ${actual.code}", actual.isLetter)
                        else assertEquals(physical.code, actual.code)
                    }
                }
            }
        }
    }

    fun testDragUsesVisibleCellAndReleasePosition() {
        lateinit var view: KeyboardView
        var swapped: Pair<String, String>? = null
        lateinit var g: HexGeometry
        onMain {
            val p = Profile.DEFAULT
            view = KeyboardView(instrumentation.targetContext)
            view.keys = KeyPage.build(p.letters, Mode.ABC, 1, TelexConfig(), p.swipe, p.symbols, "↵")
            view.layout(0, 0, 1080, view.expectedHeight(1080))
            g = HexGeometry(1080f, view.dp(8f), view.dp(6f), view.dp(3f))
            view.hapticEnabled = false
            view.canLift = { it.isLetter }
            view.dragHoldMs = 1
            view.validNextLetter = { it == 'f' }
            view.listener = object : KeyboardView.Listener {
                override fun onKeyDown(key: Key) {}
                override fun onKeyMove(key: Key, dx: Float, dy: Float, dt: Long) {}
                override fun onKeyUp(key: Key, gesture: Gesture, dx: Float, dy: Float, dt: Long) { fail("Drag typed ${key.code}") }
                override fun onKeyCancel(key: Key) {}
                override fun onKeySwap(from: Key, to: Key) { swapped = from.code to to.code }
            }
        }
        val start = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) = onMain {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0)
            try { view.onTouchEvent(event) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, g.centerX(2, 0), g.centerY(2))
        // Detached views queue postDelayed until attachment, so run the same lift callback directly.
        onMain {
            val field = KeyboardView::class.java.getDeclaredField("liftKey").apply { isAccessible = true }
            (field.get(view) as Runnable).run()
        }
        // This point is visibly T, but dynamic zones choose the valid F during normal typing.
        send(MotionEvent.ACTION_MOVE, g.centerX(1, 2) + g.w * .45f, g.centerY(1))
        send(MotionEvent.ACTION_UP, g.centerX(1, 2) + g.w * .45f, g.centerY(1))
        assertEquals("a" to "t", swapped)
        swapped = null
        send(MotionEvent.ACTION_DOWN, g.centerX(2, 0), g.centerY(2))
        onMain {
            val field = KeyboardView::class.java.getDeclaredField("liftKey").apply { isAccessible = true }
            (field.get(view) as Runnable).run()
        }
        send(MotionEvent.ACTION_MOVE, g.centerX(1, 2), g.centerY(1))
        send(MotionEvent.ACTION_UP, g.centerX(1, 3), g.centerY(1))
        assertEquals("a" to "f", swapped)
    }

    fun testStationaryHoldNeverSwapsAdaptiveSource() = onMain {
        for (dynamic in listOf(false, true)) {
            val p = Profile.DEFAULT
            val view = KeyboardView(instrumentation.targetContext)
            view.keys = KeyPage.build(p.letters, Mode.ABC, 1, TelexConfig(), p.swipe, p.symbols, "↵")
            view.layout(0, 0, 1080, view.expectedHeight(1080))
            val g = HexGeometry(1080f, view.dp(8f), view.dp(6f), view.dp(3f))
            view.hapticEnabled = false
            view.canLift = { it.isLetter }
            if (dynamic) view.validNextLetter = { it == 'f' }
            else view.touchOffsets = mutableMapOf("t" to floatArrayOf(-g.w * .2f, 0f))
            val x = g.centerX(1, 2) + g.w * .45f
            val y = g.centerY(1)
            assertEquals("f", view.keyAt(x, y)?.code)
            var lifted: String? = null
            var typed: String? = null
            view.listener = object : KeyboardView.Listener {
                override fun onKeyDown(key: Key) {}
                override fun onKeyMove(key: Key, dx: Float, dy: Float, dt: Long) {}
                override fun onKeyUp(key: Key, gesture: Gesture, dx: Float, dy: Float, dt: Long) {
                    assertEquals(Gesture.LONG_PRESS, gesture)
                    typed = key.code
                }
                override fun onKeyCancel(key: Key) {}
                override fun onKeyLift(key: Key) { lifted = key.code }
                override fun onKeySwap(from: Key, to: Key) { fail("Stationary hold swapped ${from.code}/${to.code}") }
            }
            val start = SystemClock.uptimeMillis()
            fun send(action: Int, dt: Long) {
                val event = MotionEvent.obtain(start, start + dt, action, x, y, 0)
                try { view.onTouchEvent(event) } finally { event.recycle() }
            }
            send(MotionEvent.ACTION_DOWN, 0)
            val field = KeyboardView::class.java.getDeclaredField("liftKey").apply { isAccessible = true }
            (field.get(view) as Runnable).run()
            send(MotionEvent.ACTION_MOVE, 850)
            send(MotionEvent.ACTION_UP, 900)
            assertEquals("t", lifted)
            assertEquals("f", typed)
        }
    }

    fun testAnhAfterEditingBothLayersAndLandingBesideSettings() = onMain {
        val original = Profile.DEFAULT
        val besideSettings = original.swapLetters(original.letters.find(KeyCode.SETTINGS)!!, 1 to 0)
        val profile = besideSettings.swapLetters(besideSettings.letters.find("a")!!, 1 to 1)
            .swapSymbols(0, 0 to 0, 0 to 1)
        val view = KeyboardView(instrumentation.targetContext)
        fun show(mode: Mode) {
            view.keys = KeyPage.build(profile.letters, mode, 1, TelexConfig(), profile.swipe, profile.symbols, "↵")
        }
        show(Mode.ABC)
        view.layout(0, 0, 1080, view.expectedHeight(1080))
        show(Mode.SYMBOLS)
        show(Mode.ABC)
        view.hapticEnabled = false
        val typed = mutableListOf<String>()
        view.listener = object : KeyboardView.Listener {
            override fun onKeyDown(key: Key) {}
            override fun onKeyMove(key: Key, dx: Float, dy: Float, dt: Long) {}
            override fun onKeyUp(key: Key, gesture: Gesture, dx: Float, dy: Float, dt: Long) { typed += key.code }
            override fun onKeyCancel(key: Key) {}
        }
        val a = view.centerOf(view.keys.first { it.code == "a" })!!
        val settings = view.centerOf(view.keys.first { it.code == KeyCode.SETTINGS })!!
        val boundary = (a.x + settings.x) / 2
        assertEquals(KeyCode.SETTINGS, view.keyAt(boundary - view.dp(2f), a.y)?.code)
        assertEquals("a", view.keyAt(boundary + view.dp(2f), a.y)?.code)
        val start = SystemClock.uptimeMillis()
        fun send(action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(start, start + if (action == MotionEvent.ACTION_UP) 40 else 0, action, x, y, 0)
            try { view.onTouchEvent(event) } finally { event.recycle() }
        }
        // The thumb rolls 4dp across the boundary before lifting inside A.
        send(MotionEvent.ACTION_DOWN, boundary - view.dp(2f), a.y)
        send(MotionEvent.ACTION_UP, boundary + view.dp(2f), a.y)
        for (code in listOf("n", "h")) {
            val c = view.centerOf(view.keys.first { it.code == code })!!
            send(MotionEvent.ACTION_DOWN, c.x, c.y)
            send(MotionEvent.ACTION_UP, c.x, c.y)
        }
        assertEquals(listOf("a", "n", "h"), typed)
    }

    fun testLandingFrom123OnLetterKeepsIntentionalFunctionGestures() = onMain {
        val p = Profile.DEFAULT
        val view = KeyboardView(instrumentation.targetContext)
        view.keys = KeyPage.build(p.letters, Mode.ABC, 1, TelexConfig(), p.swipe, p.symbols, "↵")
        view.layout(0, 0, 1080, view.expectedHeight(1080))
        view.hapticEnabled = false
        view.touchOffsets = mutableMapOf()
        val g = HexGeometry(1080f, view.dp(8f), view.dp(6f), view.dp(3f))
        val numbers = view.centerOf(view.keys.first { it.code == KeyCode.NUMBERS })!!
        val o = view.centerOf(view.keys.first { it.code == "o" })!!
        val boundary = (numbers.x + o.x) / 2
        var result: Pair<String, Gesture>? = null
        view.listener = object : KeyboardView.Listener {
            override fun onKeyDown(key: Key) {}
            override fun onKeyMove(key: Key, dx: Float, dy: Float, dt: Long) {}
            override fun onKeyUp(key: Key, gesture: Gesture, dx: Float, dy: Float, dt: Long) { result = key.code to gesture }
            override fun onKeyCancel(key: Key) {}
        }
        val start = SystemClock.uptimeMillis()
        fun touch(x: Float, y: Float, upX: Float = x, upY: Float = y, duration: Long = 40) {
            for ((action, dt, point) in listOf(
                Triple(MotionEvent.ACTION_DOWN, 0L, x to y),
                Triple(MotionEvent.ACTION_UP, duration, upX to upY),
            )) {
                val event = MotionEvent.obtain(start, start + dt, action, point.first, point.second, 0)
                try { view.onTouchEvent(event) } finally { event.recycle() }
            }
        }
        touch(boundary + view.dp(2f), o.y, boundary - view.dp(2f), o.y)
        assertEquals("o" to Gesture.TAP, result)
        // The recovered letter learns the release position, not the point inside 123.
        assertEquals((boundary - view.dp(2f) - o.x).coerceIn(-g.w * .4f, g.w * .4f) * .08f,
            view.touchOffsets!!["o"]!![0], .001f)
        for (code in listOf(KeyCode.NUMBERS, KeyCode.SETTINGS)) {
            val c = view.centerOf(view.keys.first { it.code == code })!!
            touch(c.x, c.y)
            assertEquals(code to Gesture.TAP, result)
            touch(c.x, c.y, duration = 600)
            assertEquals(code to Gesture.LONG_PRESS, result)
        }
        val settings = view.centerOf(view.keys.first { it.code == KeyCode.SETTINGS })!!
        touch(settings.x, settings.y, upY = settings.y + view.dp(30f))
        assertEquals(KeyCode.SETTINGS to Gesture.SWIPE_DOWN, result)
        // A letter tap ending across the boundary must not become a function.
        touch(boundary - view.dp(2f), o.y, boundary + view.dp(2f), o.y)
        assertEquals("o" to Gesture.TAP, result)
    }
}
