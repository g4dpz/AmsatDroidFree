// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

/**
 * The satellites a user has chosen, kept as a comma-separated list of catalogue numbers. Pure, so it is unit tested.
 *
 * Every valid number is kept, including ones no current keps source has, so the chooser can show them as "not in the
 * current elements" instead of silently losing them.
 */
object CatnumList {
    /** The International Space Station: the one satellite offered until the user chooses a list. */
    const val ISS = 25544

    /** The numbers in [text]: entries are trimmed; anything that is not a whole number above 0, and repeats, are dropped. */
    fun parse(text: String): Set<Int> =
        text.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n > 0 } }.toSet()

    /** Sorted, comma separated, without spaces; empty for an empty set. */
    fun format(catnums: Set<Int>): String = catnums.sorted().joinToString(",")
}
