// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import org.json.JSONException
import org.json.JSONObject
import uk.me.g4dpz.satellite.OrbitalElements
import uk.me.g4dpz.satellite.TLE
import java.io.IOException

/** The satellites the app is currently using, and where they came from. */
data class LoadedKeps(
    val tles: List<TLE>,
    /** A [KepsSource] key or [StoredKeps.BUNDLED]. */
    val source: String,
    /** When they were downloaded; null for the bundled set. */
    val fetchedAtMillis: Long?,
) {
    val isBundled: Boolean get() = source == StoredKeps.BUNDLED
}

/** What the last attempts did, for the About dialog and for the 2-hour rule. */
data class KepsStatus(
    /** Per source key: when the server last answered with data or a permanent refusal. */
    val lastAnswerMillis: Map<String, Long> = emptyMap(),
    val lastSuccess: Success? = null,
    val lastFailure: Failure? = null,
) {
    data class Success(val source: String, val millis: Long, val count: Int)
    data class Failure(val source: String, val millis: Long, val reason: String)
}

sealed interface RefreshResult {
    /** New elements were downloaded, validated and stored. */
    data class Updated(val count: Int, val skipped: Int, val fetchedAtMillis: Long) : RefreshResult

    /**
     * Nothing was downloaded because the same source was fetched less than two hours ago. [lastSuccessMillis] is when
     * the stored elements for this source were fetched, if they were.
     */
    data class TooSoon(val nextAllowedAtMillis: Long, val lastSuccessMillis: Long?) : RefreshResult

    /** The existing elements were kept. */
    data class Failed(val outcome: DownloadOutcome) : RefreshResult
}

/**
 * Keeps the app's orbital elements: loads them, downloads new ones politely, and remembers what happened.
 *
 * Files (in the [FileStore] directory): one `keps_<SOURCE>.json` per source that has been downloaded (the elements, see
 * [KepsJson]) and `keps_status.json`. A source's elements are only ever replaced by a new download of **that source**
 * that was received, parsed and found to contain at least one satellite, and the replacement is atomic.
 *
 * @param bundledText returns the TLE text of the elements shipped inside the app
 * @param clock current time in milliseconds
 */
