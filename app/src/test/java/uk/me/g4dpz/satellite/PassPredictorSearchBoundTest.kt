// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.satellite

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Date

/**
 * The horizon search in [PassPredictor.nextSatPass] must give up for a satellite that never crosses the horizon.
 * Before the bound was added these calls looped for ever: every step of 60 s of satellite time found the same
 * answer, and nothing ever stopped the loop.
 */
class PassPredictorSearchBoundTest {

    /** QO-100 (Es'hail 2), geostationary at 25.9 E, from the Celestrak amateur list of November 2026. */
    private val qo100 = TLE.fromElements(
        OrbitalElements(
            name = "ES'HAIL 2", catnum = 43700, epochYear = 2026, epochDay = 308.11433122,
            meanMotion = 1.00272722, eccentricity = 0.00016, inclination = 0.0116, raan = 244.1212,
            argPerigee = 23.3346, meanAnomaly = 202.8356, elementSetNo = 999, revAtEpoch = 2908,
        )
    )
    private val at = Date(1793880000000L) // 2026-11-04T12:00:00Z

    private fun elevationFrom(latitude: Double, longitude: Double): Double =
        SatelliteFactory.createSatellite(qo100).getPosition(GroundStationPosition(latitude, longitude, 0.0), at).elevation

    private fun assertGivesUp(latitude: Double, longitude: Double) {
        val predictor = PassPredictor(qo100, GroundStationPosition(latitude, longitude, 0.0))
        val started = System.nanoTime()
        try {
            predictor.nextSatPass(at)
            fail("expected SatNotFoundException: a geostationary satellite has no pass")
        } catch (expected: SatNotFoundException) {
            assertTrue(expected.message!!, expected.message!!.contains("No horizon crossing"))
        }
        val seconds = (System.nanoTime() - started) / 1e9
        assertTrue("gave up after $seconds s; the bound should keep this to a few seconds", seconds < 30.0)
    }

    @Test
    fun `always above the horizon - gives up instead of searching for a setting that never comes`() {
        assertTrue("fixture: QO-100 is up from London", elevationFrom(51.5, -0.1) > 0.0)
        assertGivesUp(51.5, -0.1)
    }

    @Test
    fun `never above the horizon - gives up instead of searching for a rising that never comes`() {
        assertTrue("fixture: QO-100 is below the horizon from Hawaii", elevationFrom(21.3, -157.8) < 0.0)
        assertGivesUp(21.3, -157.8)
    }

    @Test
    fun `getPasses is bounded too`() {
        val predictor = PassPredictor(qo100, GroundStationPosition(51.5, -0.1, 0.0))
        try {
            predictor.getPasses(at, 24, true)
            fail("expected SatNotFoundException")
        } catch (expected: SatNotFoundException) {
            // the bound applies to every nextSatPass call that getPasses makes
        }
    }
}
