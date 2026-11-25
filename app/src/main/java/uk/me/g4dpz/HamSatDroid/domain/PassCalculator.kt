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

    /** Passes exist, but none within [searchedHours] hours reaches [minElevationDeg] degrees. */
    data class NoPassReaches(val minElevationDeg: Int, val searchedHours: Int) : CalcResult

    /**
     * The satellite does not move across the sky (geostationary or similar), so there are no passes: it is either
     * always up at [position] or never visible from here.
     */
    data class Stationary(val position: SatelliteMotion.FixedPosition) : CalcResult

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
        if (SatelliteMotion.isStationary(tle)) {
            // A pass search would never find a horizon crossing; report where it sits instead.
            CalcResult.Stationary(SatelliteMotion.fixedPosition(tle, latitude, longitude, start))
        } else {
            val predictor = PassPredictor(tle, GroundStationPosition(latitude, longitude, 0.0))
            // Winding back (a quarter orbit, so a pass already in progress is found) can also find a pass that was
            // over before [start]; it is not one of the passes to come, so it is dropped. Should that leave nothing,
            // the search is made again from [start] without winding back.
            val passes = predictor.getPasses(start, hoursAhead, true).filter { it.maxEl == 0.0 || it.endTime.after(start) }
                .ifEmpty { predictor.getPasses(start, hoursAhead, false) }
            // The library reports "nothing found" as a single pass with zero maximum elevation.
            if (passes.isEmpty() || passes.first().maxEl == 0.0) CalcResult.NoPasses else CalcResult.Passes(passes)
        }
    } catch (e: InvalidTleException) {
        CalcResult.BadElements
    } catch (e: SatNotFoundException) {
        CalcResult.UnknownSatellite
    } catch (e: RuntimeException) {
        CalcResult.Error(e.message ?: e.javaClass.simpleName)
    }

    /**
     * The next pass that reaches at least [minElevationDeg] degrees, for "next pass only" with a minimum elevation: the
     * next pass may be too low, so the search goes on from one pass to the one after until one reaches the minimum or
     * [limitHours] hours after [start] have been searched ([CalcResult.NoPassReaches]).
     *
     * The first lookup winds back (so a pass already in progress at [start] is found); later ones start just after the
     * pass that was rejected, with winding back OFF, because winding back rewinds by a quarter orbit and would find the
     * same pass again instead of the next one. A pass that was already over at [start] (winding back can return one) is
     * skipped like a pass that is too low; one still in progress at [start] counts. [keepGoing] is asked between lookups so a cancelled calculation can stop
     * early (the library call itself cannot be interrupted); when it says no, [CalcResult.NoPasses] is returned.
     */
    open fun nextPassReaching(
        tle: TLE,
        latitude: Double,
        longitude: Double,
        start: Date,
        minElevationDeg: Int,
        limitHours: Int,
        keepGoing: () -> Boolean = { true },
    ): CalcResult = try {
        if (SatelliteMotion.isStationary(tle)) {
            CalcResult.Stationary(SatelliteMotion.fixedPosition(tle, latitude, longitude, start))
        } else {
            val predictor = PassPredictor(tle, GroundStationPosition(latitude, longitude, 0.0))
            val limit = start.time + limitHours * 3_600_000L
            var from = start
            var windBack = true
            var result: CalcResult? = null
            while (result == null) {
                if (!keepGoing()) {
                    result = CalcResult.NoPasses
                    break
                }
                val pass = predictor.getPasses(from, 0, windBack).firstOrNull()
                if (pass != null && pass.maxEl != 0.0 && pass.endTime.time <= start.time) {
                    // Winding back (a quarter orbit) can return a pass that was over before [start]: not the next pass.
                    from = Date(pass.endTime.time + 1_000L)
                    windBack = false
                } else if (pass == null || pass.maxEl == 0.0) {
                    // The library's "nothing found"
                    result = CalcResult.NoPasses
                } else if (pass.maxEl >= minElevationDeg) {
                    result = if (pass.startTime.time <= limit) CalcResult.Passes(listOf(pass)) else CalcResult.NoPassReaches(minElevationDeg, limitHours)
                } else if (pass.endTime.time >= limit) {
                    result = CalcResult.NoPassReaches(minElevationDeg, limitHours)
                } else {
                    from = Date(pass.endTime.time + 1_000L)
                    windBack = false
                }
            }
            result ?: CalcResult.NoPasses
        }
    } catch (e: InvalidTleException) {
        CalcResult.BadElements
    } catch (e: SatNotFoundException) {
        CalcResult.UnknownSatellite
    } catch (e: RuntimeException) {
        CalcResult.Error(e.message ?: e.javaClass.simpleName)
    }
}
