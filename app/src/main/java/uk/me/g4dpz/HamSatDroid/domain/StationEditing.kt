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

    /** [stations] without the station with [id]. */
    fun without(stations: List<SavedStation>, id: String): List<SavedStation> = stations.filter { it.id != id }
}
