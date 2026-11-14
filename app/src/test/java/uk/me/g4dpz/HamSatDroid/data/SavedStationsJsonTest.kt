// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedStationsJsonTest {
    private val kansas = SavedStation("id-1", "W0KC", "39.5", "-95", "EM29mm")
    private val london = SavedStation("id-2", "", "51.5", "-0.12", "IO91wm")

    @Test
    fun `stations survive a round trip, in order`() {
        assertEquals(listOf(kansas, london), SavedStationsJson.read(SavedStationsJson.write(listOf(kansas, london))))
    }

    @Test
    fun `an empty list survives a round trip`() {
        assertEquals(emptyList<SavedStation>(), SavedStationsJson.read(SavedStationsJson.write(emptyList())))
    }

    @Test
    fun `names with quotes, accents and symbols are kept`() {
        val odd = SavedStation("id-3", "Zoë \"G4\" \\ é€", "0.5", "0.5", "JJ00mm")
        assertEquals(listOf(odd), SavedStationsJson.read(SavedStationsJson.write(listOf(odd))))
    }

    @Test
    fun `missing or blank text gives an empty list`() {
        assertEquals(emptyList<SavedStation>(), SavedStationsJson.read(null))
        assertEquals(emptyList<SavedStation>(), SavedStationsJson.read(""))
        assertEquals(emptyList<SavedStation>(), SavedStationsJson.read("   "))
    }

    @Test
    fun `corrupt or wrong-shaped text gives an empty list`() {
        for (text in listOf(
            "not json", "{", "[]", "[1,2,3]", "\"text\"", "42", "{}", "null",
            """{"schema":1}""", """{"schema":1,"stations":"x"}""", """{"schema":1,"stations":{"a":1}}""",
            """{"schema":2,"stations":[{"id":"a","name":"","latitude":"1","longitude":"1","locator":"JJ00aa"}]}""",
            """{"stations":[{"id":"a","name":"","latitude":"1","longitude":"1","locator":"JJ00aa"}]}""",
        )) {
            assertEquals("text: $text", emptyList<SavedStation>(), SavedStationsJson.read(text))
        }
    }

    /** One entry of the stations array, built from key and value pairs (a null value is a JSON null). */
    private fun entry(vararg fields: Pair<String, Any?>): String =
        JSONObject().apply { fields.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } }.toString()

    private fun documentOf(vararg entries: String) = """{"schema":1,"stations":[${entries.joinToString(",")}]}"""

    @Test
    fun `an entry that cannot be used is dropped and the others are kept`() {
        val text = documentOf(
            entry("name" to "no id", "latitude" to "10", "longitude" to "20"),
            entry("id" to "", "latitude" to "10", "longitude" to "20"),
            entry("id" to "  ", "latitude" to "10", "longitude" to "20"),
            entry("id" to "a", "latitude" to "north", "longitude" to "20"),
            entry("id" to "b", "latitude" to "10", "longitude" to ""),
            entry("id" to "c", "latitude" to "90.5", "longitude" to "20"),
            entry("id" to "d", "latitude" to "10", "longitude" to "-181"),
            entry("id" to "e", "latitude" to "NaN", "longitude" to "0"),
            entry("id" to "f"), // no position
            entry("id" to "g", "latitude" to null, "longitude" to null),
            "42", "null", "\"text\"", "[]",
            entry("id" to "ok", "name" to "A", "latitude" to "10", "longitude" to "20", "locator" to "JK00aa"),
        )
        assertEquals(listOf(SavedStation("ok", "A", "10", "20", "JK00aa")), SavedStationsJson.read(text))
    }

    @Test
    fun `the edges of the valid range are kept, and a comma as decimal separator is accepted`() {
        val text = documentOf(
            entry("id" to "a", "latitude" to "-90", "longitude" to "180"),
            entry("id" to "b", "latitude" to "90", "longitude" to "-180"),
            entry("id" to "c", "latitude" to "51,5", "longitude" to "-0,12"),
        )
        assertEquals(listOf("a", "b", "c"), SavedStationsJson.read(text).map { it.id })
    }

    @Test
    fun `fields it does not know are ignored, and optional fields may be missing`() {
        val text = documentOf(entry("id" to "x", "latitude" to "1", "longitude" to "2", "callsign" to "G4DPZ", "colour" to 3))
        assertEquals(listOf(SavedStation("x", "", "1", "2", "")), SavedStationsJson.read(text))
    }

    @Test
    fun `a JSON null is a missing field, not the text null`() {
        val text = documentOf(
            entry("id" to null, "latitude" to "1", "longitude" to "2"),
            entry("id" to "x", "name" to null, "latitude" to "1", "longitude" to "2", "locator" to null),
        )
        assertEquals(listOf(SavedStation("x", "", "1", "2", "")), SavedStationsJson.read(text))
    }

    @Test
    fun `a name longer than the limit is cut`() {
        val long = "N".repeat(SavedStation.MAX_NAME_LENGTH + 15)
        val read = SavedStationsJson.read(SavedStationsJson.write(listOf(kansas.copy(name = long)))).single()
        assertEquals("N".repeat(SavedStation.MAX_NAME_LENGTH), read.name)
    }

    @Test
    fun `a repeated id keeps only the first entry`() {
        val read = SavedStationsJson.read(SavedStationsJson.write(listOf(kansas, kansas.copy(name = "again"), london)))
        assertEquals(listOf(kansas, london), read)
    }

    @Test
    fun `no more than the maximum number of stations is read`() {
        val many = List(SavedStation.MAX_STATIONS + 5) { SavedStation("id-$it", "S$it", "1", "2", "JJ00aa") }
        val read = SavedStationsJson.read(SavedStationsJson.write(many))
        assertEquals(SavedStation.MAX_STATIONS, read.size)
        assertEquals(many.take(SavedStation.MAX_STATIONS), read)
    }

    @Test
    fun `the display name is the name, or the locator when there is no name`() {
        assertEquals("W0KC", kansas.displayName)
        assertEquals("IO91wm", london.displayName)
        assertEquals("IO91wm", london.copy(name = "   ").displayName)
        assertTrue(SavedStation.MAX_STATIONS == 10 && SavedStation.MAX_NAME_LENGTH == 20)
    }
}
