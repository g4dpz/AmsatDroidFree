// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.satellite.TLE
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * How old a satellite's orbital elements are. Predictions from old elements drift, and for low satellites with drag
 * they soon become meaningless, so the passes screen warns when the elements are older than [STALE_AFTER_DAYS].
 */
object ElementAge {

    /** Elements older than this many days get a warning. */
    const val STALE_AFTER_DAYS = 30

    private const val MILLIS_PER_DAY = 86_400_000.0

    /**
     * The element set's epoch in milliseconds since 1970 (UTC). TLE years have two digits: 57 to 99 are 1957 to 1999,
     * 00 to 56 are 2000 to 2056 (the usual TLE convention). Day 1.0 is midnight at the start of 1 January.
     */
    fun epochMillis(twoDigitYear: Int, epochDay: Double): Long {
        val year = if (twoDigitYear < 57) 2000 + twoDigitYear else 1900 + twoDigitYear
        val startOfYear = LocalDate.of(year, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return startOfYear + ((epochDay - 1.0) * MILLIS_PER_DAY).toLong()
    }

    fun epochMillis(tle: TLE): Long = epochMillis(tle.year, tle.refepoch)

    /** Whole days from the epoch to [nowMillis] (negative if the epoch is in the future). */
    fun ageDays(epochMillis: Long, nowMillis: Long): Int = Math.floorDiv(nowMillis - epochMillis, 86_400_000L).toInt()

    /** The age in days when the elements are older than [STALE_AFTER_DAYS], otherwise null. */
    fun staleDays(epochMillis: Long, nowMillis: Long): Int? =
        ageDays(epochMillis, nowMillis).takeIf { it > STALE_AFTER_DAYS }
}
