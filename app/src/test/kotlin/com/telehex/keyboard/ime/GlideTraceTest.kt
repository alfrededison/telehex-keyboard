package com.telehex.keyboard.ime

import com.telehex.telex.predict.GlideDecoder.Point
import org.junit.Assert.*
import org.junit.Test

class GlideTraceTest {
    @Test fun tapsAndShortVerticalSwipesRemainKeyGestures() {
        val trace = GlideTrace(Point(0f, 0f), 'a')
        trace.add(Point(0.1f, 0.1f), 'a')
        assertFalse(trace.active)
        trace.add(Point(0.1f, 1.7f), 'b')
        assertFalse(trace.active)
    }

    @Test fun twoDistinctKeysNeverActivateEvenOnALongOrRepeatedPath() {
        val trace = GlideTrace(Point(0f, 0f), 'a')
        trace.add(Point(2f, 0.1f), 'b')
        assertFalse(trace.active)
        trace.add(Point(0f, 0f), 'a')
        trace.add(Point(3f, 2f), 'b')
        assertFalse(trace.active)
    }

    @Test fun threeDistinctKeysActivateInEveryDirectionAndStayActiveOnReturn() {
        for (direction in listOf(Point(1f, 0f), Point(0f, 1f), Point(0.7f, 0.7f))) {
            val trace = GlideTrace(Point(0f, 0f), 'a')
            trace.add(direction, 'b')
            assertFalse(trace.active)
            trace.add(Point(direction.x * 2, direction.y * 2), 'c')
            assertTrue(trace.active)
            trace.add(Point(0f, 0f), 'a')
            assertTrue(trace.active)
            assertEquals(Point(0f, 0f), trace.points.last())
        }
    }

    @Test fun functionKeysAndGapsDoNotCountAsAdditionalLetters() {
        val trace = GlideTrace(Point(0f, 0f), 'a')
        trace.add(Point(1f, 0f), null)
        trace.add(Point(2f, 0f), 'b')
        assertFalse(trace.active)
        trace.add(Point(3f, 0f), 'c')
        assertTrue(trace.active)
    }

    @Test fun leavingKeyboardWithoutCrossingLettersDoesNotActivate() {
        val trace = GlideTrace(Point(0f, 0f), 'a')
        trace.add(Point(5f, 0f), null)
        assertFalse(trace.active)
    }

    @Test fun longTracesAreBoundedAndKeepEndpoints() {
        val first = Point(0f, 0f)
        val trace = GlideTrace(first, 'a')
        for (i in 1..4000) trace.add(Point(i / 10f, 0f), 'b')
        assertTrue(trace.points.size <= 256)
        assertEquals(first, trace.points.first())
        assertEquals(Point(400f, 0f), trace.points.last())
    }
}
