// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

/**
 * The order satellites are listed in: those with an OSCAR designation first, by its number then its letters
 * (AO-7, UO-11, IO-26, FO-29, ..., SO-50, CO-55, ...), then the rest alphabetically by name. Case and spaces are
 * ignored when comparing names.
 *
 * The OSCAR number is read from the name, where it appears as a tag like "AO-7" or "(SO-50)": one or more letters,
 * a hyphen, then digits. Pure, so it is unit tested; the UI gives it the names.
 */
object SatelliteOrder {

    // An OSCAR designation is one or two letters ending in O, then a hyphen and a number, e.g. AO-7, SO-50, IO-117.
    // Requiring the trailing O keeps out other hyphen-number tags in names (KKS-1, UWE-3, RS-44, OTP-2).
    private val OSCAR = Regex("""\b([A-Z]?O)-(\d{1,3})\b""")

    /** The OSCAR designation in [name] as (letters, number), or null if it has none. */
    fun oscarTag(name: String): Pair<String, Int>? =
        OSCAR.findAll(name.uppercase())
            .map { it.groupValues[1] to it.groupValues[2].toInt() }
            // a name may contain more than one tag; the lowest number-then-letters wins, so the order is stable
            .minWithOrNull(compareBy({ it.second }, { it.first }))

    /** A comparator over anything, given how to get its name. */
    fun <T> byName(name: (T) -> String): Comparator<T> = Comparator { a, b ->
        val ta = oscarTag(name(a))
        val tb = oscarTag(name(b))
        when {
            ta != null && tb != null -> compareValuesBy(ta, tb, { it.second }, { it.first })
            ta != null -> -1 // OSCAR satellites come first
            tb != null -> 1
            else -> name(a).normalise().compareTo(name(b).normalise())
        }
    }

    private fun String.normalise(): String = uppercase().filter { !it.isWhitespace() }
}
