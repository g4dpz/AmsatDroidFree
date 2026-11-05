// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.SatelliteFactory
import uk.me.g4dpz.satellite.TLE
import java.io.File
import java.time.Instant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SkyGeometryTest {

    /** The formulas exactly as the original SkyView.TrackView had them. */
    private fun legacyX(az: Double, el: Double, circleRadius: Double): Float {
        val piDivByTwo = Math.PI / 2.0
        val r = circleRadius * (piDivByTwo - el) / piDivByTwo
        return (r * Math.cos(piDivByTwo - az)).toFloat()
    }

    private fun legacyY(az: Double, el: Double, circleRadius: Double): Float {
        val piDivByTwo = Math.PI / 2.0
        val r = circleRadius * (piDivByTwo - el) / piDivByTwo
        return (r * Math.sin(piDivByTwo - az)).toFloat()
    }

    @Test
    fun matchesTheOriginalFormulasForManyDirections() {
        val rnd = java.util.Random(42)
        repeat(500) {
            val az = rnd.nextDouble() * 2 * PI
            val el = (rnd.nextDouble() - 0.1) * PI / 2
            val radius = 50.0 + rnd.nextDouble() * 400
            assertEquals(legacyX(az, el, radius), SkyGeometry.azElToX(az, el, radius), 0f)
            assertEquals(legacyY(az, el, radius), SkyGeometry.azElToY(az, el, radius), 0f)
        }
    }

    @Test
    fun theZenithIsTheCentre() {
        assertEquals(0f, SkyGeometry.azElToX(1.0, PI / 2, 100.0), 1e-4f)
        assertEquals(0f, SkyGeometry.azElToY(1.0, PI / 2, 100.0), 1e-4f)
    }

    @Test
    fun theHorizonIsOnTheCircleWithNorthAtTheTopAndEastOnTheRight() {
        val r = 100.0
        // north: straight up (positive y offset, which is -y on screen)
        assertEquals(0f, SkyGeometry.azElToX(0.0, 0.0, r), 1e-4f)
        assertEquals(100f, SkyGeometry.azElToY(0.0, 0.0, r), 1e-3f)
        // east: to the right
        assertEquals(100f, SkyGeometry.azElToX(PI / 2, 0.0, r), 1e-3f)
        assertEquals(0f, SkyGeometry.azElToY(PI / 2, 0.0, r), 1e-3f)
        // south: down; west: left
        assertEquals(-100f, SkyGeometry.azElToY(PI, 0.0, r), 1e-3f)
        assertEquals(-100f, SkyGeometry.azElToX(3 * PI / 2, 0.0, r), 1e-3f)
    }

    @Test
    fun fortyFiveDegreesElevationIsHalfwayOut() {
        val x = SkyGeometry.azElToX(PI / 2, PI / 4, 100.0)
        assertEquals(50f, x, 1e-3f)
    }

    @Test
    fun screenOffsetsFlipYSoNorthIsUp() {
        val offsets = SkyGeometry.toScreenOffsets(listOf(AzEl(0.0, 0.0), AzEl(PI / 2, 0.0)), 100.0)
        assertEquals(4, offsets.size)
        assertEquals(-100f, offsets[1], 1e-3f) // north is up: negative screen y
        assertEquals(100f, offsets[2], 1e-3f) // east is right
    }

    @Test
    fun chartRadiusUsesTheShorterSide() {
        assertEquals(35f, SkyGeometry.chartRadius(100f, 400f), 1e-4f)
        assertEquals(35f, SkyGeometry.chartRadius(400f, 100f), 1e-4f)
    }

    // ---- the track of a real pass ----

    private val tle: TLE = File("src/main/res/raw/nasabare.txt").inputStream().use { TLE.importSat(it) }.first { it.name == "AO-07" }
    private val station = { GroundStationPosition(52.0, -1.0, 0.0) }
    private val start = Instant.ofEpochMilli(1264723200000L)

    @Test
    fun trackHasOnePositionEveryFiveSecondsAndStopsBeforeTheEnd() {
        val sat = SatelliteFactory.createSatellite(tle)
        val track = SkyGeometry.computeTrack(sat, station(), start, start.plusSeconds(600))
        assertEquals(120, track.size) // 0, 5, ... 595
        val none = SkyGeometry.computeTrack(sat, station(), start, start)
        assertTrue(none.isEmpty())
        assertEquals(1, SkyGeometry.computeTrack(sat, station(), start, start.plusSeconds(1)).size)
    }

    @Test
    fun trackOfAPassRisesAndSetsAndMatchesThePassMaximumElevation() {
        val predictor = uk.me.g4dpz.satellite.PassPredictor(tle, station())
        val pass = predictor.getPasses(java.util.Date(start.toEpochMilli()), 12, true).first { it.maxEl > 20.0 }
        val sat = SatelliteFactory.createSatellite(tle)
        val track = SkyGeometry.computeTrack(sat, station(), pass.startTime.toInstant(), pass.endTime.toInstant())

        assertTrue("a pass is several minutes long", track.size > 30)
        // The track starts near the horizon, climbs, and ends near the horizon
        assertTrue("starts near the horizon: ${Math.toDegrees(track.first().elevation)}", Math.toDegrees(track.first().elevation) in -2.0..3.0)
        assertTrue("ends near the horizon: ${Math.toDegrees(track.last().elevation)}", Math.toDegrees(track.last().elevation) in -2.0..3.0)
        val maxEl = track.maxOf { Math.toDegrees(it.elevation) }
        assertEquals("highest point agrees with the pass", pass.maxEl, maxEl, 0.6)
        // elevation rises and then falls (single peak)
        val peak = track.indices.maxByOrNull { track[it].elevation }!!
        assertTrue((1..peak).all { track[it].elevation >= track[it - 1].elevation - 1e-6 })
        assertTrue((peak + 1 until track.size).all { track[it].elevation <= track[it - 1].elevation + 1e-6 })
    }

    @Test
    fun azimuthsStayInTheUsualRange() {
        val sat = SatelliteFactory.createSatellite(tle)
        val track = SkyGeometry.computeTrack(sat, station(), start, start.plusSeconds(3600))
        assertTrue(track.all { it.azimuth in 0.0..(2 * PI + 1e-9) })
        assertTrue(cos(track[0].azimuth).isFinite() && sin(track[0].azimuth).isFinite())
    }
}
