// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.HamSatDroid.data.LocationText

/** Which longitude is in the middle of the world map. */
object MapCentre {
    /**
     * The home longitude when [centreOnHome] is on and the home location is set (see [HomeLocation.isSet]), otherwise 0,
     * the original map. [lonText] is the stored longitude text.
     */
    fun longitude(centreOnHome: Boolean, latText: String, lonText: String): Double {
        if (!centreOnHome || !HomeLocation.isSet(latText, lonText)) return 0.0
        return LocationText.toDegrees(lonText) ?: 0.0
    }
}
