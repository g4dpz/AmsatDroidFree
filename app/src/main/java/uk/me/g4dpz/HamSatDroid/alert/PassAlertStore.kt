package uk.me.g4dpz.HamSatDroid.alert

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uk.me.g4dpz.HamSatDroid.data.FileStore
import java.io.IOException

/**
 * The pass alerts the user has set, kept as JSON in `alerts.json` through [FileStore]. Reading never throws: a
 * missing, corrupt or wrong-schema file gives an empty list. Writes are atomic. It only stores; scheduling the
 * alarms is `PassAlertScheduler`'s job.
 */
class PassAlertStore(private val files: FileStore) {

    fun all(): List<PassAlert> = files.readTextOrNull(FILE)?.let(::decode) ?: emptyList()

    fun contains(id: Int): Boolean = all().any { it.id == id }

    @Throws(IOException::class)
    fun add(alert: PassAlert) {
        val without = all().filterNot { it.id == alert.id }
        write(without + alert)
    }

    @Throws(IOException::class)
    fun remove(id: Int) {
        val remaining = all().filterNot { it.id == id }
        if (remaining.size != all().size) write(remaining)
    }

    /** Drops alerts whose pass has ended, and returns what was removed (so the caller can cancel their alarms). */
    @Throws(IOException::class)
    fun pruneEnded(nowMillis: Long): List<PassAlert> {
        val all = all()
        val (ended, keep) = all.partition { it.hasEnded(nowMillis) }
        if (ended.isNotEmpty()) write(keep)
        return ended
    }

    @Throws(IOException::class)
    private fun write(alerts: List<PassAlert>) = files.writeTextAtomic(FILE, encode(alerts))

    internal fun encode(alerts: List<PassAlert>): String {
        val array = JSONArray()
        for (a in alerts) {
            array.put(
                JSONObject()
                    .put("catnum", a.catnum).put("satName", a.satName)
                    .put("aos", a.aosMillis).put("los", a.losMillis)
                    .put("maxEl", a.maxElDeg).put("aosAz", a.aosAz).put("losAz", a.losAz)
            )
        }
        return JSONObject().put("schema", SCHEMA).put("alerts", array).toString()
    }

    private fun decode(text: String): List<PassAlert>? = try {
        val root = JSONObject(text)
        if (root.optInt("schema") != SCHEMA) return null
        val array = root.getJSONArray("alerts")
        buildList {
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                add(
                    PassAlert(
                        catnum = o.getInt("catnum"), satName = o.getString("satName"),
                        aosMillis = o.getLong("aos"), losMillis = o.getLong("los"),
                        maxElDeg = o.getDouble("maxEl"), aosAz = o.getInt("aosAz"), losAz = o.getInt("losAz"),
                    )
                )
            }
        }
    } catch (e: JSONException) {
        null
    }

    private companion object {
        const val FILE = "alerts.json"
        const val SCHEMA = 1
    }
}
