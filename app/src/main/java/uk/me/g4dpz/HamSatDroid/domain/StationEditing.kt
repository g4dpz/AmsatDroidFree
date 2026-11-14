// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.HamSatDroid.data.SavedStation

/**
 * The rules of the contact planner's station editor, without any screen: 4-character locator squares, what can be
 * saved, and adding, replacing and deleting saved stations.
 */
object StationEditing {
    private val SQUARE = Regex("^[A-Ra-r]{2}[0-9]{2}$")

    /** The ending that turns a 4-character square into the 6-character locator at its centre. */
    private const val CENTRE = "mm"

    /** Whether [text] is a 4-character square such as EM29 (spaces around it are ignored). */
    fun isSquare(text: String): Boolean = SQUARE.matches(text.trim())

    /** A square such as EM29 becomes EM29mm, the locator at its centre; any other text is returned as it is. */
    fun expandSquare(text: String): String = if (isSquare(text)) text.trim() + CENTRE else text

    /** The locator rule of Settings, with a 4-character square accepted as well. */
    fun editLocator(text: String, current: LocatorSync.Location): LocatorSync.Result =
        LocatorSync.editLocator(expandSquare(text), current)

    /** A station can be saved only with a real position: 0, 0 is how an unset position looks (requirement 2.3). */
    fun canSave(location: LocatorSync.Location): Boolean = HomeLocation.isSet(location.latitude, location.longitude)

    fun isFull(stations: List<SavedStation>): Boolean = stations.size >= SavedStation.MAX_STATIONS

    /**
     * [stations] with [station] added at the end, or replacing the station with the same id; null when this would add
     * an eleventh station.
     */
    fun saved(stations: List<SavedStation>, station: SavedStation): List<SavedStation>? =
        if (stations.any { it.id == station.id }) {
            stations.map { if (it.id == station.id) station else it }
        } else if (isFull(stations)) {
            null
        } else {
            stations + station
        }

    /**
     * The position fields of the editor: what is committed ([location]) and what each field currently shows. A field is
     * committed when the user finishes with it (keyboard Done, or the focus moving on). Every commit, accepted or
     * rejected, shows the committed position in all three fields again, so a field never keeps text that no longer
     * matches the position (for example `em29mm` where the position says `EM29mm`).
     */
    data class Draft(
        val location: LocatorSync.Location,
        val locator: String = location.locator,
        val latitude: String = location.latitude,
        val longitude: String = location.longitude,
        /** The 4-character square the locator came from, for the line under the field; null otherwise. */
        val squareNote: String? = null,
    ) {
        /** The result of a commit: the new draft, and the field that was rejected, if any. */
        data class Committed(val draft: Draft, val rejected: LocatorSync.Field?)

        fun typeLocator(text: String) = copy(locator = text)

        fun typeLatitude(text: String) = copy(latitude = text)

        fun typeLongitude(text: String) = copy(longitude = text)

        fun commitLocator(): Committed {
            if (locator == location.locator) return Committed(this, null)
            val square = if (isSquare(locator)) locator.trim().uppercase() else null
            return take(editLocator(locator, location), square)
        }

        fun commitLatitude(): Committed =
            if (latitude == location.latitude) Committed(this, null) else take(LocatorSync.editLatitude(latitude, location), null)

        fun commitLongitude(): Committed =
            if (longitude == location.longitude) Committed(this, null) else take(LocatorSync.editLongitude(longitude, location), null)

        /**
         * Commits the field that has an edit not yet committed. Leaving a field commits it, so there is at most one;
         * this is what Save does first, in case the last field was never left.
         */
        fun commitPending(): Committed = when {
            locator != location.locator -> commitLocator()
            latitude != location.latitude -> commitLatitude()
            longitude != location.longitude -> commitLongitude()
            else -> Committed(this, null)
        }

        val canSave: Boolean get() = canSave(location)

        private fun take(result: LocatorSync.Result, square: String?): Committed {
            val (l, rejected) = when (result) {
                is LocatorSync.Result.Accepted -> result.location to null
                is LocatorSync.Result.Rejected -> result.location to result.field
            }
            return Committed(Draft(location = l, squareNote = square), rejected)
        }
    }

    /** [stations] without the station with [id]. */
    fun without(stations: List<SavedStation>, id: String): List<SavedStation> = stations.filter { it.id != id }
}
