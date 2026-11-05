// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.InvalidTleException
import uk.me.g4dpz.satellite.PassPredictor
import uk.me.g4dpz.satellite.SatNotFoundException
import uk.me.g4dpz.satellite.SatelliteFactory
import uk.me.g4dpz.satellite.TLE
import java.time.Instant
import java.util.Date

/** One satellite's part of the overview. */
sealed interface OverviewItem {
    val catnum: Int

    data class Result(
        override val catnum: Int,
        val name: String,
        /** Where it is now, if it is above the horizon; null if it is below. */
        val now: AzEl?,
        /** When the pass in progress ends, if it is up and that is known (null for a satellite that never sets). */
        val losNow: Instant?,
        /** Passes starting within the window (including one in progress at the start), sorted by AOS. */
        val passes: List<PassSummary>,
    ) : OverviewItem

    /** Left out: it never rises here, its elements are bad, or (geostationary) it is not up. */
    data class Skipped(override val catnum: Int) : OverviewItem
}

/**
 * Calculates passes of every satellite at once, for the overview screen.
 *
 * Each satellite runs in its own coroutine with **its own [PassPredictor] and its own [GroundStationPosition]**: the
 * prediction code writes into the ground station, so sharing one between threads would be a data race. Results are
 * emitted as each satellite finishes. Cancelling the collector cancels the work (each satellite checks between steps;
 * a single satellite's calculation, a few milliseconds, runs to the end).
 *
 * Satellites that hardly move in the sky (geostationary and similar, below [MIN_MEAN_MOTION] revolutions a day) are
 * never searched for passes: when one is above the horizon the pass search would never find it setting. They are
 * listed as "up now" when visible and left out otherwise.
 */
class OverviewCalculator(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    ),
) {

    fun calculate(
        tles: List<TLE>,
        latitude: Double,
        longitude: Double,
        start: Instant,
        hours: Int,
    ): Flow<OverviewItem> = channelFlow {
        // The flow completes when every satellite's coroutine has finished.
        for (tle in tles) {
            launch(dispatcher) {
                ensureActive()
                send(calculateOne(tle, latitude, longitude, start, hours))
            }
        }
    }

    /** One satellite, on the calling thread. Public so the parallel results can be compared with sequential ones. */
    fun calculateOne(tle: TLE, latitude: Double, longitude: Double, start: Instant, hours: Int): OverviewItem = try {
        val nowStation = GroundStationPosition(latitude, longitude, 0.0)
        val position = SatelliteFactory.createSatellite(tle).getPosition(nowStation, Date.from(start))
        val now = if (position.elevation > 0.0) {
            AzEl(Math.toDegrees(position.azimuth), Math.toDegrees(position.elevation))
        } else {
            null
        }

        if (tle.meanmo < MIN_MEAN_MOTION) {
            if (now == null) OverviewItem.Skipped(tle.catnum)
            else OverviewItem.Result(tle.catnum, tle.name, now, losNow = null, passes = emptyList())
        } else {
            val predictor = PassPredictor(tle, GroundStationPosition(latitude, longitude, 0.0))
            val end = start.plusSeconds(hours * 3600L)
            val passes = predictor.getPasses(Date.from(start), hours, true)
                // the library always returns one pass that starts after the window, and "none" as maxEl 0
                .filter { it.maxEl > 0.0 && it.startTime.toInstant().isBefore(end) && it.endTime.toInstant().isAfter(start) }
                .map { PassSummary.from(it, tle.catnum, tle.name) }
                .sortedBy { it.start }
            val losNow = if (now != null) passes.firstOrNull { it.isInProgress(start) }?.end else null
            OverviewItem.Result(tle.catnum, tle.name, now, losNow, passes)
        }
    } catch (e: SatNotFoundException) {
        OverviewItem.Skipped(tle.catnum)
    } catch (e: InvalidTleException) {
        OverviewItem.Skipped(tle.catnum)
    } catch (e: IllegalArgumentException) {
        OverviewItem.Skipped(tle.catnum)
    }

    companion object {
        /** Below this many revolutions a day a satellite is treated as stationary in the sky (geostationary is 1.0). */
        const val MIN_MEAN_MOTION = 1.5
    }
}
