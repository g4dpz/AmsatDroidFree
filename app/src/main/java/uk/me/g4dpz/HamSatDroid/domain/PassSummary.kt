// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.satellite.SatPassTime
import java.time.Duration
import java.time.Instant
import java.util.Date

/** Whether a pass crossed the direction of north or south in the sky. */
enum class PoleCrossing {
    NONE, NORTH, SOUTH;

    companion object {
        /** The library reports this as "north", "south" or "none". Anything else counts as none. */
        fun fromLibrary(text: String?): PoleCrossing = when (text?.lowercase()) {
            "north" -> NORTH
            "south" -> SOUTH
            else -> NONE
        }
    }

    fun toLibrary(): String = name.lowercase()
}

/**
 * One predicted pass, as plain data for the UI: no formatting, so screens can format it in the user's language.
 * Replaces the English text of `SatPassTime.toString()`.
 */
data class PassSummary(
    val catnum: Int,
    val satName: String,
    val start: Instant,
    val end: Instant,
    /** Time of closest approach. */
    val tca: Instant,
    val pole: PoleCrossing,
    val aosAzimuthDeg: Int,
    val losAzimuthDeg: Int,
    val maxElevationDeg: Double,
) {
    /** Identifies a pass across rotation and recalculation: the satellite and its start time (to the second). */
    val id: String get() = "$catnum@${start.epochSecond}"

    val duration: Duration get() = Duration.between(start, end)

    fun isOver(now: Instant): Boolean = !now.isBefore(end)

    fun isInProgress(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(end)

    fun toSatPassTime(): SatPassTime = SatPassTime(
        Date.from(start), Date.from(end), Date.from(tca), pole.toLibrary(),
        aosAzimuthDeg, losAzimuthDeg, maxElevationDeg,
    )

    companion object {
        fun from(pass: SatPassTime, catnum: Int, satName: String) = PassSummary(
            catnum = catnum,
            satName = satName,
            start = pass.startTime.toInstant(),
            end = pass.endTime.toInstant(),
            tca = pass.tca.toInstant(),
            pole = PoleCrossing.fromLibrary(pass.polePassed),
            aosAzimuthDeg = pass.aosAzimuth,
            losAzimuthDeg = pass.losAzimuth,
            maxElevationDeg = pass.maxEl,
        )
    }
}

/**
 * What the heading above the pass list should say, as data. The screens turn it into text with string resources.
 */
sealed interface PassHeader {
    /** Passes were found. [hoursAhead] is 0 for "next pass only". */
    data class Predictions(
        val satName: String,
        val latitude: Double,
        val longitude: Double,
        val locator: String,
        val hoursAhead: Int,
        val startTime: Instant,
    ) : PassHeader

    /** No pass in the following [searchedHours] hours. */
    data class NoPasses(
        val satName: String,
        val latitude: Double,
        val longitude: Double,
        val locator: String,
        val searchedHours: Int,
    ) : PassHeader

    /**
     * The satellite is stationary in the sky (geostationary or similar): no passes, it is always up at
     * [position] or never visible from here.
     */
    data class Stationary(val satName: String, val position: SatelliteMotion.FixedPosition) : PassHeader

    /** The elements for the satellite could not be used. */
    data object BadElements : PassHeader

    /** The satellite never rises above the horizon at this location. */
    data object UnknownSatellite : PassHeader

    /** The home latitude or longitude in settings is not a number. */
    data object InvalidLocation : PassHeader
    /** The home location has not been set (it is still the default 0, 0), so nothing is calculated. */
    data object LocationNotSet : PassHeader

    /** The list shown was restored from the previous session; the location it was calculated for is not kept. */
    data class Restored(val satName: String, val computedAt: Instant) : PassHeader

    /** Something unexpected went wrong during the calculation. */
    data class Error(val message: String) : PassHeader
}
