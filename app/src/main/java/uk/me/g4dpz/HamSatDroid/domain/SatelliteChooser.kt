// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.HamSatDroid.ui.SatelliteItem

/** What the "Choose satellites" screen shows and how its buttons change the draft list. Pure, so it is unit tested. */
object SatelliteChooser {
    /** Whether [item] matches the search [query]: its name or catalogue number contains it, ignoring case. A blank query matches all. */
    fun matches(item: SatelliteItem, query: String): Boolean = matchesText(item.name, item.catnum, query)

    private fun matchesText(name: String?, catnum: Int, query: String): Boolean {
        val q = query.trim()
        return q.isEmpty() || catnum.toString().contains(q) || (name?.contains(q, ignoreCase = true) ?: false)
    }

    /**
     * The satellites to list for [query]: those that were chosen when the screen opened ([initial]) first, then the rest,
     * each group in [all]'s order. Based on [initial] and not the draft so that ticking a row does not move it.
     */
    fun shown(all: List<SatelliteItem>, initial: Set<Int>, query: String): List<SatelliteItem> {
        // A catalogue number can appear twice in a keps file; the list shows (and keys) each one once.
        val found = all.distinctBy { it.catnum }.filter { matches(it, query) }
        return found.filter { it.catnum in initial } + found.filter { it.catnum !in initial }
    }

    /** The chosen catalogue numbers that are not in [all] (not in the current elements) and match [query], sorted. */
    fun notInElements(all: List<SatelliteItem>, draft: Set<Int>, query: String): List<Int> {
        val present = all.map { it.catnum }.toSet()
        return (draft - present).filter { matchesText(null, it, query) }.sorted()
    }

    /** Adds every satellite in [shown] to [draft]. */
    fun selectAll(draft: Set<Int>, shown: List<SatelliteItem>): Set<Int> = draft + shown.map { it.catnum }

    /** Removes every satellite in [shown], and every number in [shownAbsent], from [draft]. */
    fun clearAll(draft: Set<Int>, shown: List<SatelliteItem>, shownAbsent: List<Int>): Set<Int> =
        draft - shown.map { it.catnum }.toSet() - shownAbsent.toSet()

    /** [draft] with [catnum] ticked or unticked. */
    fun toggle(draft: Set<Int>, catnum: Int): Set<Int> = if (catnum in draft) draft - catnum else draft + catnum
}
