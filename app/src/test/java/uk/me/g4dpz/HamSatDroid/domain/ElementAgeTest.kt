// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ElementAgeTest {

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun `day 1 point 0 is midnight on 1 January`() {
        assertEquals(millis("2026-01-01T00:00:00Z"), ElementAge.epochMillis(26, 1.0))
    }

    @Test
    fun `fractional day gives the time of day`() {
        assertEquals(millis("2026-02-01T12:00:00Z"), ElementAge.epochMillis(26, 32.5))
    }

    @Test
    fun `two digit years follow the TLE convention`() {
        assertEquals(millis("1957-01-01T00:00:00Z"), ElementAge.epochMillis(57, 1.0))
        assertEquals(millis("1999-01-01T00:00:00Z"), ElementAge.epochMillis(99, 1.0))
        assertEquals(millis("2000-01-01T00:00:00Z"), ElementAge.epochMillis(0, 1.0))
        assertEquals(millis("2056-01-01T00:00:00Z"), ElementAge.epochMillis(56, 1.0))
    }

    @Test
    fun `the bundled 2010 ISS elements are stale`() {
        // Epoch 10028.85115464 from res/raw/nasabare.txt
        val epoch = ElementAge.epochMillis(10, 28.85115464)
        val days = ElementAge.staleDays(epoch, millis("2026-11-04T16:00:00Z"))
        // 28 Jan 2010 20:25 UTC to 4 Nov 2026 16:00 UTC is 6123 whole days (checked with Python's datetime)
        assertEquals(6123, days)
    }

    @Test
    fun `thirty days old is not stale, thirty one is`() {
        val epoch = millis("2026-10-01T00:00:00Z")
        assertNull(ElementAge.staleDays(epoch, millis("2026-10-31T00:00:00Z")))
        assertEquals(31, ElementAge.staleDays(epoch, millis("2026-11-01T00:00:00Z")))
    }

    @Test
    fun `an epoch in the future is not stale`() {
        val epoch = millis("2026-11-10T00:00:00Z")
        assertEquals(-6, ElementAge.ageDays(epoch, millis("2026-11-04T00:00:00Z")))
        assertNull(ElementAge.staleDays(epoch, millis("2026-11-04T00:00:00Z")))
    }
}
