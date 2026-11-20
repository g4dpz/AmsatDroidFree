// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.ui.SatelliteItem
import uk.me.g4dpz.satellite.TLE

class SatelliteChoiceTest {
    private fun tle(resource: String): TLE =
        checkNotNull(javaClass.getResourceAsStream("/$resource")) { "missing test resource $resource" }
            .use { TLE.importSat(it) }.first()
    private val rs44 = tle("rs44_20261114.tle")
    private val iss = tle("iss_20261114.tle")
    private val ao7 = tle("ao7_20261114.tle")
    private val allTles = listOf(rs44, iss, ao7)
    private val all = listOf(SatelliteItem(7530, "AO-07"), SatelliteItem(25544, "ISS"), SatelliteItem(44909, "RS-44"))
    private fun catnums(items: List<SatelliteItem>) = items.map { it.catnum }

    @Test fun offersTheChosenInTheOrderOfAll() =
        assertEquals(listOf(7530, 44909), catnums(SatelliteChoice.offered(all, setOf(44909, 7530), null)))
    @Test fun issOnly() = assertEquals(listOf(25544), catnums(SatelliteChoice.offered(all, setOf(25544), 25544)))
    @Test fun emptyListOffersOnlyTheSelectedSatellite() =
        assertEquals(listOf(44909), catnums(SatelliteChoice.offered(all, emptySet(), 44909)))
    @Test fun emptyListAndNothingSelectedOffersNothing() =
        assertEquals(emptyList<Int>(), catnums(SatelliteChoice.offered(all, emptySet(), null)))
    @Test fun selectedOutsideTheListIsStillOffered() =
        assertEquals(listOf(25544, 44909), catnums(SatelliteChoice.offered(all, setOf(25544), 44909)))
    @Test fun chosenButAbsentFromTheElementsIsNotOffered() =
        assertEquals(listOf(25544), catnums(SatelliteChoice.offered(all, setOf(25544, 99999), null)))
    @Test fun noneOfTheChosenArePresent() =
        assertEquals(emptyList<Int>(), catnums(SatelliteChoice.offered(all, setOf(1, 2), null)))

    @Test fun searchTakesTheChosenTles() {
        val s = SatelliteChoice.forSearch(allTles, setOf(25544, 7530))
        assertEquals(listOf(25544, 7530), s.tles.map { it.catnum })
        assertEquals(0, s.missing)
    }
    @Test fun searchCountsTheMissing() {
        val s = SatelliteChoice.forSearch(allTles, setOf(25544, 1, 2))
        assertEquals(listOf(25544), s.tles.map { it.catnum })
        assertEquals(2, s.missing)
    }
    @Test fun searchWithAnEmptyListFindsNothing() {
        val s = SatelliteChoice.forSearch(allTles, emptySet())
        assertEquals(emptyList<TLE>(), s.tles)
        assertEquals(0, s.missing)
    }
    @Test fun searchWhenNoneArePresent() {
        val s = SatelliteChoice.forSearch(allTles, setOf(1, 2))
        assertEquals(emptyList<TLE>(), s.tles)
        assertEquals(2, s.missing)
    }
}
