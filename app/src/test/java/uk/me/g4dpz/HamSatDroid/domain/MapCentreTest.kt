// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MapCentreTest {
    @Test
    fun offKeepsTheOriginalMap() {
        assertEquals(0.0, MapCentre.longitude(false, "-33.87", "151.21"), 0.0)
    }

    @Test
    fun onCentresOnTheHomeLongitude() {
        assertEquals(151.21, MapCentre.longitude(true, "-33.87", "151.21"), 1e-9)
        assertEquals(-122.4, MapCentre.longitude(true, "37.8", "-122.4"), 1e-9)
    }

    @Test
    fun aCommaDecimalFromAnOldLocaleIsRead() {
        assertEquals(151.21, MapCentre.longitude(true, "-33,87", "151,21"), 1e-9)
    }

    @Test
    fun anUnsetOrBadLocationKeepsTheOriginalMap() {
        assertEquals(0.0, MapCentre.longitude(true, "0", "0"), 0.0)
        assertEquals(0.0, MapCentre.longitude(true, "", ""), 0.0)
        assertEquals(0.0, MapCentre.longitude(true, "abc", "151.2"), 0.0)
    }

    @Test
    fun aHomeOnTheMeridianWithALatitudeIsStillSet() {
        assertEquals(0.0, MapCentre.longitude(true, "51.5", "0"), 0.0)
    }
}
