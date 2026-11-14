// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import uk.me.g4dpz.satellite.TLE
import java.time.Instant

/**
 * Runs the contact planner over many satellites at once, for the "all satellites" search.
 *
 * The same shape as [OverviewCalculator]: each satellite runs in its own coroutine, and [ContactPlanner.windows] makes
 * its own ground stations and satellite, so nothing is shared between threads. Results are emitted as each satellite
 * finishes. Cancelling the collector cancels the work: a satellite not yet started never starts, and one already running
 * stops at its next checkpoint (inside the planner's own loops; a single pass search in the orbit library, a few
 * milliseconds, runs to the end).
 *
 * [onCheckpoint] is called at each checkpoint after the cancellation check; it exists so a test can watch the work stop.
 */
class ContactPlannerCalculator(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    ),
    private val onCheckpoint: () -> Unit = {},
) {
    fun calculate(
        tles: List<TLE>,
        home: PlannerStation,
        other: PlannerStation,
        minElevationDeg: Double,
        start: Instant,
        hours: Int,
    ): Flow<PlannerItem> = channelFlow {
        // The flow completes when every satellite's coroutine has finished.
        for (tle in tles) {
            launch(dispatcher) {
                ensureActive()
                send(
                    ContactPlanner.windows(tle, home, other, minElevationDeg, start, hours) {
                        ensureActive()
                        onCheckpoint()
                    }
                )
            }
        }
    }
}