class KepsRepository(
    private val files: FileStore,
    private val downloader: KepsDownloader,
    private val bundledText: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
    /** The source the user has chosen; read each time, so a change is seen at once. */
    private val activeSource: () -> KepsSource = { KepsSource.DEFAULT },
) {

    /**
     * The elements to use: the chosen source's stored copy; if that source has never been downloaded, the stored copy
     * of whichever other source was fetched most recently; if nothing valid is stored, the bundled elements. No error
     * is reported for any of these.
     *
     * Each source keeps its own copy (see [fileName]), so downloading one never discards another. Celestrak refuses a
     * repeat download of the same data inside two hours, so a copy that was thrown away could not be fetched again.
     */
    fun load(): LoadedKeps {
        migrateLegacyFile()
        for (name in candidateFiles()) {
            val stored = files.readTextOrNull(name)?.let(KepsJson::decode) ?: continue
            if (stored.elements.isEmpty()) continue
            val tles = toTles(stored.elements)
            if (tles.isNotEmpty()) return LoadedKeps(tles, stored.source, stored.fetchedAtMillis.takeIf { it > 0 })
        }
        return LoadedKeps(toTles(TleTextParser.parse(bundledText()).elements), StoredKeps.BUNDLED, null)
    }

    /**
     * When the file [load] would read last changed, used to notice a background update or a change of source. Null if
     * there is no stored copy yet.
     */
    fun lastModifiedMillis(): Long? {
        migrateLegacyFile()
        return candidateFiles().firstOrNull()?.let(files::lastModified)
    }

    /**
     * The stored files to try, best first: the chosen source's, then the others, most recently written first. The old
     * single file comes last, so that if it could not be moved (see [migrateLegacyFile]) its elements are still shown
     * and not replaced by the bundled set while the download window may stop them being fetched again.
     */
    private fun candidateFiles(): List<String> {
        val active = fileName(activeSource())
        val others = KepsSource.entries.map(::fileName).filter { it != active }
            .mapNotNull { name -> files.lastModified(name)?.let { name to it } }
            .sortedByDescending { it.second }
            .map { it.first }
        val legacy = if (files.exists(LEGACY_KEPS_FILE)) listOf(LEGACY_KEPS_FILE) else emptyList()
        return (if (files.exists(active)) listOf(active) else emptyList()) + others + legacy
    }

    /**
     * Earlier versions kept every download in one file, `keps.json`, replacing it each time. Moves such a file to its
     * own source's name (unless that source already has one) and removes it. A file that cannot be moved is left in
     * place, still readable by [load], and tried again on the next call.
     *
     * The usual case, no old file, returns at once without taking [REFRESH_LOCK], so ordinary loads never wait for a
     * download. When there is an old file the move runs under the lock: otherwise it could check that the destination
     * is free, a download of that source could then finish and save its new elements, and the move would overwrite
     * them with the old copy.
     */
    private fun migrateLegacyFile() {
        if (!files.exists(LEGACY_KEPS_FILE)) return
        synchronized(REFRESH_LOCK) { migrateLegacyFileLocked() }
    }

    private fun migrateLegacyFileLocked() {
        val text = files.readTextOrNull(LEGACY_KEPS_FILE) ?: return
        val stored = KepsJson.decode(text)
        val source = stored?.let { s -> KepsSource.entries.firstOrNull { it.key == s.source } }
        if (stored != null && source != null && stored.elements.isNotEmpty() && !files.exists(fileName(source))) {
            try {
                files.writeTextAtomic(fileName(source), text)
            } catch (e: IOException) {
                return
            }
        }
        files.delete(LEGACY_KEPS_FILE)
    }

    /** When the stored copy of [source] was downloaded, or null if it has none. */
    private fun storedFetchedAt(source: KepsSource): Long? =
        files.readTextOrNull(fileName(source))?.let(KepsJson::decode)?.fetchedAtMillis?.takeIf { it > 0 }

    fun status(): KepsStatus = files.readTextOrNull(STATUS_FILE)?.let(::decodeStatus) ?: KepsStatus()

    /**
     * Downloads [source] if the rules allow. Never throws for network or data problems; they are returned as
     * [RefreshResult.Failed] and recorded in [status]. Blocks while another refresh is running, so call it off the main
     * thread.
     */
    fun refresh(source: KepsSource): RefreshResult = synchronized(REFRESH_LOCK) { refreshLocked(source) }

    private fun refreshLocked(source: KepsSource): RefreshResult {
        migrateLegacyFile()
        val now = clock()
        val before = status()
        val lastAnswer = before.lastAnswerMillis[source.key]
        if (!KepsDownloadPolicy.canDownload(lastAnswer, now)) {
            // When this source's own copy was fetched (not the last download of any source)
            val success = storedFetchedAt(source)
            return RefreshResult.TooSoon(KepsDownloadPolicy.nextAllowedAtMillis(lastAnswer, now), success)
        }

        val response = try {
            downloader.download(source.url)
        } catch (e: ResponseTooLargeException) {
            return fail(source, now, DownloadOutcome.Permanent(e.message ?: "Response too large"), answered = true)
        } catch (e: IOException) {
            return fail(source, now, KepsDownloadPolicy.classifyNetworkFailure(e.message ?: e.javaClass.simpleName), answered = false)
        }

        val outcome = KepsDownloadPolicy.classify(response.status)
        if (outcome !is DownloadOutcome.Success) {
            // 5xx is a temporary problem and does not count as the server having answered with data.
            return fail(source, now, outcome, answered = outcome is DownloadOutcome.Permanent)
        }

        val parsed = try {
            when (source.format) {
                KepsFormat.OMM_JSON -> OmmJsonParser.parse(response.body)
                KepsFormat.TLE_TEXT -> TleTextParser.parse(response.body)
            }
        } catch (e: InvalidKepsException) {
            val reason = "Not satellite data: ${response.body.trim().take(100)}"
            return fail(source, now, KepsDownloadPolicy.classifyUnusableBody(reason), answered = true)
        }
        if (parsed.elements.isEmpty()) {
            return fail(source, now, KepsDownloadPolicy.classifyUnusableBody("The response contained no satellites"), answered = true)
        }

        try {
            files.writeTextAtomic(fileName(source), KepsJson.encode(StoredKeps(source.key, now, parsed.elements)))
        } catch (e: IOException) {
            return fail(source, now, DownloadOutcome.Transient("Could not save the elements: ${e.message}"), answered = true)
        }
        writeStatus(
            before.copy(
                lastAnswerMillis = before.lastAnswerMillis + (source.key to now),
                lastSuccess = KepsStatus.Success(source.key, now, parsed.elements.size),
            )
        )
        return RefreshResult.Updated(parsed.elements.size, parsed.skipped, now)
    }

    private fun fail(source: KepsSource, now: Long, outcome: DownloadOutcome, answered: Boolean): RefreshResult.Failed {
        val before = status()
        val reason = when (outcome) {
            is DownloadOutcome.Transient -> outcome.reason
            is DownloadOutcome.Permanent -> outcome.reason
            DownloadOutcome.Success -> "Success"
        }
        writeStatus(
            before.copy(
                lastAnswerMillis = if (answered) before.lastAnswerMillis + (source.key to now) else before.lastAnswerMillis,
                lastFailure = KepsStatus.Failure(source.key, now, reason),
            )
        )
        return RefreshResult.Failed(outcome)
    }

    private fun toTles(elements: List<OrbitalElements>): List<TLE> =
        elements.mapNotNull { e ->
            try {
                TLE.fromElements(e)
            } catch (ex: IllegalArgumentException) {
                null
            }
        }

    private fun writeStatus(status: KepsStatus) {
        try {
            files.writeTextAtomic(STATUS_FILE, encodeStatus(status))
        } catch (e: IOException) {
            // The status is advisory; losing it only means the 2-hour rule is forgotten once.
        }
    }

    private fun encodeStatus(s: KepsStatus): String {
        val answers = JSONObject()
        for ((k, v) in s.lastAnswerMillis) answers.put(k, v)
        val root = JSONObject().put("lastAnswerMillis", answers)
        s.lastSuccess?.let {
            root.put("lastSuccess", JSONObject().put("source", it.source).put("millis", it.millis).put("count", it.count))
        }
        s.lastFailure?.let {
            root.put("lastFailure", JSONObject().put("source", it.source).put("millis", it.millis).put("reason", it.reason))
        }
        return root.toString()
    }

    private fun decodeStatus(text: String): KepsStatus? = try {
        val root = JSONObject(text)
        val answers = root.optJSONObject("lastAnswerMillis")
        val map = HashMap<String, Long>()
        answers?.keys()?.forEach { key -> map[key] = answers.getLong(key) }
        KepsStatus(
            lastAnswerMillis = map,
            lastSuccess = root.optJSONObject("lastSuccess")?.let {
                KepsStatus.Success(it.getString("source"), it.getLong("millis"), it.getInt("count"))
            },
            lastFailure = root.optJSONObject("lastFailure")?.let {
                KepsStatus.Failure(it.getString("source"), it.getLong("millis"), it.getString("reason"))
            },
        )
    } catch (e: JSONException) {
        null
    }

    companion object {
        /**
         * The activity and the background worker can both ask for a refresh. Without this lock they could each pass the
         * two-hour check and both download, and the second would be refused by the server. With it, the second caller
         * waits and then finds the first one's result ([RefreshResult.TooSoon]).
         */
        private val REFRESH_LOCK = Any()

        /** The single file earlier versions used for every download; migrated to a per-source file on first use. */
        const val LEGACY_KEPS_FILE = "keps.json"
        const val STATUS_FILE = "keps_status.json"

        /** The file holding the elements last downloaded from [source]. */
        fun fileName(source: KepsSource): String = "keps_${source.key}.json"
    }
}
