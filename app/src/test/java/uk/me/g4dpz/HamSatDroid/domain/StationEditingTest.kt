// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.data.SavedStation

class StationEditingTest {
    private val blank = LocatorSync.Location(LocatorSync.ZERO, LocatorSync.ZERO, LocatorSync.DEFAULT_LOCATOR)

    private fun station(n: Int) = SavedStation("id-$n", "S$n", "10", "20", "JK00aa")

    // ---- 4-character squares ----

    @Test
    fun `a four-character square is recognised, in either case and with spaces around it`() {
        for (text in listOf("EM29", "em29", "Em29", " EM29 ", "IO82", "AA00", "RR99")) assertTrue(text, StationEditing.isSquare(text))
        for (text in listOf("", "EM2", "EM29m", "EM29mm", "EM29mmaa", "SS00", "E129", "EMAB", "EM 29", "29EM")) {
            assertFalse(text, StationEditing.isSquare(text))
        }
    }

    @Test
    fun `a square becomes the locator at its centre and other text is left alone`() {
        assertEquals("EM29mm", StationEditing.expandSquare("EM29"))
        assertEquals("em29mm", StationEditing.expandSquare(" em29 "))
        assertEquals("EM29mm", StationEditing.expandSquare("EM29mm"))
        assertEquals("EM29mm44", StationEditing.expandSquare("EM29mm44"))
        assertEquals("rubbish", StationEditing.expandSquare("rubbish"))
    }

    @Test
    fun `typing EM29 sets the position at the centre of the square`() {
        val result = StationEditing.editLocator("EM29", blank) as LocatorSync.Result.Accepted
        assertEquals("EM29mm", result.location.locator)
        assertEquals(39.5, result.location.latitude.toDouble(), 0.05) // the square is 39 to 40 degrees north
        assertEquals(-95.0, result.location.longitude.toDouble(), 0.05) // and 96 to 94 degrees west
    }

    @Test
    fun `six- and eight-character locators work as in Settings, and bad text is rejected`() {
        val six = StationEditing.editLocator("IO82xk", blank) as LocatorSync.Result.Accepted
        assertEquals("IO82xk", six.location.locator)
        val eight = StationEditing.editLocator("IO82xk44", blank) as LocatorSync.Result.Accepted
        assertEquals("accepted, and kept as the 6-character locator Settings keeps", "IO82xk", eight.location.locator)
        val bad = StationEditing.editLocator("EM2", blank)
        assertTrue(bad is LocatorSync.Result.Rejected && bad.field == LocatorSync.Field.LOCATOR)
    }

    // ---- what can be saved ----

    @Test
    fun `a station needs a real position`() {
        assertFalse("0, 0 is an unset position", StationEditing.canSave(blank))
        assertTrue(StationEditing.canSave(LocatorSync.Location("39.5", "-95", "EM29mm")))
        assertTrue(StationEditing.canSave(LocatorSync.Location("0", "10", "JJ00ga")))
        assertTrue(StationEditing.canSave(LocatorSync.Location("-33.87", "151.21", "QF56od")))
        val square = StationEditing.editLocator("EM29", blank) as LocatorSync.Result.Accepted
        assertTrue(StationEditing.canSave(square.location))
    }

    // ---- the list ----

    @Test
    fun `a new station is added at the end`() {
        val list = listOf(station(1), station(2))
        assertEquals(listOf(station(1), station(2), station(3)), StationEditing.saved(list, station(3)))
        assertEquals(listOf(station(1)), StationEditing.saved(emptyList(), station(1)))
    }

    @Test
    fun `a station with a known id replaces that station in place`() {
        val list = listOf(station(1), station(2), station(3))
        val edited = station(2).copy(name = "Renamed", latitude = "11")
        assertEquals(listOf(station(1), edited, station(3)), StationEditing.saved(list, edited))
    }

    @Test
    fun `no eleventh station can be added, but a full list can still be edited`() {
        val full = List(SavedStation.MAX_STATIONS) { station(it) }
        assertTrue(StationEditing.isFull(full))
        assertFalse(StationEditing.isFull(full.dropLast(1)))
        assertNull(StationEditing.saved(full, station(99)))
        val edited = station(4).copy(name = "Edited")
        assertEquals(edited, StationEditing.saved(full, edited)!![4])
        assertEquals(SavedStation.MAX_STATIONS, StationEditing.saved(full, edited)!!.size)
    }

    @Test
    fun `deleting removes only that station and ignores an unknown id`() {
        val list = listOf(station(1), station(2), station(3))
        assertEquals(listOf(station(1), station(3)), StationEditing.without(list, "id-2"))
        assertEquals(list, StationEditing.without(list, "nobody"))
        assertEquals(emptyList<SavedStation>(), StationEditing.without(listOf(station(1)), "id-1"))
    }
}
