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

    // ---- the fields of the editor ----

    private val kansasCity = LocatorSync.Location("39.5", "-95", "EM29mm")

    @Test
    fun `a new draft shows the position it starts with`() {
        val d = StationEditing.Draft(kansasCity)
        assertEquals(Triple("EM29mm", "39.5", "-95"), Triple(d.locator, d.latitude, d.longitude))
        assertTrue(d.canSave)
        assertFalse(StationEditing.Draft(blank).canSave)
    }

    @Test
    fun `typing and leaving the locator fills in the others`() {
        val c = StationEditing.Draft(blank).typeLocator("IO82xk").commitLocator()
        assertNull(c.rejected)
        assertEquals("IO82xk", c.draft.location.locator)
        assertEquals(52.4375, c.draft.location.latitude.toDouble(), 0.01)
        assertEquals(c.draft.location.latitude, c.draft.latitude)
        assertEquals(c.draft.location.longitude, c.draft.longitude)
        assertNull(c.draft.squareNote)
    }

    @Test
    fun `a square is committed as its centre and the field and the note say so`() {
        val c = StationEditing.Draft(blank).typeLocator("em29").commitLocator()
        assertEquals("EM29mm", c.draft.locator)
        assertEquals("EM29", c.draft.squareNote)
        // editing a coordinate afterwards takes the note away: the position is no longer the square's centre
        val after = c.draft.typeLatitude("39.9").commitLatitude()
        assertNull(after.draft.squareNote)
    }

    @Test
    fun `a rejected value is reported and reset, as in Settings, and the fields show the committed position`() {
        val start = StationEditing.Draft(kansasCity)
        val badLocator = start.typeLocator("EM2").commitLocator()
        assertEquals(LocatorSync.Field.LOCATOR, badLocator.rejected)
        assertEquals("only the locator is reset", Triple("JJ00aa", "39.5", "-95"), Triple(badLocator.draft.locator, badLocator.draft.latitude, badLocator.draft.longitude))
        val badLatitude = start.typeLatitude("95").commitLatitude()
        assertEquals(LocatorSync.Field.LATITUDE, badLatitude.rejected)
        assertEquals("only the latitude is reset", Pair("0", "-95"), Pair(badLatitude.draft.latitude, badLatitude.draft.longitude))
        val badLongitude = start.typeLongitude("x").commitLongitude()
        assertEquals(LocatorSync.Field.LONGITUDE, badLongitude.rejected)
        assertEquals("only the longitude is reset", Pair("39.5", "0"), Pair(badLongitude.draft.latitude, badLongitude.draft.longitude))
    }

    @Test
    fun `leaving a field that was not changed commits nothing`() {
        val d = StationEditing.Draft(kansasCity)
        assertEquals(StationEditing.Draft.Committed(d, null), d.commitLocator())
        assertEquals(StationEditing.Draft.Committed(d, null), d.commitLatitude())
        assertEquals(StationEditing.Draft.Committed(d, null), d.commitLongitude())
        assertEquals(StationEditing.Draft.Committed(d, null), d.commitPending())
    }

    @Test
    fun `the same locator typed in another case does not leave a stale draft that undoes a later latitude edit`() {
        // the bug: the committed locator EM29mm is unchanged by typing em29mm, so the field kept the text em29mm, and Save
        // later applied that stale text first, throwing away a latitude edit made within the same locator
        var d = StationEditing.Draft(kansasCity)
        d = d.typeLocator("em29mm").commitLocator().draft
        assertEquals("the field shows the committed locator again", "EM29mm", d.locator)
        d = d.typeLatitude("39.51").commitLatitude().draft
        assertEquals("39.51", d.location.latitude)
        assertEquals("EM29mm", d.location.locator) // still the same 6-character locator
        val saved = d.commitPending() // what Save does first
        assertNull(saved.rejected)
        assertEquals("the latitude edit survives Save", "39.51", saved.draft.location.latitude)
    }

    @Test
    fun `Save commits the field that was typed in but never left`() {
        val typed = StationEditing.Draft(blank).typeLocator("IO82xk").commitPending()
        assertEquals("IO82xk", typed.draft.location.locator)
        assertTrue(typed.draft.canSave)
        val lat = StationEditing.Draft(kansasCity).typeLatitude("40.25").commitPending()
        assertEquals("40.25", lat.draft.location.latitude)
        val lon = StationEditing.Draft(kansasCity).typeLongitude("-94,5").commitPending() // a comma is accepted
        assertEquals("-94.5", lon.draft.location.longitude)
    }

    @Test
    fun `Save with a bad value in the field being typed reports it`() {
        val c = StationEditing.Draft(kansasCity).typeLatitude("200").commitPending()
        assertEquals(LocatorSync.Field.LATITUDE, c.rejected)
        assertEquals("the field shows the reset value, not the rejected text", "0", c.draft.latitude)
    }
}
