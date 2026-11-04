package uk.me.g4dpz.HamSatDroid.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Which way Doppler moves across a pass for this mode: normal, or reversed for an inverting transponder. */
enum class DopplerDirection {
    /** Normal: downlink falls while the satellite recedes, uplink is raised. */
    NOR,

    /** Reversed: the transponder inverts the band, so the uplink correction direction is flipped. */
    REV,
    ;

    companion object {
        fun fromText(text: String?): DopplerDirection =
            if (text?.trim()?.equals("REV", ignoreCase = true) == true) REV else NOR
    }
}

/**
 * One transponder mode of a satellite, as published in the OscarWatch transponder database. Frequencies are kept in
 * whole hertz (the database holds kilohertz, possibly with decimals; this is converted on load). A [uplinkHz] of
 * `null` means a beacon or receive-only mode: there is nothing to transmit.
 *
 * @param type a human label such as "Mode B", "FM" or "SSB Transponder"
 * @param downlinkHz the satellite's transmit frequency (what you tune to receive), in Hz
 * @param uplinkHz the frequency the satellite listens on (what you transmit), in Hz, or null for a beacon
 * @param downlinkMode downlink modulation (USB/LSB/CW/FM/FMN/...), as published; may be blank
 * @param uplinkMode uplink modulation, as published; may be blank
 * @param doppler which way the Doppler correction runs ([DopplerDirection])
 * @param ctcssHz an FM uplink CTCSS tone in Hz, or null if none
 */
data class TransponderMode(
    val type: String,
    val downlinkHz: Long,
    val uplinkHz: Long?,
    val downlinkMode: String,
    val uplinkMode: String,
    val doppler: DopplerDirection,
    val ctcssHz: Double?,
) {
    /** A beacon or receive-only mode: no uplink to transmit on. */
    val isBeaconOnly: Boolean get() = uplinkHz == null
}

/** One satellite's entry in the transponder database: its name, NORAD id and modes. */
data class TransponderSatellite(
    val name: String,
    val noradId: Int,
    val modes: List<TransponderMode>,
)

/**
 * The transponder database: satellites with their modes, indexed by NORAD id so the app can look up a satellite's
 * frequencies by the catalogue number it already tracks. Build it from the OscarWatch `satellite_database.json` with
 * [parse]; a missing, corrupt or wrong-shaped file yields [EMPTY] rather than throwing.
 */
class TransponderDatabase private constructor(
    val satellites: List<TransponderSatellite>,
) {
    private val byNorad: Map<Int, TransponderSatellite> = satellites.associateBy { it.noradId }

    /** The modes for the satellite with this NORAD id (its catalogue number), or an empty list if it is not listed. */
    fun modesForNoradId(noradId: Int): List<TransponderMode> = byNorad[noradId]?.modes ?: emptyList()

    val isEmpty: Boolean get() = satellites.isEmpty()

    companion object {
        val EMPTY = TransponderDatabase(emptyList())

        /**
         * Parses OscarWatch's `satellite_database.json`: a top-level array of `{name, norad_id, modes[]}`, each mode
         * carrying `type`, `downlink`/`uplink` in kilohertz (numbers, possibly decimal; a missing or zero uplink means
         * a beacon), optional `downlink_mode`/`uplink_mode`, `doppler` (NOR/REV) and an optional `ctcss` tone in Hz.
         * Unknown fields are ignored. Returns [EMPTY] if the text is not a usable database; individual malformed
         * satellites or modes are skipped rather than failing the whole file.
         */
        fun parse(text: String): TransponderDatabase {
            val array = try {
                JSONArray(text)
            } catch (e: JSONException) {
                return EMPTY
            }
            val satellites = ArrayList<TransponderSatellite>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                parseSatellite(obj)?.let(satellites::add)
            }
            return TransponderDatabase(satellites)
        }

        private fun parseSatellite(obj: JSONObject): TransponderSatellite? {
            val name = obj.optString("name").trim()
            val noradId = obj.optString("norad_id").trim().toIntOrNull()
            if (name.isEmpty() || noradId == null) return null
            val modesJson = obj.optJSONArray("modes") ?: return null
            val modes = ArrayList<TransponderMode>(modesJson.length())
            for (j in 0 until modesJson.length()) {
                val modeObj = modesJson.optJSONObject(j) ?: continue
                parseMode(modeObj)?.let(modes::add)
            }
            if (modes.isEmpty()) return null
            return TransponderSatellite(name, noradId, modes)
        }

        private fun parseMode(obj: JSONObject): TransponderMode? {
            val type = obj.optString("type").trim().ifEmpty { return null }
            val downlinkKHz = obj.optDouble("downlink", 0.0)
            if (downlinkKHz <= 0.0) return null
            val uplinkKHz = obj.optDouble("uplink", 0.0)
            val ctcss = if (obj.has("ctcss") && !obj.isNull("ctcss")) {
                obj.optDouble("ctcss", 0.0).takeIf { it > 0.0 }
            } else {
                null
            }
            return TransponderMode(
                type = type,
                downlinkHz = kHzToHz(downlinkKHz),
                uplinkHz = if (uplinkKHz > 0.0) kHzToHz(uplinkKHz) else null,
                downlinkMode = obj.optString("downlink_mode").trim(),
                uplinkMode = obj.optString("uplink_mode").trim(),
                doppler = DopplerDirection.fromText(obj.optString("doppler")),
                ctcssHz = ctcss,
            )
        }

        private fun kHzToHz(kHz: Double): Long = Math.round(kHz * 1000.0)
    }
}
