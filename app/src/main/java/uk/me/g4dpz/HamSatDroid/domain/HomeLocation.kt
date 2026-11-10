// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.HamSatDroid.data.LocationText

/** Whether the stored home location has been set by the user, without any Android dependency. */
object HomeLocation {
    /**
     * True unless the location is missing, not a number, or exactly 0, 0 (the stored default, a point in the Gulf of
     * Guinea where nobody is using this app).
     */
    fun isSet(latText: String, lonText: String): Boolean {
        val lat = LocationText.toDegrees(latText)
        val lon = LocationText.toDegrees(lonText)
        return !(lat == null || lon == null || (lat == 0.0 && lon == 0.0))
    }
}
