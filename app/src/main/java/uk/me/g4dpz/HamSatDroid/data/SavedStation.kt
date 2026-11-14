// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The other station of the contact planner, saved on the device so it can be chosen again.
 *
 * Latitude, longitude and locator are kept in the form `LocatorSync` uses for the home location: decimal degrees as
 * text, and the IARU locator that goes with them.
 */
data class SavedStation(
    /** A random UUID, made when the station is added; it stays the same when the station is edited. */
    val id: String,
    /** Such as a callsign; may be empty. At most [MAX_NAME_LENGTH] characters. */
    val name: String,
    val latitude: String,
    val longitude: String,
    val locator: String,
) {
    /** What the planner shows: the name, or the locator when the station has no name (requirement 2.4). */
    val displayName: String get() = name.ifBlank { locator }

    companion object {
        const val MAX_NAME_LENGTH = 20

        /** [name] cut to [MAX_NAME_LENGTH] characters, counting a symbol such as an emoji as one and never splitting it. */
        fun limitName(name: String): String =
            if (name.codePointCount(0, name.length) <= MAX_NAME_LENGTH) name
            else name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH))

        /** The most stations the planner keeps (open question O4: 10 in the first version). */
        const val MAX_STATIONS = 10
    }
}

/**
 * The saved stations as stored in settings: `{"schema":1,"stations":[{"id":"...","name":"...","latitude":"...",
 * "longitude":"...","locator":"..."}, ...]}`.
 *
 * Reading is tolerant, so a damaged value can never stop the app starting: missing, corrupt or wrong-shaped text gives
 * an empty list, an entry that cannot be used is dropped, and fields it does not know are ignored.
 */
object SavedStationsJson {
    private const val SCHEMA = 1

    fun write(stations: List<SavedStation>): String {
        val array = JSONArray()
        for (s in stations) {
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("name", s.name)
                    .put("latitude", s.latitude)
                    .put("longitude", s.longitude)
                    .put("locator", s.locator)
            )
        }
        return JSONObject().put("schema", SCHEMA).put("stations", array).toString()
    }

    fun read(text: String?): List<SavedStation> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            val root = JSONObject(text)
            if (!isCurrentSchema(root.opt("schema"))) return emptyList()
            val array = root.optJSONArray("stations") ?: return emptyList()
            val stations = ArrayList<SavedStation>()
            for (i in 0 until array.length()) {
                val station = array.optJSONObject(i)?.let(::readStation) ?: continue
                if (stations.none { it.id == station.id }) stations += station
                if (stations.size == SavedStation.MAX_STATIONS) break
            }
            stations
        } catch (e: JSONException) {
            emptyList()
        }
    }

    /** Only the whole number 1: not 1.5, 1.0, "1" or true, which a coercing accessor would accept. */
    private fun isCurrentSchema(value: Any?): Boolean = when (value) {
        is Int -> value == SCHEMA
        is Long -> value == SCHEMA.toLong()
        else -> false
    }

    /** One entry, or null when it cannot be used: no id, or a latitude or longitude that is missing or out of range. */
    private fun readStation(o: JSONObject): SavedStation? {
        val id = o.text("id").trim()
        if (id.isEmpty()) return null
        val latitude = o.text("latitude")
        val longitude = o.text("longitude")
        val lat = LocationText.toDegrees(latitude) ?: return null
        val lon = LocationText.toDegrees(longitude) ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return SavedStation(
            id = id,
            name = SavedStation.limitName(o.text("name")),
            latitude = latitude,
            longitude = longitude,
            locator = o.text("locator"),
        )
    }

    /** The field as text, or empty when it is missing or a JSON null (`optString` would turn a null into "null"). */
    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "")
}
