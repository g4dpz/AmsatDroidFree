// SPDX-License-Identifier: GPL-3.0-or-later
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

    // ---- the physics the corrections must satisfy, modelled directly ----

    /** What a signal sent at [hz] arrives at after crossing the link once, at range rate [rangeRateKmS]. */
    private fun afterOneHop(hz: Double, rangeRateKmS: Double): Double =
        hz * (1.0 - rangeRateKmS * 1000.0 / DopplerMath.SPEED_OF_LIGHT)

    /**
     * The corrections are first order and truncate to a whole hertz, so a round trip is off by the second-order term
     * (f v^2/c^2, about 0.2 Hz at 435 MHz and 6 km/s) plus up to a hertz per truncation. A few hertz of tolerance
     * separates that from the error being hunted, which is thousands of hertz.
     */
    private val roundTripToleranceHz = 3.0

    @Test
    fun `transmitting on the corrected uplink is heard at the nominal uplink, whatever the transponder does`() {
        // The satellite's receiver sees the uplink shift before the transponder touches the signal, so the frequency
        // to transmit on depends only on the range rate, not on whether the transponder inverts.
        val nominalUplink = 435_000_000L
        for (rangeRate in listOf(6.0, -6.0, 2.5, -0.7)) {
            val transmitted = DopplerMath.uplinkHz(nominalUplink, rangeRate)
            val heardAtSatellite = afterOneHop(transmitted.toDouble(), rangeRate)
            assertEquals("range rate $rangeRate km/s", nominalUplink.toDouble(), heardAtSatellite, roundTripToleranceHz)
        }
    }

    @Test
    fun `the station's own signal comes back at the displayed receive frequency for normal and inverting transponders`() {
        // A U/V transponder: uplink around 435 MHz, downlink around 145 MHz. Normal: out = in + k. Inverting:
        // out = k - in. Either way the downlink nominal is what the transponder makes of the nominal uplink, and the
        // signal arrives on the ground shifted by one more hop.
        val nominalUplink = 435_050_000L
        for (rangeRate in listOf(6.0, -6.0)) {
            val transmitted = DopplerMath.uplinkHz(nominalUplink, rangeRate).toDouble()
            val received = afterOneHop(transmitted, rangeRate)

            val kNormal = 145_900_000L - nominalUplink // out = in + k
            val normalNominalDownlink = nominalUplink + kNormal
            val normalArrival = afterOneHop(received + kNormal, rangeRate)
            assertEquals(
                "normal transponder, range rate $rangeRate",
                DopplerMath.downlinkHz(normalNominalDownlink, rangeRate).toDouble(), normalArrival, roundTripToleranceHz,
            )

            val kInverting = 435_050_000L + 145_950_000L // out = k - in
            val invertingNominalDownlink = kInverting - nominalUplink
            val invertingArrival = afterOneHop(kInverting - received, rangeRate)
            assertEquals(
                "inverting transponder, range rate $rangeRate",
                DopplerMath.downlinkHz(invertingNominalDownlink, rangeRate).toDouble(), invertingArrival, roundTripToleranceHz,
            )
        }
    }

    @Test
    fun `flipping the uplink correction for an inverting transponder would miss by twice the shift`() {
        // Documents why the earlier flip was wrong: transmitting on the downlink-style correction is heard at the
        // satellite about two shifts below nominal, not at nominal.
        val nominalUplink = 435_000_000L
        val rangeRate = 6.0
        val flipped = DopplerMath.downlinkHz(nominalUplink, rangeRate)
        val heardAtSatellite = afterOneHop(flipped.toDouble(), rangeRate)
        val shift = DopplerMath.uplinkShiftHz(nominalUplink, rangeRate)
        assertEquals(nominalUplink - 2.0 * shift, heardAtSatellite, roundTripToleranceHz)
        assertTrue("shift of $shift Hz should be thousands of Hz at 435 MHz", shift > 5_000)
    }
}
