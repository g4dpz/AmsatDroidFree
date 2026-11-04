package uk.me.g4dpz.HamSatDroid.domain

/**
 * First-order Doppler correction, using exactly the formulas of `PassPredictor.getDownlinkFreq` and `getUplinkFreq`
 * so the two always agree (a unit test checks this). Pure, so it can be unit tested on the JVM.
 *
 * [rangeRateKmS] is the rate of change of range in km/s as the prediction code reports it: positive while the
 * satellite is receding (so the downlink tunes lower), negative while it approaches. The downlink is the frequency to
 * tune to receive; the uplink is the frequency to transmit on so the satellite hears its nominal.
 */
object DopplerMath {

    /** Speed of light in m/s, the same constant the library uses. */
    const val SPEED_OF_LIGHT = 2.99792458E8

    fun downlinkHz(nominalHz: Long, rangeRateKmS: Double): Long =
        (nominalHz.toDouble() * (SPEED_OF_LIGHT - rangeRateKmS * 1000.0) / SPEED_OF_LIGHT).toLong()

    fun uplinkHz(nominalHz: Long, rangeRateKmS: Double): Long =
        (nominalHz.toDouble() * (SPEED_OF_LIGHT + rangeRateKmS * 1000.0) / SPEED_OF_LIGHT).toLong()

    /** The downlink shift (corrected minus nominal); negative while receding. */
    fun downlinkShiftHz(nominalHz: Long, rangeRateKmS: Double): Long = downlinkHz(nominalHz, rangeRateKmS) - nominalHz

    /** The uplink shift (corrected minus nominal); positive while receding. */
    fun uplinkShiftHz(nominalHz: Long, rangeRateKmS: Double): Long = uplinkHz(nominalHz, rangeRateKmS) - nominalHz

    /**
     * The uplink to transmit on so the satellite hears its nominal, for a transponder whose band may be inverted.
     *
     * The Doppler on a radio link depends only on the range rate, so the magnitude of the uplink shift is unchanged by
     * inversion. What inversion changes is which way the operator follows it: on a normal (NOR) transponder the uplink
     * is raised while the satellite recedes; on an inverting (REV) transponder the uplink is lowered instead, so a
     * single tuning knob keeps the pair in the passband. [inverting] true applies that flip.
     */
    fun uplinkHz(nominalHz: Long, rangeRateKmS: Double, inverting: Boolean): Long =
        if (inverting) downlinkHz(nominalHz, rangeRateKmS) else uplinkHz(nominalHz, rangeRateKmS)
}
