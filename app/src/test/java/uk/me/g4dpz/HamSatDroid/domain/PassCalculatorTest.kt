// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.satellite.TLE
import java.io.File
import java.util.Date

class PassCalculatorTest {

    private val tles = File("src/main/res/raw/nasabare.txt").inputStream().use { TLE.importSat(it) }
    private fun sat(name: String) = tles.first { it.name == name }
    private val start = Date(1264723200000L) // 2010-01-29 00:00 UTC, near the epoch of the bundled keps

    @Test
    fun findsPassesForAVisibleSatellite() {
        val result = PassCalculator().calculate(sat("AO-07"), 51.5, -0.1, start, 24)
        assertTrue(result is CalcResult.Passes)
        val passes = (result as CalcResult.Passes).passes
        assertTrue("expected several passes in 24 hours, got ${passes.size}", passes.size >= 3)
        assertTrue(passes.all { it.maxEl > 0.0 })
        assertEquals(passes.sortedBy { it.startTime }, passes)
    }

    @Test
    fun nextPassOnlyGivesAtLeastOnePass() {
        val result = PassCalculator().calculate(sat("AO-07"), 51.5, -0.1, start, 0)
        assertTrue(result is CalcResult.Passes)
        assertTrue((result as CalcResult.Passes).passes.isNotEmpty())
    }

    @Test
    fun aSatelliteThatNeverRisesIsReportedAsUnknown() {
        // SO-33 has an inclination of 31 degrees, so it can never be seen from 85 degrees north.
        assertEquals(CalcResult.UnknownSatellite, PassCalculator().calculate(sat("SO-33"), 85.0, 0.0, start, 24))
    }

    /** QO-100 (Es'hail 2), geostationary at 25.9 E, from the Celestrak amateur list of November 2026. */
    private val qo100 = TLE.fromElements(
        uk.me.g4dpz.satellite.OrbitalElements(
            name = "ES'HAIL 2", catnum = 43700, epochYear = 2026, epochDay = 308.11433122,
            meanMotion = 1.00272722, eccentricity = 0.00016, inclination = 0.0116, raan = 244.1212,
            argPerigee = 23.3346, meanAnomaly = 202.8356, elementSetNo = 999, revAtEpoch = 2908,
        )
    )
    private val nov2026 = Date(1793880000000L) // 2026-11-04T12:00:00Z

    @Test
    fun aGeostationarySatelliteThatIsUpIsReportedAsStationaryWithItsPosition() {
        // Before this guard the call never returned: the predictor searched for a setting that never comes.
        val result = PassCalculator().calculate(qo100, 51.5, -0.1, nov2026, 24)
        assertTrue("got $result", result is CalcResult.Stationary)
        val position = (result as CalcResult.Stationary).position
        assertTrue(position.isUp)
        // From London QO-100 sits low in the south-east.
        assertTrue("azimuth ${position.azimuthDeg}", position.azimuthDeg in 130.0..160.0)
        assertTrue("elevation ${position.elevationDeg}", position.elevationDeg in 15.0..30.0)
    }

    @Test
    fun aGeostationarySatelliteBelowTheHorizonIsReportedAsStationaryAndDown() {
        val result = PassCalculator().calculate(qo100, 21.3, -157.8, nov2026, 24) // Hawaii
        assertTrue("got $result", result is CalcResult.Stationary)
        assertTrue(!(result as CalcResult.Stationary).position.isUp)
    }

    @Test
    fun theStationaryThresholdMatchesTheOverview() {
        assertEquals(OverviewCalculator.MIN_MEAN_MOTION, SatelliteMotion.MIN_MEAN_MOTION, 0.0)
        assertTrue(SatelliteMotion.isStationary(qo100))
        assertTrue(!SatelliteMotion.isStationary(sat("AO-07")))
    }

    @Test
    fun theSameResultComesFromSeparateCalls() {
        val a = PassCalculator().calculate(sat("FO-29"), 52.0, -1.0, start, 12) as CalcResult.Passes
        val b = PassCalculator().calculate(sat("FO-29"), 52.0, -1.0, start, 12) as CalcResult.Passes
        assertEquals(a.passes.map { it.startTime }, b.passes.map { it.startTime })
        assertEquals(a.passes.map { it.maxEl }, b.passes.map { it.maxEl })
    }

    // ---- the next pass that reaches a minimum elevation ----

    private fun passesIn24Hours(name: String) =
        (PassCalculator().calculate(sat(name), 51.5, -0.1, start, 24) as CalcResult.Passes).passes

