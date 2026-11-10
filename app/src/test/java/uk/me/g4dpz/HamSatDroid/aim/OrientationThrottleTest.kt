// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.aim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientationThrottleTest {

    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    /** [identity] with the first element nudged by [delta]. */
    private fun nudged(delta: Float) = identity.copyOf().also { it[0] += delta }

    private fun throttle() = OrientationThrottle(minIntervalMillis = 50, minMatrixChange = 0.01f)

    @Test
    fun `the first reading is always passed on`() {
        assertTrue(throttle().shouldEmit(identity, accuracy = 3, nowMillis = 0))
    }

    @Test
    fun `a reading that has hardly moved is dropped`() {
        val t = throttle()
        t.shouldEmit(identity, 3, 0)
        assertFalse(t.shouldEmit(nudged(0.002f), 3, 1000))
    }

    @Test
    fun `a real movement is passed on after the minimum interval`() {
        val t = throttle()
        t.shouldEmit(identity, 3, 0)
        assertTrue(t.shouldEmit(nudged(0.05f), 3, 60))
    }

    @Test
    fun `a real movement inside the minimum interval waits`() {
        val t = throttle()
        t.shouldEmit(identity, 3, 0)
        assertFalse(t.shouldEmit(nudged(0.05f), 3, 20))
        assertTrue(t.shouldEmit(nudged(0.05f), 3, 50))
    }

    @Test
    fun `a change of accuracy is passed on at once`() {
        val t = throttle()
        t.shouldEmit(identity, 3, 0)
        assertTrue(t.shouldEmit(identity, 1, 1))
    }

    @Test
    fun `slow drift is passed on once it adds up, because it is compared with the last one passed on`() {
        val t = throttle()
        t.shouldEmit(identity, 3, 0)
        var passed = 0
        var now = 0L
        for (step in 1..10) {
            now += 100
            if (t.shouldEmit(nudged(0.004f * step), 3, now)) passed++
        }
        // 0.004 a step: the first reading to differ by 0.01 from the one passed on is step 3 (0.012), then step 6
        assertEquals(3, passed)
    }

    @Test
    fun `a device lying still passes on almost nothing`() {
        val t = throttle()
        t.shouldEmit(identity, 3, 0)
        var passed = 0
        for (i in 1..500) {
            val noise = if (i % 2 == 0) 0.003f else -0.003f
            if (t.shouldEmit(nudged(noise), 3, i * 10L)) passed++
        }
        assertEquals(0, passed)
    }

    @Test
    fun `the caller may reuse its array`() {
        val t = throttle()
        val reused = identity.copyOf()
        t.shouldEmit(reused, 3, 0)
        reused[0] = 0.5f // the caller overwrites its array with the next reading
        // compared with the stored copy (1.0), not the overwritten array, this is a big change
        assertTrue(t.shouldEmit(reused, 3, 100))
        // and the same reading again is no change
        assertFalse(t.shouldEmit(reused, 3, 200))
    }
}
