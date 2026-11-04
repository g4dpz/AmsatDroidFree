package uk.me.g4dpz.HamSatDroid.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uk.me.g4dpz.satellite.OrbitalElements
import uk.me.g4dpz.satellite.TLE
import java.time.LocalDate
import java.time.LocalTime

/** A keps download that is not a satellite list at all (for example a text error message). */
class InvalidKepsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The satellites found in a keps file, and how many individual records were skipped because they were malformed or
 * outside the range the prediction code supports.
 */
data class ParsedKeps(val elements: List<OrbitalElements>, val skipped: Int)

/**
 * Reads Celestrak's JSON format (OMM keywords, `gp.php?...&FORMAT=JSON`).
 *
 * The whole body must be a JSON array, otherwise [InvalidKepsException] is thrown. Within the array a bad record is
 * skipped and counted rather than discarding the whole download. Catalogue numbers above 99999 are accepted.
 */
object OmmJsonParser {

    private val EPOCH = Regex("""^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})(\.\d+)?Z?$""")

    @Throws(InvalidKepsException::class)
    fun parse(text: String): ParsedKeps {
        val trimmed = text.trim()
        if (!trimmed.startsWith("[")) {
            throw InvalidKepsException("Not a JSON array: ${trimmed.take(80)}")
        }
        val array = try {
            JSONArray(trimmed)
        } catch (e: JSONException) {
            throw InvalidKepsException("Unreadable JSON: ${e.message}", e)
        }
        val elements = ArrayList<OrbitalElements>(array.length())
        var skipped = 0
        for (i in 0 until array.length()) {
            val record = array.optJSONObject(i)
            val parsed = record?.let { toElements(it) }
            if (parsed == null) skipped++ else elements.add(parsed)
        }
        return ParsedKeps(elements, skipped)
    }

    /** Returns null if the record is incomplete, malformed or unsupported. */
    private fun toElements(o: JSONObject): OrbitalElements? = try {
        val catnum = o.getLong("NORAD_CAT_ID").also { require(it in 1..Int.MAX_VALUE) }.toInt()
        val (year, day) = parseEpoch(o.getString("EPOCH"))
        val name = o.optString("OBJECT_NAME", "").trim().ifEmpty {
            o.optString("OBJECT_ID", "").trim().ifEmpty { "CAT $catnum" }
        }
        OrbitalElements(
            name = name,
            catnum = catnum,
            epochYear = year,
            epochDay = day,
            meanMotion = o.getDouble("MEAN_MOTION"),
            eccentricity = o.getDouble("ECCENTRICITY"),
            inclination = o.getDouble("INCLINATION"),
            raan = o.getDouble("RA_OF_ASC_NODE"),
            argPerigee = o.getDouble("ARG_OF_PERICENTER"),
            meanAnomaly = o.getDouble("MEAN_ANOMALY"),
            bstar = o.optDouble("BSTAR", 0.0),
            meanMotionDot = o.optDouble("MEAN_MOTION_DOT", 0.0),
            meanMotionDdot = o.optDouble("MEAN_MOTION_DDOT", 0.0),
            elementSetNo = o.optInt("ELEMENT_SET_NO", 0),
            revAtEpoch = o.optInt("REV_AT_EPOCH", 0),
        ).also { it.validate() }
    } catch (e: JSONException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: java.time.DateTimeException) {
        null
    }

    /** Converts an ISO 8601 UTC epoch such as `2026-11-04T00:29:23.112096` to a year and a fractional day of year. */
    internal fun parseEpoch(text: String): Pair<Int, Double> {
        val m = EPOCH.matchEntire(text.trim()) ?: throw IllegalArgumentException("Bad epoch: $text")
        val (y, mo, d, h, mi, s) = m.destructured
        val date = LocalDate.of(y.toInt(), mo.toInt(), d.toInt())
        val fraction = m.groupValues[7].let { if (it.isEmpty()) 0.0 else it.toDouble() }
        val time = LocalTime.of(h.toInt(), mi.toInt(), s.toInt())
        val secondsOfDay = time.toSecondOfDay() + fraction
        return date.year to (date.dayOfYear + secondsOfDay / 86400.0)
    }
}

/**
 * Reads classic three-line element sets (name, line 1, line 2), as used by AMSAT's list.
 *
 * Lines are matched by their leading "1 " and "2 ", so stray blank lines or text are skipped, and a record that the
 * library cannot parse is skipped and counted. TLE text cannot hold catalogue numbers above 99999.
 */
object TleTextParser {

    fun parse(text: String): ParsedKeps {
        val lines = text.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toList()
        val elements = ArrayList<OrbitalElements>()
        var skipped = 0
        var i = 0
        while (i + 2 < lines.size) {
            if (lines[i + 1].startsWith("1 ") && lines[i + 2].startsWith("2 ")) {
                val parsed = try {
                    val tle = TLE(arrayOf<String?>(lines[i], lines[i + 1], lines[i + 2]))
                    OrbitalElements.from(tle).also { it.validate() }
                } catch (e: RuntimeException) {
                    null
                }
                if (parsed == null) skipped++ else elements.add(parsed)
                i += 3
            } else {
                i++
            }
        }
        return ParsedKeps(elements, skipped)
    }
}
