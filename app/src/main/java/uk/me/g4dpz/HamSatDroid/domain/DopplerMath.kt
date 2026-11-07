// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

/**
 * First-order Doppler correction, using exactly the formulas of `PassPredictor.getDownlinkFreq` and `getUplinkFreq`
 * so the two always agree (a unit test checks this). Pure, so it can be unit tested on the JVM.
 *
 * [rangeRateKmS] is the rate of change of range in km/s as the prediction code reports it: positive while the
 * satellite is receding (so the downlink tunes lower), negative while it approaches. The downlink is the frequency to
 * tune to receive; the uplink is the frequency to transmit on so the satellite hears its nominal.
 *
 * ### Why the uplink correction does not depend on the transponder's direction
 *
 * With range rate `v` (positive receding) and `c` the speed of light, a signal sent from the ground at `f` reaches the
 * satellite at `f (1 - v/c)`, and a signal the satellite sends at `g` reaches the ground at `g (1 - v/c)`. Both are
 * fixed by the geometry alone: the satellite's receiver sees the uplink shift before the transponder does anything
 * with it. So to be heard at the nominal uplink `fU` the station must transmit at `fU / (1 - v/c)`, to first order
 * `fU (1 + v/c)`, that is [uplinkHz], whether the transponder is normal or inverting.
 *
 * Inversion only decides where that signal comes out: a normal transponder emits `received + k`, an inverting one
 * `k - received`, and either way the result arrives on the ground at the nominal downlink times `(1 - v/c)`, which
 * is [downlinkHz]. `DopplerMathTest` models this round trip for both kinds.
 *
 * An earlier version flipped the uplink correction for inverting transponders (transmitting lower while receding).
 * That made the satellite hear `fU (1 - 2v/c)`: on a 435 MHz uplink at 6 km/s the station's own signal then came out
 * about 17 kHz away from the displayed receive frequency. The "tune the uplink the same way as the downlink"
 * convention that motivated it is about keeping two VFOs linked by hand, not about the physical correction, so it must
 * not be reintroduced here.
 */
object DopplerMath {

    /** Speed of light in m/s, the same constant the library uses. */
    const val SPEED_OF_LIGHT = 2.99792458E8

    /** The frequency to tune to hear a downlink whose nominal is [nominalHz]. */
    fun downlinkHz(nominalHz: Long, rangeRateKmS: Double): Long =
        (nominalHz.toDouble() * (SPEED_OF_LIGHT - rangeRateKmS * 1000.0) / SPEED_OF_LIGHT).toLong()

    /**
     * The frequency to transmit on so the satellite hears [nominalHz]. The same for normal and inverting
     * transponders; see the class comment.
     */
    fun uplinkHz(nominalHz: Long, rangeRateKmS: Double): Long =
        (nominalHz.toDouble() * (SPEED_OF_LIGHT + rangeRateKmS * 1000.0) / SPEED_OF_LIGHT).toLong()

    /** The downlink shift (corrected minus nominal); negative while receding. */
    fun downlinkShiftHz(nominalHz: Long, rangeRateKmS: Double): Long = downlinkHz(nominalHz, rangeRateKmS) - nominalHz

    /** The uplink shift (corrected minus nominal); positive while receding. */
    fun uplinkShiftHz(nominalHz: Long, rangeRateKmS: Double): Long = uplinkHz(nominalHz, rangeRateKmS) - nominalHz
}
