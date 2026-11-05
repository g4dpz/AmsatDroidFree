// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import java.time.Instant

/** A satellite above the horizon now, for the "Up now" list. */
data class UpNowRow(
    val catnum: Int,
    val name: String,
    val position: AzEl,
    /** When it sets, or null for a satellite that never does (geostationary). */
    val los: Instant?,
    /** The pass in progress, for opening the sky view; null for a geostationary satellite. */
    val pass: PassSummary?,
)

/** Turns the overview's per-satellite results into the lists the screen shows. Pure, so it can be tested. */
object OverviewRows {

    /**
     * Every pass that has not yet ended at [now] and reaches at least [minElevationDeg], from every satellite, sorted
     * by AOS (then by name, so equal times keep a stable order).
     */
    fun upcoming(results: Collection<OverviewItem.Result>, minElevationDeg: Int, now: Instant): List<PassSummary> =
        results.asSequence()
            .flatMap { it.passes.asSequence() }
            .filter { !it.isOver(now) && it.maxElevationDeg >= minElevationDeg }
            .sortedWith(compareBy<PassSummary> { it.start }.thenBy { it.satName })
            .toList()

    /**
     * The satellites that are up at [now]: those with a pass in progress, and stationary ones that were up when
     * calculated. Their positions come from [positionAt] (the screen recalculates them as time goes on); a satellite
     * whose position says it is below the horizon is left out.
     */
    fun upNow(
        results: Collection<OverviewItem.Result>,
        now: Instant,
        positionAt: (catnum: Int) -> AzEl?,
    ): List<UpNowRow> = results.mapNotNull { r ->
        val pass = r.passes.firstOrNull { it.isInProgress(now) }
        val stationary = r.passes.isEmpty() && r.now != null && r.losNow == null
        if (pass == null && !stationary) return@mapNotNull null
        val position = positionAt(r.catnum)?.takeIf { it.elevation > 0.0 } ?: return@mapNotNull null
        UpNowRow(r.catnum, r.name, position, pass?.end, pass)
    }.sortedByDescending { it.position.elevation }

    /** The satellites [upNow] would consider, so the caller only works out positions for those. */
    fun upNowCandidates(results: Collection<OverviewItem.Result>, now: Instant): List<OverviewItem.Result> =
        results.filter { r ->
            r.passes.any { it.isInProgress(now) } || (r.passes.isEmpty() && r.now != null && r.losNow == null)
        }
}
