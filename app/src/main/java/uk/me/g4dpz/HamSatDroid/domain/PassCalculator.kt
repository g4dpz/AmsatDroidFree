// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.InvalidTleException
import uk.me.g4dpz.satellite.PassPredictor
import uk.me.g4dpz.satellite.SatNotFoundException
import uk.me.g4dpz.satellite.SatPassTime
import uk.me.g4dpz.satellite.TLE
import java.util.Date

/** The outcome of a pass calculation. */
sealed interface CalcResult {
    data class Passes(val passes: List<SatPassTime>) : CalcResult

    /** The satellite is visible from here but no pass starts in the following hours. */
    data object NoPasses : CalcResult

    data object BadElements : CalcResult
    data object UnknownSatellite : CalcResult
    data class Error(val message: String) : CalcResult
}

/**
 * Calculates passes of one satellite. This is the logic that used to live in the activity. A fresh
 * [GroundStationPosition] is made for every call because the prediction code writes into it.
 */
open class PassCalculator {

    /**
     * @param hoursAhead how far ahead to look for the first pass; 0 means the next pass only
     * @param start when to start looking
     */
    open fun calculate(tle: TLE, latitude: Double, longitude: Double, start: Date, hoursAhead: Int): CalcResult = try {
        val predictor = PassPredictor(tle, GroundStationPosition(latitude, longitude, 0.0))
        val passes = predictor.getPasses(start, hoursAhead, true)
        // The library reports "nothing found" as a single pass with zero maximum elevation.
        if (passes.isEmpty() || passes.first().maxEl == 0.0) CalcResult.NoPasses else CalcResult.Passes(passes)
    } catch (e: InvalidTleException) {
        CalcResult.BadElements
    } catch (e: SatNotFoundException) {
        CalcResult.UnknownSatellite
    } catch (e: RuntimeException) {
        CalcResult.Error(e.message ?: e.javaClass.simpleName)
    }
}
