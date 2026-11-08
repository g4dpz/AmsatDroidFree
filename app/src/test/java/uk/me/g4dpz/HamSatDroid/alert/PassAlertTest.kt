// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import uk.me.g4dpz.HamSatDroid.domain.PoleCrossing
import java.time.Instant

class PassAlertTest {

    private val aos = 1_000_000_000_000L
    private val los = aos + 10 * 60_000L // a ten-minute pass
    private val alert = PassAlert(25544, "ISS", aos, los, 45.0, 114, 339)

    @Test
    fun `the same prediction is the same pass`() {
        assertTrue(alert.isSamePass(alert))
        assertTrue(alert.isSamePass(25544, aos, los))
    }

    @Test
    fun `a prediction a few seconds different is the same pass, though its id differs`() {
        // The passes screen and the overview predict from different start times; the predictor refines to 5 seconds
        // from wherever it started, so the two AOS values can straddle a second boundary.
        val shifted = alert.copy(aosMillis = aos + 4_000L, losMillis = los - 3_000L)
        assertNotEquals(alert.id, shifted.id)
        assertTrue(alert.isSamePass(shifted))
        assertTrue(shifted.isSamePass(alert))
    }

    @Test
    fun `overlap is what matters, however small`() {
        // Starts just before this one ends.
        assertTrue(alert.isSamePass(25544, los - 1L, los + 60_000L))
        // Ends just after this one starts.
        assertTrue(alert.isSamePass(25544, aos - 60_000L, aos + 1L))
    }

    @Test
    fun `touching intervals are different passes`() {
        // [aos, los) is half-open: a pass that starts exactly at this one's LOS does not overlap it.
        assertFalse(alert.isSamePass(25544, los, los + 60_000L))
        assertFalse(alert.isSamePass(25544, aos - 60_000L, aos))
    }

    @Test
    fun `the next pass of the same satellite is a different pass`() {
        val nextOrbit = aos + 95 * 60_000L
        assertFalse(alert.isSamePass(25544, nextOrbit, nextOrbit + 10 * 60_000L))
    }

    @Test
    fun `another satellite at the same time is a different pass`() {
        assertFalse(alert.isSamePass(7530, aos, los))
        assertFalse(alert.isSamePass(alert.copy(catnum = 7530)))
    }

    @Test
    fun `a PassSummary is matched by the same rule`() {
        val summary = PassSummary(
            catnum = 25544, satName = "ISS",
            start = Instant.ofEpochMilli(aos + 2_000L), end = Instant.ofEpochMilli(los + 1_000L),
            tca = Instant.ofEpochMilli(aos + 5 * 60_000L), pole = PoleCrossing.NONE,
            aosAzimuthDeg = 114, losAzimuthDeg = 339, maxElevationDeg = 45.0,
        )
        assertTrue(alert.isSamePass(summary))
        assertFalse(alert.isSamePass(summary.copy(catnum = 7530)))
        // from() keeps the times, so an alert built from the summary matches it exactly.
        assertEquals(summary.start.toEpochMilli(), PassAlert.from(summary).aosMillis)
        assertTrue(PassAlert.from(summary).isSamePass(summary))
    }
}
