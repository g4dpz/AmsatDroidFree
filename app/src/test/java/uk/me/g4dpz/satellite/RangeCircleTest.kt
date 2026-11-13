// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.satellite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.min

/**
 * The footprint circle drawn on the map. The south point (azimuth 180) must stay on the satellite's longitude; only
 * when the circle reaches over a pole does that pole's point move to the opposite longitude.
 */
class RangeCircleTest {

    private fun circle(latDeg: Double, lonDeg: Double, altitudeKm: Double): Array<DoubleArray> = SatPos().apply {
        latitude = Math.toRadians(latDeg)
        longitude = Math.toRadians(lonDeg)
        altitude = altitudeKm
    }.rangeCircle

    /** Half the footprint's angular width in degrees, as the circle code works it out. */
    private fun betaDeg(altitudeKm: Double): Double {
        val dia = (12756.33 * acos(6.378137E3 / (6.378137E3 + altitudeKm))).toInt()
        return Math.toDegrees(0.5 * dia / 6378.16)
    }

    private fun lonGap(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return min(d, 360.0 - d)
    }

    @Test
    fun southPointStaysOnTheSatelliteLongitudeWhenTheNorthPoleIsCovered() {
        // A polar satellite at 800 km and 75 N: its footprint (about 27 degrees) reaches over the north pole
        val c = circle(75.0, 10.0, 800.0)
        assertTrue(betaDeg(800.0) > 90.0 - 75.0)
        assertEquals(10.0, c[180][1], 0.5)
        assertEquals(75.0 - betaDeg(800.0), c[180][0], 0.01)
        // and the north point is on the far side of the pole
        assertEquals(190.0, c[0][1], 0.5)
    }

    @Test
    fun noPointNearTheBottomJumpsAcrossTheMap() {
        // the drawn line ran from the bottom point to the opposite longitude and back
        val c = circle(75.0, 10.0, 800.0)
        for (azi in 170..190) {
            assertTrue("azimuth $azi to ${azi + 1}", lonGap(c[azi][1], c[azi + 1][1]) < 10.0)
        }
    }

    @Test
    fun northPointStaysOnTheSatelliteLongitudeWhenTheSouthPoleIsCovered() {
        val c = circle(-75.0, 10.0, 800.0)
        assertEquals(10.0, c[0][1], 0.5)
        assertEquals(190.0, c[180][1], 0.5)
    }

    @Test
    fun topAndBottomPointsJoinTheirNeighboursEverywhere() {
        // From low orbits to geostationary, at every latitude: no half-world jump either side of the top or bottom
        // point (that jump is what drew the stray horizontal line on the map)
        for (alt in listOf(400.0, 800.0, 1450.0, 8000.0, 20000.0, 35786.0)) {
            for (lat in -85..85 step 5) {
                val c = circle(lat.toDouble(), 10.0, alt)
                for ((a, b) in listOf(179 to 180, 180 to 181, 359 to 0, 0 to 1)) {
                    // right by a pole the longitude legitimately swings fast, so only look at points away from it
                    if (abs(c[a][0]) > 87.0 || abs(c[b][0]) > 87.0) continue
                    assertTrue(
                        "alt $alt lat $lat azimuth $a to $b: ${c[a][1]} to ${c[b][1]}",
                        lonGap(c[a][1], c[b][1]) < 20.0,
                    )
                }
            }
        }
    }

    @Test
    fun ordinaryFootprintHasBothPointsOnTheSatelliteLongitude() {
        val c = circle(40.0, 10.0, 420.0)
        assertEquals(10.0, c[0][1], 0.5)
        assertEquals(10.0, c[180][1], 0.5)
    }
}
