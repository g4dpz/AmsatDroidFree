// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class OverviewRowsTest {

    private val t0 = Instant.parse("2026-11-04T12:00:00Z")

    private fun pass(catnum: Int, name: String, startMin: Long, lengthMin: Long, maxEl: Double) = PassSummary(
        catnum = catnum, satName = name,
        start = t0.plusSeconds(startMin * 60), end = t0.plusSeconds((startMin + lengthMin) * 60),
        tca = t0.plusSeconds((startMin + lengthMin / 2) * 60), pole = PoleCrossing.NONE,
        aosAzimuthDeg = 0, losAzimuthDeg = 180, maxElevationDeg = maxEl,
    )

    private val iss = OverviewItem.Result(
        25544, "ISS", now = AzEl(200.0, 20.0), losNow = t0.plusSeconds(4 * 60),
        passes = listOf(pass(25544, "ISS", -6, 10, 45.0), pass(25544, "ISS", 90, 9, 12.0)),
    )
    private val ao07 = OverviewItem.Result(
        7530, "AO-07", now = null, losNow = null,
        passes = listOf(pass(7530, "AO-07", 30, 20, 8.0), pass(7530, "AO-07", 150, 22, 60.0)),
    )
    private val qo100 = OverviewItem.Result(43700, "QO-100", now = AzEl(150.0, 25.0), losNow = null, passes = emptyList())
    private val all = listOf(iss, ao07, qo100)

    @Test
    fun `upcoming merges every satellite sorted by AOS and keeps the pass in progress`() {
        val rows = OverviewRows.upcoming(all, 0, t0)
        assertEquals(listOf(-6L, 30L, 90L, 150L), rows.map { Duration.between(t0, it.start).toMinutes() })
        assertEquals(listOf("ISS", "AO-07", "ISS", "AO-07"), rows.map { it.satName })
    }

    @Test
    fun `upcoming drops passes that have ended`() {
        val rows = OverviewRows.upcoming(all, 0, t0.plusSeconds(5 * 60))
        assertTrue(rows.none { it.satName == "ISS" && it.start.isBefore(t0) })
        assertEquals(3, rows.size)
    }

    @Test
    fun `the minimum elevation filter keeps passes at or above it`() {
        assertEquals(listOf(45.0, 12.0, 60.0), OverviewRows.upcoming(all, 10, t0).map { it.maxElevationDeg })
        assertEquals(listOf(45.0, 60.0), OverviewRows.upcoming(all, 30, t0).map { it.maxElevationDeg })
        assertEquals(listOf(45.0, 60.0), OverviewRows.upcoming(all, 45, t0).map { it.maxElevationDeg })
    }

    @Test
    fun `up now lists satellites in a pass and stationary ones, highest first`() {
        val positions = mapOf(25544 to AzEl(210.0, 22.0), 43700 to AzEl(150.0, 25.0))
        val rows = OverviewRows.upNow(all, t0) { positions[it] }
        assertEquals(listOf("QO-100", "ISS"), rows.map { it.name })
        val issRow = rows.last()
        assertEquals(t0.plusSeconds(4 * 60), issRow.los)
        assertEquals(iss.passes.first(), issRow.pass)
        assertNull(rows.first().los)
        assertNull(rows.first().pass)
    }

    @Test
    fun `up now leaves out a satellite whose pass has ended or whose position is below the horizon`() {
        val later = t0.plusSeconds(5 * 60) // the ISS pass ended at +4 min
        assertEquals(listOf("QO-100"), OverviewRows.upNow(all, later) { AzEl(0.0, 10.0) }.map { it.name })
        assertEquals(emptyList<UpNowRow>(), OverviewRows.upNow(all, t0) { AzEl(0.0, -1.0) })
    }

    @Test
    fun `candidates are the satellites up now asks positions for`() {
        assertEquals(listOf(25544, 43700), OverviewRows.upNowCandidates(all, t0).map { it.catnum })
        assertEquals(listOf(7530, 43700), OverviewRows.upNowCandidates(all, t0.plusSeconds(35 * 60)).map { it.catnum })
    }

    @Test
    fun `countdown formats minutes and hours`() {
        assertEquals("0:00", Countdown.format(Duration.ZERO))
        assertEquals("4:05", Countdown.format(Duration.ofSeconds(245)))
        assertEquals("59:59", Countdown.format(Duration.ofSeconds(3599)))
        assertEquals("1:00:00", Countdown.format(Duration.ofHours(1)))
        assertEquals("12:03:09", Countdown.format(Duration.ofSeconds(12 * 3600 + 189)))
        assertEquals("0:00", Countdown.format(Duration.ofSeconds(-5)))
    }
}
