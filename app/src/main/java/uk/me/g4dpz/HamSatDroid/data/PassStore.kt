// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uk.me.g4dpz.satellite.SatPassTime
import java.io.IOException
import java.util.Date

/** The last calculated pass list, kept so it can be shown again after the app restarts. */
data class StoredPasses(
    val catnum: Int,
    val satName: String,
    /** When the calculation was done. */
    val computedAtMillis: Long,
    /** How many hours ahead were asked for (0 means "next pass only"). */
    val hoursAhead: Int,
    val passes: List<SatPassTime>,
)

/**
 * Stores the last pass list as JSON in `passes.json`. This replaces the Java-serialized `prefs.bin` of earlier
 * versions. Reading never throws: a missing, corrupt or wrong-schema file gives null and the app starts with an empty
 * list. Writes are atomic.
 */
class PassStore(private val files: FileStore) {

    @Throws(IOException::class)
    fun save(stored: StoredPasses) {
        files.writeTextAtomic(FILE, encode(stored))
    }

    fun load(): StoredPasses? = files.readTextOrNull(FILE)?.let(::decode)

    fun clear() {
        files.delete(FILE)
    }

    internal fun encode(s: StoredPasses): String {
        val array = JSONArray()
        for (p in s.passes) {
            array.put(
                JSONObject()
                    .put("start", p.startTime.time)
                    .put("end", p.endTime.time)
                    .put("tca", p.tca.time)
                    .put("pole", p.polePassed ?: JSONObject.NULL)
                    .put("aos", p.aosAzimuth)
                    .put("los", p.losAzimuth)
                    .put("maxEl", p.maxEl)
            )
        }
        return JSONObject()
            .put("schema", SCHEMA)
            .put("catnum", s.catnum)
            .put("satName", s.satName)
            .put("computedAtMillis", s.computedAtMillis)
            .put("hoursAhead", s.hoursAhead)
            .put("passes", array)
            .toString()
    }

    internal fun decode(text: String): StoredPasses? = try {
        val root = JSONObject(text)
        if (root.getInt("schema") != SCHEMA) {
            null
        } else {
            val array = root.getJSONArray("passes")
            val passes = ArrayList<SatPassTime>(array.length())
            for (i in 0 until array.length()) decodePass(array.getJSONObject(i))?.let(passes::add)
            StoredPasses(
                catnum = root.getInt("catnum"),
                satName = root.getString("satName"),
                computedAtMillis = root.getLong("computedAtMillis"),
                hoursAhead = root.getInt("hoursAhead"),
                passes = passes,
            )
        }
    } catch (e: JSONException) {
        null
    }

    private fun decodePass(o: JSONObject): SatPassTime? = try {
        val start = o.getLong("start")
        val end = o.getLong("end")
        require(end >= start)
        SatPassTime(
            Date(start), Date(end), Date(o.getLong("tca")),
            if (o.isNull("pole")) null else o.getString("pole"),
            o.getInt("aos"), o.getInt("los"), o.getDouble("maxEl"),
        )
    } catch (e: JSONException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        const val FILE = "passes.json"
        private const val SCHEMA = 1
    }
}
