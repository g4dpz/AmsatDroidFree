package uk.me.g4dpz.HamSatDroid.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uk.me.g4dpz.satellite.OrbitalElements

/** The satellites held in `keps.json`, with where and when they came from. */
data class StoredKeps(
    /** A [KepsSource] key, or [BUNDLED] for the elements shipped inside the app. */
    val source: String,
    /** When the elements were downloaded (0 for the bundled set). */
    val fetchedAtMillis: Long,
    val elements: List<OrbitalElements>,
) {
    companion object {
        const val BUNDLED = "BUNDLED"
    }
}

/**
 * The on-disk format of `keps.json`:
 * `{"schema":1,"source":"...","fetchedAtMillis":123,"elements":[{...}, ...]}`.
 *
 * It holds [OrbitalElements] rather than TLE text because TLE lines cannot hold catalogue numbers above 99999.
 */
object KepsJson {
    private const val SCHEMA = 1

    fun encode(keps: StoredKeps): String {
        val array = JSONArray()
        for (e in keps.elements) array.put(encode(e))
        return JSONObject()
            .put("schema", SCHEMA)
            .put("source", keps.source)
            .put("fetchedAtMillis", keps.fetchedAtMillis)
            .put("elements", array)
            .toString()
    }

    /** Returns null if the text is not a valid `keps.json` of the current schema. Individual bad records are dropped. */
    fun decode(text: String): StoredKeps? = try {
        val root = JSONObject(text)
        if (root.getInt("schema") != SCHEMA) {
            null
        } else {
            val array = root.getJSONArray("elements")
            val elements = ArrayList<OrbitalElements>(array.length())
            for (i in 0 until array.length()) decodeElement(array.getJSONObject(i))?.let(elements::add)
            StoredKeps(root.getString("source"), root.getLong("fetchedAtMillis"), elements)
        }
    } catch (e: JSONException) {
        null
    }

    private fun encode(e: OrbitalElements) = JSONObject()
        .put("name", e.name)
        .put("catnum", e.catnum)
        .put("epochYear", e.epochYear)
        .put("epochDay", e.epochDay)
        .put("meanMotion", e.meanMotion)
        .put("eccentricity", e.eccentricity)
        .put("inclination", e.inclination)
        .put("raan", e.raan)
        .put("argPerigee", e.argPerigee)
        .put("meanAnomaly", e.meanAnomaly)
        .put("bstar", e.bstar)
        .put("meanMotionDot", e.meanMotionDot)
        .put("meanMotionDdot", e.meanMotionDdot)
        .put("elementSetNo", e.elementSetNo)
        .put("revAtEpoch", e.revAtEpoch)

    private fun decodeElement(o: JSONObject): OrbitalElements? = try {
        OrbitalElements(
            name = o.getString("name"),
            catnum = o.getInt("catnum"),
            epochYear = o.getInt("epochYear"),
            epochDay = o.getDouble("epochDay"),
            meanMotion = o.getDouble("meanMotion"),
            eccentricity = o.getDouble("eccentricity"),
            inclination = o.getDouble("inclination"),
            raan = o.getDouble("raan"),
            argPerigee = o.getDouble("argPerigee"),
            meanAnomaly = o.getDouble("meanAnomaly"),
            bstar = o.getDouble("bstar"),
            meanMotionDot = o.getDouble("meanMotionDot"),
            meanMotionDdot = o.getDouble("meanMotionDdot"),
            elementSetNo = o.getInt("elementSetNo"),
            revAtEpoch = o.getInt("revAtEpoch"),
        ).also { it.validate() }
    } catch (e: JSONException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
