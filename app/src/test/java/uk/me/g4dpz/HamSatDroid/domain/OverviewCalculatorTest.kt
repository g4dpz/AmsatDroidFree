// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.satellite.OrbitalElements
import uk.me.g4dpz.satellite.TLE
import java.io.File
import java.time.Instant

class OverviewCalculatorTest {

    private val tles = File("src/main/res/raw/nasabare.txt").inputStream().use { TLE.importSat(it) }
    private val start = Instant.ofEpochMilli(1264723200000L) // 2010-01-29 00:00 UTC, near the bundled keps' epoch
    private val lat = 51.5
    private val lon = -0.1

    /** QO-100 (Es'hail 2), geostationary at 25.9 E, from the Celestrak amateur list of November 2026. */
    private val qo100 = TLE.fromElements(
        OrbitalElements(
            name = "ES'HAIL 2", catnum = 43700, epochYear = 2026, epochDay = 308.11433122,
            meanMotion = 1.00272722, eccentricity = 0.00016, inclination = 0.0116, raan = 244.1212,
            argPerigee = 23.3346, meanAnomaly = 202.8356, elementSetNo = 999, revAtEpoch = 2908,
        )
    )
    private val qo100Start = Instant.parse("2026-11-04T12:00:00Z")

    private fun parallel(list: List<TLE>, at: Instant = start, hours: Int = 12) = runBlocking {
        OverviewCalculator(Dispatchers.Default).calculate(list, lat, lon, at, hours).toList()
    }

    @Test
    fun `parallel results equal sequential results`() {
        // Running in parallel must give the same answers as one at a time. Note this does not reliably catch a shared
        // ground station: the library writes `theta` into it and reads it back a few lines later, a race window too
        // narrow to hit in a test (tried: sharing one station still passed). One station per satellite is enforced
        // in OverviewCalculator.calculateOne.
        val calc = OverviewCalculator()
        val sequential = tles.map { calc.calculateOne(it, lat, lon, start, 12) }.associateBy { it.catnum }
        repeat(3) {
            val parallel = parallel(tles).associateBy { it.catnum }
            assertEquals(sequential.keys, parallel.keys)
            for ((catnum, expected) in sequential) assertEquals("satellite $catnum", expected, parallel[catnum])
        }
    }

    @Test
    fun `every satellite reports exactly once`() {
        val items = parallel(tles)
        assertEquals(tles.size, items.size)
        assertEquals(tles.map { it.catnum }.sorted(), items.map { it.catnum }.sorted())
    }

    @Test
    fun `passes are inside the window and sorted`() {
        val end = start.plusSeconds(12 * 3600L)
        val results = parallel(tles).filterIsInstance<OverviewItem.Result>()
        assertTrue("expected many satellites with passes", results.count { it.passes.isNotEmpty() } > 20)
        for (r in results) {
            assertEquals(r.passes.sortedBy { it.start }, r.passes)
            for (p in r.passes) {
                assertTrue("${r.name} pass starts after the window", p.start.isBefore(end))
                assertTrue("${r.name} pass ended before the window", p.end.isAfter(start))
                assertTrue(p.maxElevationDeg > 0.0)
            }
        }
    }

    @Test
    fun `passes agree with the passes screen calculation`() {
        val ao07 = tles.first { it.name == "AO-07" }
        val overview = OverviewCalculator().calculateOne(ao07, lat, lon, start, 24) as OverviewItem.Result
        val screen = PassCalculator().calculate(ao07, lat, lon, java.util.Date.from(start), 24) as CalcResult.Passes
        val screenInWindow = screen.passes.filter { it.startTime.toInstant().isBefore(start.plusSeconds(24 * 3600L)) }
        assertEquals(screenInWindow.map { it.startTime.toInstant() }, overview.passes.map { it.start })
        assertEquals(screenInWindow.map { it.maxEl }, overview.passes.map { it.maxElevationDeg })
    }

    @Test
    fun `satellites that never rise are skipped`() {
        // SO-33 has an inclination of 31 degrees, so it can never be seen from 85 degrees north.
        val so33 = tles.first { it.name == "SO-33" }
        val item = OverviewCalculator().calculateOne(so33, 85.0, 0.0, start, 12)
        assertEquals(OverviewItem.Skipped(so33.catnum), item)
    }

    @Test
    fun `up now has a position and the LOS of the pass in progress`() {
        // RS-22 rises at 00:00:05 and sets at 00:07:55 (from the 00:00 calculation), so at 00:03 it is mid-pass.
        val at = start.plusSeconds(180)
        val results = parallel(tles, at).filterIsInstance<OverviewItem.Result>()
        val rs22 = results.first { it.name == "RS-22" }
        assertTrue(rs22.now!!.elevation > 0.0)
        assertTrue(rs22.now.azimuth in 0.0..360.0)
        assertEquals(Instant.parse("2010-01-29T00:07:55Z"), rs22.losNow)
        // the pass in progress is listed first
        assertEquals(rs22.losNow, rs22.passes.first().end)
        for (r in results.filter { it.now != null }) {
            assertTrue(r.now!!.elevation > 0.0)
            r.losNow?.let { assertTrue("${r.name} LOS is in the past", it.isAfter(at)) }
        }
        // and a satellite that is down has no position
        assertTrue(results.any { it.now == null && it.losNow == null })
    }

    @Test(timeout = 10_000)
    fun `a geostationary satellite in view is listed as up now without searching for passes`() {
        // From London QO-100 is about 25 degrees up and never sets; the pass search would never return.
        val item = OverviewCalculator().calculateOne(qo100, lat, lon, qo100Start, 12)
        item as OverviewItem.Result
        val now = assertNotNull(item.now).let { item.now!! }
        assertTrue("elevation ${now.elevation}", now.elevation in 15.0..35.0)
        assertNull(item.losNow)
        assertTrue(item.passes.isEmpty())
    }

    @Test(timeout = 10_000)
    fun `a geostationary satellite out of view is skipped`() {
        // From Hawaii 25.9 E is on the other side of the Earth.
        val item = OverviewCalculator().calculateOne(qo100, 21.3, -157.8, qo100Start, 12)
        assertEquals(OverviewItem.Skipped(qo100.catnum), item)
    }

    @Test(timeout = 10_000)
    fun `cancelling stops the calculation early`() {
        // Taking the first few results cancels the rest; this must return promptly rather than finish all of them.
        val many = List(20) { tles }.flatten()
        val first = runBlocking {
            OverviewCalculator(Dispatchers.Default.limitedParallelism(1))
                .calculate(many, lat, lon, start, 24).take(3).toList()
        }
        assertEquals(3, first.size)
    }
}
