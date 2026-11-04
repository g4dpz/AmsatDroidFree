package uk.me.g4dpz.HamSatDroid.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * A named set of frequencies for one satellite: a [label] (such as "FM voice" or "SSB"), a [downlinkHz] to tune, and
 * an optional [uplinkHz] to transmit. The app bundles no frequencies (A11); these are entered by the user.
 */
data class FrequencyEntry(
    val catnum: Int,
    val label: String,
    val downlinkHz: Long,
    val uplinkHz: Long?,
) {
    /** A stable id for this entry within a satellite (label is unique per satellite, enforced on add/edit). */
    val id: String get() = "$catnum:$label"
}

/**
 * Stores the user's frequency entries as JSON in `frequencies.json` through [FileStore]. Reading never throws: a
 * missing, corrupt or wrong-schema file gives an empty list. Writes are atomic.
 */
class FrequencyStore(private val files: FileStore) {

    fun all(): List<FrequencyEntry> = files.readTextOrNull(FILE)?.let(::decode) ?: emptyList()

    fun forSatellite(catnum: Int): List<FrequencyEntry> = all().filter { it.catnum == catnum }.sortedBy { it.label }

    /** Adds [entry], or replaces an existing entry with the same satellite and label. */
    @Throws(IOException::class)
    fun put(entry: FrequencyEntry) {
        val without = all().filterNot { it.catnum == entry.catnum && it.label == entry.label }
        write(without + entry)
    }

    @Throws(IOException::class)
    fun remove(catnum: Int, label: String) {
        val remaining = all().filterNot { it.catnum == catnum && it.label == label }
        if (remaining.size != all().size) write(remaining)
    }

    @Throws(IOException::class)
    private fun write(entries: List<FrequencyEntry>) = files.writeTextAtomic(FILE, encode(entries))

    internal fun encode(entries: List<FrequencyEntry>): String {
        val array = JSONArray()
        for (e in entries) {
            array.put(
                JSONObject()
                    .put("catnum", e.catnum)
                    .put("label", e.label)
                    .put("downlink", e.downlinkHz)
                    .put("uplink", e.uplinkHz ?: JSONObject.NULL)
            )
        }
        return JSONObject().put("schema", SCHEMA).put("entries", array).toString()
    }

    private fun decode(text: String): List<FrequencyEntry>? = try {
        val root = JSONObject(text)
        if (root.optInt("schema") != SCHEMA) return null
        val array = root.getJSONArray("entries")
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    FrequencyEntry(
                        catnum = o.getInt("catnum"),
                        label = o.getString("label"),
                        downlinkHz = o.getLong("downlink"),
                        uplinkHz = if (o.isNull("uplink")) null else o.getLong("uplink"),
                    )
                )
            }
        }
    } catch (e: JSONException) {
        null
    }

    private companion object {
        const val FILE = "frequencies.json"
        const val SCHEMA = 1
    }
}
