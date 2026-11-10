// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.aim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OrientationThrottleTest {

    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    /** [identity] with the first element nudged by [delta]. */
    private fun nudged(delta: Float) = identity.copyOf().also { it[0] += delta }

    private fun throttle() = OrientationThrottle(minIntervalMillis = 50, minMatrixChange = 0.01f, minHeadingChangeDeg = 0.5)

    @Test
    fun `the first reading is always passed on`() {
        assertTrue(throttle().shouldEmit(identity, 0.0, accuracy = 3, nowMillis = 0))
    }

    @Test
    fun `a reading that has hardly moved is dropped`() {
        val t = throttle()
        t.shouldEmit(identity, 0.0, 3, 0)
        assertFalse(t.shouldEmit(nudged(0.002f), 0.1, 3, 1000))
    }

    @Test
    fun `a real movement is passed on after the minimum interval`() {
        val t = throttle()
        t.shouldEmit(identity, 0.0, 3, 0)
        assertTrue(t.shouldEmit(nudged(0.05f), 0.0, 3, 60))
    }

    @Test
    fun `a real movement inside the minimum interval waits`() {
        val t = throttle()
        t.shouldEmit(identity, 0.0, 3, 0)
        assertFalse(t.shouldEmit(nudged(0.05f), 0.0, 3, 20))
        assertTrue(t.shouldEmit(nudged(0.05f), 0.0, 3, 50))
    }

    @Test
    fun `a change of accuracy is passed on at once`() {
        val t = throttle()
        t.shouldEmit(identity, 0.0, 3, 0)
        assertTrue(t.shouldEmit(identity, 0.0, 1, 1))
    }

    @Test
    fun `slow drift is passed on once it adds up, because it is compared with the last one passed on`() {
        val t = throttle()
        t.shouldEmit(identity, 0.0, 3, 0)
        var passed = 0
        var now = 0L
        for (step in 1..10) {
            now += 100
            if (t.shouldEmit(nudged(0.004f * step), 0.0, 3, now)) passed++
        }
        // 0.004 a step: the first reading to differ by 0.01 from the one passed on is step 3 (0.012), then step 6
        assertEquals(3, passed)
    }

    @Test
    fun `a device lying still passes on almost nothing`() {
        val t = throttle()
        t.shouldEmit(identity, 90.0, 3, 0)
        var passed = 0
        for (i in 1..500) {
            val noise = if (i % 2 == 0) 0.003f else -0.003f
            val headingNoise = if (i % 2 == 0) 0.1 else -0.1
            if (t.shouldEmit(nudged(noise), 90.0 + headingNoise, 3, i * 10L)) passed++
        }
        assertEquals(0, passed)
    }

    @Test
    fun `the caller may reuse its array`() {
        val t = throttle()
        val reused = identity.copyOf()
        t.shouldEmit(reused, 0.0, 3, 0)
        reused[0] = 0.5f // the caller overwrites its array with the next reading
        // compared with the stored copy (1.0), not the overwritten array, this is a big change
        assertTrue(t.shouldEmit(reused, 0.0, 3, 100))
        // and the same reading again is no change
        assertFalse(t.shouldEmit(reused, 0.0, 3, 200))
    }

    @Test
    fun `after a turn stops the heading keeps settling and the chart gets there`() {
        // The raw matrix stops changing the moment the device stops, but the heading is smoothed (alpha 0.15) and takes
        // many more readings to arrive. A throttle that looked only at the matrix would stop passing readings on after
        // the first one and leave the chart about 80 degrees short.
        val t = throttle()
        val turned = floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f) // a quarter turn
        var smoothed = 0.0
        var shown = 0.0
        t.shouldEmit(identity, smoothed, 3, 0) // at rest facing 0 degrees
        var now = 0L
        repeat(200) {
            now += 20
            smoothed = HeadingMath.smoothHeading(smoothed, 90.0, 0.15)
            if (t.shouldEmit(turned, smoothed, 3, now)) shown = smoothed
        }
        assertTrue("smoothed should have arrived, was $smoothed", abs(smoothed - 90.0) < 0.01)
        assertTrue("the chart stopped at $shown", abs(shown - smoothed) <= 0.5)
    }

    @Test
    fun `a heading change across north counts as small, not 359 degrees`() {
        val t = throttle()
        t.shouldEmit(identity, 359.9, 3, 0)
        assertFalse(t.shouldEmit(identity, 0.2, 3, 100)) // 0.3 degrees round the top
        assertTrue(t.shouldEmit(identity, 0.5, 3, 200)) // 0.6 degrees round the top
    }
}
