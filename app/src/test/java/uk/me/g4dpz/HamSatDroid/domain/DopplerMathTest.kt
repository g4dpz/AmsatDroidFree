package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.PassPredictor
import uk.me.g4dpz.satellite.SatelliteFactory
import uk.me.g4dpz.satellite.TLE
import java.io.File
import java.util.Date

class DopplerMathTest {

    private val tles = File("src/main/res/raw/nasabare.txt").inputStream().use { TLE.importSat(it) }
    private fun sat(name: String) = tles.first { it.name == name }

    @Test
    fun `agrees with PassPredictor for a golden satellite and time`() {
        // AO-07 during a known pass near the bundled keps' epoch.
        val tle = sat("AO-07")
        val station = GroundStationPosition(51.5, -0.1, 0.0)
        val predictor = PassPredictor(tle, station)
        val when435 = Date(1264723200000L + 5 * 3600_000L)
        val down = 435_000_000L
        val up = 145_900_000L

        // The range rate the sky screen uses: from the same SatPos it already computes each second.
        val rangeRate = SatelliteFactory.createSatellite(tle)
            .getPosition(GroundStationPosition(51.5, -0.1, 0.0), when435).rangeRate

        assertEquals(predictor.getDownlinkFreq(down, when435), DopplerMath.downlinkHz(down, rangeRate))
        assertEquals(predictor.getUplinkFreq(up, when435), DopplerMath.uplinkHz(up, rangeRate))
    }

    @Test
    fun `receding lowers the downlink and raises the uplink`() {
        val nominal = 435_000_000L
        val receding = 6.0 // km/s away
        assertTrue(DopplerMath.downlinkHz(nominal, receding) < nominal)
        assertTrue(DopplerMath.uplinkHz(nominal, receding) > nominal)
        assertTrue(DopplerMath.downlinkShiftHz(nominal, receding) < 0)
        assertTrue(DopplerMath.uplinkShiftHz(nominal, receding) > 0)
    }

    @Test
    fun `approaching raises the downlink and lowers the uplink`() {
        val nominal = 145_800_000L
        val approaching = -6.0
        assertTrue(DopplerMath.downlinkHz(nominal, approaching) > nominal)
        assertTrue(DopplerMath.uplinkHz(nominal, approaching) < nominal)
    }

    @Test
    fun `zero range rate leaves the frequency unchanged`() {
        assertEquals(435_000_000L, DopplerMath.downlinkHz(435_000_000L, 0.0))
        assertEquals(435_000_000L, DopplerMath.uplinkHz(435_000_000L, 0.0))
    }

    @Test
    fun `the shift is a few kHz on 435 MHz at low orbit speeds`() {
        // ~7.5 km/s radial would be an extreme; a typical max is a few km/s giving a few kHz.
        val shift = DopplerMath.downlinkShiftHz(435_000_000L, 3.0)
        assertTrue("shift was $shift", Math.abs(shift) in 3_000..6_000)
    }
}
