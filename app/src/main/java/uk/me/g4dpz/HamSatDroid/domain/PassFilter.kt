// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

/** Which of the calculated passes the passes screen lists for a minimum elevation. */
object PassFilter {
    /**
     * The passes whose maximum elevation is at least [minElevationDeg] degrees, plus the pass with id [keepId] (the one
     * shown in the sky view) even when it is lower, so choosing a minimum never makes the sky view lose its pass. A
     * minimum of 0 or less lists every pass. Nothing is removed from [passes]: this only chooses what to show.
     */
    fun visible(passes: List<PassSummary>, minElevationDeg: Int, keepId: String?): List<PassSummary> =
        if (minElevationDeg <= 0) passes else passes.filter { it.maxElevationDeg >= minElevationDeg || it.id == keepId }

    /** True when [pass] is only listed because it is selected, that is, it is below [minElevationDeg]. */
    fun isBelowMinimum(pass: PassSummary, minElevationDeg: Int): Boolean =
        minElevationDeg > 0 && pass.maxElevationDeg < minElevationDeg
}
