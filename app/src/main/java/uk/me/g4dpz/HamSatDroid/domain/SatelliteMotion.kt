// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.SatelliteFactory
import uk.me.g4dpz.satellite.TLE
import java.util.Date

/**
 * Tells satellites that hardly move in the sky (geostationary and similar) from those that pass over.
 *
 * A pass search makes no sense for a stationary satellite: from a given place it is either always above the horizon
 * or never, so `PassPredictor.nextSatPass` would look for a horizon crossing that never comes. Every caller that asks
 * for passes checks [isStationary] first and reports the satellite's fixed position instead. (The predictor also
 * bounds its own search, so a caller that forgets gets an exception rather than a hang.)
 */
object SatelliteMotion {

    /** Below this many revolutions a day a satellite is treated as stationary in the sky (geostationary is 1.0). */
    const val MIN_MEAN_MOTION = 1.5

    fun isStationary(tle: TLE): Boolean = tle.meanmo < MIN_MEAN_MOTION

    /** Where a stationary satellite sits as seen from [latitude]/[longitude] at [at], in degrees. */
    data class FixedPosition(val azimuthDeg: Double, val elevationDeg: Double) {
        val isUp: Boolean get() = elevationDeg > 0.0
    }

    /** A fresh ground station each time: the prediction code writes into it. */
    fun fixedPosition(tle: TLE, latitude: Double, longitude: Double, at: Date): FixedPosition {
        val pos = SatelliteFactory.createSatellite(tle).getPosition(GroundStationPosition(latitude, longitude, 0.0), at)
        return FixedPosition(Math.toDegrees(pos.azimuth), Math.toDegrees(pos.elevation))
    }
}
