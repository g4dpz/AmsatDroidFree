// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.HamSatDroid.ui.SatelliteItem
import uk.me.g4dpz.satellite.TLE

/** The satellites to search and how many chosen ones the current elements do not have. */
data class Selection(val tles: List<TLE>, val missing: Int)

/** One rule for what the user is offered, used by the picker, the overview and the contact planner. Pure. */
object SatelliteChoice {
    /**
     * The satellites the picker offers: those whose catalogue number is in [chosen], plus the selected one (so the
     * picker never loses what is on screen), in [all]'s order. An empty [chosen] offers only the selected satellite.
     */
    fun offered(all: List<SatelliteItem>, chosen: Set<Int>, selectedCatnum: Int?): List<SatelliteItem> =
        all.filter { it.catnum in chosen || it.catnum == selectedCatnum }

    /** The chosen satellites that exist in [all], and the number of chosen ones that do not. */
    fun forSearch(all: List<TLE>, chosen: Set<Int>): Selection {
        val found = all.filter { it.catnum in chosen }
        return Selection(found, chosen.size - found.map { it.catnum }.toSet().size)
    }
}
