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

    @Test
    fun theSameResultComesFromSeparateCalls() {
        val a = PassCalculator().calculate(sat("FO-29"), 52.0, -1.0, start, 12) as CalcResult.Passes
        val b = PassCalculator().calculate(sat("FO-29"), 52.0, -1.0, start, 12) as CalcResult.Passes
        assertEquals(a.passes.map { it.startTime }, b.passes.map { it.startTime })
        assertEquals(a.passes.map { it.maxEl }, b.passes.map { it.maxEl })
    }
}
