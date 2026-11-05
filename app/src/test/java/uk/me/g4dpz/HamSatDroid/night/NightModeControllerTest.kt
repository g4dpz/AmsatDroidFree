// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.night

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.night.NightModeController.Companion.ENTER_MILLIS
import uk.me.g4dpz.HamSatDroid.night.NightModeController.Companion.EXIT_MILLIS

class NightModeControllerTest {

    private val dark = 1f
    private val bright = 100f
    private val between = 10f // above ENTER_LUX, below EXIT_LUX

    @Test
    fun `starts in day and stays there in bright light`() {
        val c = NightModeController()
        assertFalse(c.onSample(bright, 0))
        assertFalse(c.onSample(bright, 60_000))
    }

    @Test
    fun `switches to night only after it has been dark long enough`() {
        val c = NightModeController()
        assertFalse(c.onSample(dark, 1_000))
        assertFalse(c.onSample(dark, 1_000 + ENTER_MILLIS - 1))
        assertTrue(c.onSample(dark, 1_000 + ENTER_MILLIS))
    }

    @Test
    fun `switches back to day only after it has been bright long enough`() {
        val c = NightModeController(initiallyNight = true)
        assertTrue(c.onSample(bright, 0))
        assertTrue(c.onSample(bright, EXIT_MILLIS - 1))
        assertFalse(c.onSample(bright, EXIT_MILLIS))
    }

    @Test
    fun `a brief brightening restarts the wait for night`() {
        val c = NightModeController()
        c.onSample(dark, 0)
        c.onSample(dark, ENTER_MILLIS - 1_000)
        c.onSample(bright, ENTER_MILLIS - 500) // someone switches a torch on for a moment
        assertFalse(c.onSample(dark, ENTER_MILLIS))
        assertFalse(c.onSample(dark, ENTER_MILLIS + ENTER_MILLIS - 1))
        assertTrue(c.onSample(dark, ENTER_MILLIS + ENTER_MILLIS))
    }

    @Test
    fun `light between the thresholds changes nothing, so it does not flicker`() {
        val night = NightModeController(initiallyNight = true)
        for (t in 0L..60_000L step 500) assertTrue(night.onSample(between, t))
        val day = NightModeController()
        for (t in 0L..60_000L step 500) assertFalse(day.onSample(between, t))
    }

    @Test
    fun `hovering across one threshold does not toggle`() {
        val c = NightModeController()
        // alternating just below ENTER_LUX and between the thresholds every second never stays dark for long enough
        for (i in 0 until 60) {
            val lux = if (i % 2 == 0) dark else between
            assertFalse(c.onSample(lux, i * 1_000L))
        }
    }

    @Test
    fun `a steady reading fed again by the tick still switches`() {
        // The light sensor reports only changes, so the caller repeats the last value; that must be enough.
        val c = NightModeController()
        var t = 0L
        while (t < ENTER_MILLIS) {
            assertFalse(c.onSample(dark, t))
            t += 500
        }
        assertTrue(c.onSample(dark, t))
    }

    @Test
    fun `reset keeps the current answer but forgets a wait in progress`() {
        val c = NightModeController()
        c.onSample(dark, 0)
        c.resetTimers()
        // after a long time in the background, the first reading back starts a new wait
        assertFalse(c.onSample(dark, 3_600_000))
        assertTrue(c.onSample(dark, 3_600_000 + ENTER_MILLIS))
        c.resetTimers()
        assertTrue("stays night after a reset", c.isNight)
    }

    @Test
    fun `automatic night overrides the chosen theme only while it is dark`() {
        assertEquals(ThemeMode.RED_NIGHT, effectiveThemeMode(ThemeMode.LIGHT, ambientNight = true))
        assertEquals(ThemeMode.LIGHT, effectiveThemeMode(ThemeMode.LIGHT, ambientNight = false))
        assertEquals(ThemeMode.DARK, effectiveThemeMode(ThemeMode.DARK, ambientNight = false))
    }
}