    @Test
    fun withAMinimumOfZeroTheNextPassIsJustTheNextPass() {
        val expected = (PassCalculator().calculate(sat("AO-07"), 51.5, -0.1, start, 0) as CalcResult.Passes).passes.first()
        val result = PassCalculator().nextPassReaching(sat("AO-07"), 51.5, -0.1, start, 0, 24) as CalcResult.Passes
        assertEquals(expected.startTime, result.passes.single().startTime)
    }

    /** The same pass found from a different start time can begin a few seconds earlier or later. */
    private fun assertSamePass(expected: uk.me.g4dpz.satellite.SatPassTime, actual: uk.me.g4dpz.satellite.SatPassTime) {
        val seconds = kotlin.math.abs(expected.startTime.time - actual.startTime.time) / 1000
        assertTrue("start times differ by $seconds s: ${expected.startTime} and ${actual.startTime}", seconds <= 30)
    }

    @Test
    fun skipsLowPassesToTheFirstOneThatReachesTheMinimum() {
        val all = passesIn24Hours("AO-07")
        // a minimum that the first pass misses
        val minimum = kotlin.math.ceil(all.first().maxEl + 0.5).toInt()
        val expected = all.firstOrNull { it.maxEl >= minimum }
        assertTrue("the test needs a later pass above $minimum", expected != null)
        val result = PassCalculator().nextPassReaching(sat("AO-07"), 51.5, -0.1, start, minimum, 24)
        assertTrue("got $result", result is CalcResult.Passes)
        val found = (result as CalcResult.Passes).passes.single()
        assertTrue(found.maxEl >= minimum)
        assertSamePass(expected!!, found)
        assertTrue("it must be a later pass than the first", found.startTime.time > all.first().startTime.time + 60_000)
    }

    @Test
    fun rejectingAPassMovesOnToADistinctLaterPass() {
        val all = passesIn24Hours("AO-07")
        val best = all.maxByOrNull { it.maxEl }!!
        val others = all.filter { it !== best }
        val minimum = kotlin.math.floor(best.maxEl).toInt()
        assertTrue("only the best pass may reach $minimum", others.none { it.maxEl >= minimum })
        val found = (PassCalculator().nextPassReaching(sat("AO-07"), 51.5, -0.1, start, minimum, 24) as CalcResult.Passes).passes.single()
        assertSamePass(best, found)
        // a minimum of 0 finds the very first pass, the one that was rejected above
        val first = (PassCalculator().nextPassReaching(sat("AO-07"), 51.5, -0.1, start, 0, 24) as CalcResult.Passes).passes.single()
        assertSamePass(all.first(), first)
    }

    @Test
    fun saysSoWhenNoPassInTheLimitReachesTheMinimum() {
        // no pass is ever above 89 degrees in 24 hours of AO-07 from London
        val result = PassCalculator().nextPassReaching(sat("AO-07"), 51.5, -0.1, start, 89, 24)
        assertEquals(CalcResult.NoPassReaches(89, 24), result)
    }

    @Test
    fun aShortLimitStopsTheSearchEarly() {
        val all = passesIn24Hours("AO-07")
        val last = all.maxByOrNull { it.maxEl }!!
        val hoursToBest = ((last.startTime.time - start.time) / 3_600_000L).toInt()
        // a limit shorter than the wait for the best pass cannot find it
        if (hoursToBest >= 2) {
            val minimum = kotlin.math.floor(last.maxEl).toInt()
            val earlier = all.filter { it.startTime.time < start.time + 3_600_000L }
            if (earlier.none { it.maxEl >= minimum }) {
                assertEquals(CalcResult.NoPassReaches(minimum, 1), PassCalculator().nextPassReaching(sat("AO-07"), 51.5, -0.1, start, minimum, 1))
            }
        }
    }

    @Test
    fun stopsWhenAskedNotToKeepGoing() {
        var asked = 0
        val result = PassCalculator().nextPassReaching(sat("AO-07"), 51.5, -0.1, start, 89, 24, keepGoing = { asked++ < 1 })
        assertEquals(CalcResult.NoPasses, result)
        assertTrue(asked >= 2)
    }

    @Test
    fun aStationarySatelliteIsReportedAsStationary() {
        assertTrue(PassCalculator().nextPassReaching(qo100, 51.5, -0.1, nov2026, 30, 24) is CalcResult.Stationary)
    }
}
