// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

/** How a downloaded keps file is encoded. */
enum class KepsFormat {
    /** Classic three-line element sets. Cannot represent catalogue numbers above 99999. */
    TLE_TEXT,

    /** CCSDS OMM keywords as a JSON array (Celestrak `FORMAT=JSON`). Supports catalogue numbers up to 9 digits. */
    OMM_JSON
}

/**
 * The places Keplerian elements can be downloaded from.
 *
 * The enum name is the stable key stored in preferences and used by earlier versions of the app, so do not rename
 * entries. All URLs are https.
 *
 * Celestrak notes (from its GP data documentation):
 *  - the old `NORAD/elements/<group>.txt` files have been removed; `gp.php` queries are the supported way in;
 *  - since 2026-07-11 new objects have 6-digit catalogue numbers and are **not available as TLE**, so Celestrak
 *    sources use JSON;
 *  - data changes only every 2 hours and repeat downloads are refused (HTTP 403), see [MIN_DOWNLOAD_INTERVAL_MILLIS];
 *  - errors can come back as HTTP 200 with a text body, so a download must be parsed and checked before it is trusted.
 */
enum class KepsSource(val url: String, val format: KepsFormat) {
    /** AMSAT's curated list. TLE only, so it can never contain objects numbered above 99999. */
    AMATEUR_AMSAT("https://www.amsat.org/amsat/ftp/keps/current/nasabare.txt", KepsFormat.TLE_TEXT),
    AMATEUR_CELESTRAK("https://celestrak.org/NORAD/elements/gp.php?GROUP=amateur&FORMAT=JSON", KepsFormat.OMM_JSON),

    /** Replaces the old `noaa.txt`, which has no direct equivalent; the weather group is the closest. */
    WEATHER_CELESTRAK("https://celestrak.org/NORAD/elements/gp.php?GROUP=weather&FORMAT=JSON", KepsFormat.OMM_JSON),
    CUBESAT_CELESTRAK("https://celestrak.org/NORAD/elements/gp.php?GROUP=cubesat&FORMAT=JSON", KepsFormat.OMM_JSON),
    RESOURCES_CELESTRAK("https://celestrak.org/NORAD/elements/gp.php?GROUP=resource&FORMAT=JSON", KepsFormat.OMM_JSON),

    /** Replaces the old `tle-new.txt`. This group can legitimately be empty. */
    NEW_CELESTRAK("https://celestrak.org/NORAD/elements/gp.php?GROUP=last-30-days&FORMAT=JSON", KepsFormat.OMM_JSON);

    /** The key stored in preferences. */
    val key: String get() = name

    companion object {
        /**
         * Celestrak's amateur group as JSON. Chosen over the original AMSAT list because AMSAT's TLE-only file cannot
         * contain objects numbered above 99999 (for example AO-129 and AO-130).
         */
        val DEFAULT: KepsSource = AMATEUR_CELESTRAK

        /**
         * Celestrak refreshes its data every two hours and refuses (HTTP 403) a second download of the same data
         * within that time. Nothing in the app may fetch the same source more often than this.
         */
        const val MIN_DOWNLOAD_INTERVAL_MILLIS: Long = 2L * 60 * 60 * 1000

        /** Looks a source up by its stored key, falling back to [DEFAULT] for null or unknown keys. */
        fun fromKey(key: String?): KepsSource = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
