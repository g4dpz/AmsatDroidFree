// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** The transponder database the app is using, and where it came from. */
data class LoadedTransponderDatabase(
    val database: TransponderDatabase,
    /** True when the bundled copy is in use because no valid synced copy exists. */
    val isBundled: Boolean,
    /** When the synced copy was downloaded; null when the bundled copy is in use. */
    val fetchedAtMillis: Long?,
)

/** What the last sync attempts did, for display and for the minimum-interval rule. */
data class TransponderSyncStatus(
    /** When the server last answered with data or a permanent refusal. */
    val lastAnswerMillis: Long? = null,
    val lastSuccess: Success? = null,
    val lastFailure: Failure? = null,
) {
    data class Success(val millis: Long, val count: Int)
    data class Failure(val millis: Long, val reason: String)
}

sealed interface TransponderSyncResult {
    /** A new database was downloaded, parsed and stored. */
    data class Updated(val count: Int, val fetchedAtMillis: Long) : TransponderSyncResult

    /** Nothing was downloaded because the published file was fetched less than the minimum interval ago. */
    data class TooSoon(val nextAllowedAtMillis: Long, val lastSuccessMillis: Long?) : TransponderSyncResult

    /** The existing database was kept. */
    data class Failed(val outcome: DownloadOutcome) : TransponderSyncResult
}

/**
 * Keeps the app's satellite transponder database (OscarWatch's `satellite_database.json`).
 *
 * A copy is bundled with the app as the fallback; the published copy is synced from [REMOTE_URL] and preferred when
 * present (synced → bundled). The database is read-only: a sync replaces the synced copy wholesale; the app never
 * edits it or lets the user override it. Downloads use the same safeguards as the keps refresh (https only via
 * [HttpsKepsDownloader], no redirects, size cap, timeouts, a User-Agent naming the app, and a minimum interval between
 * fetches shared by automatic and manual runs).
 *
 * Files (in the [FileStore] directory): `satellite_database.json` (the synced copy) and `transponder_sync_status.json`.
 *
 * @param bundledText returns the JSON of the database shipped inside the app
 * @param clock current time in milliseconds
 */
class TransponderDatabaseRepository(
    private val files: FileStore,
    private val downloader: KepsDownloader,
    private val bundledText: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * The database to use: the synced copy if a valid one is stored, otherwise the bundled copy. A missing, corrupt or
     * wrong-shaped synced copy falls back to the bundled copy; a bad bundled copy yields an empty database. Never
     * throws.
     */
    fun load(): LoadedTransponderDatabase {
        val storedText = files.readTextOrNull(DATABASE_FILE)
        if (storedText != null) {
            val parsed = TransponderDatabase.parse(storedText)
            if (!parsed.isEmpty) {
                return LoadedTransponderDatabase(parsed, isBundled = false, fetchedAtMillis = status().lastSuccess?.millis)
            }
        }
        val bundled = TransponderDatabase.parse(bundledText())
        return LoadedTransponderDatabase(bundled, isBundled = true, fetchedAtMillis = null)
    }

    /** When the synced database last changed, used to notice a background update. Null if there is no synced file. */
    fun lastModifiedMillis(): Long? = files.lastModified(DATABASE_FILE)

    fun status(): TransponderSyncStatus = files.readTextOrNull(STATUS_FILE)?.let(::decodeStatus) ?: TransponderSyncStatus()

    /**
     * Downloads the published database if the minimum-interval rule allows. Never throws for network or data problems;
     * they are returned as [TransponderSyncResult.Failed] and recorded in [status]. Blocks while another sync is
     * running, so call it off the main thread.
     */
    fun sync(): TransponderSyncResult = synchronized(SYNC_LOCK) { syncLocked() }

    private fun syncLocked(): TransponderSyncResult {
        val now = clock()
        val before = status()
        if (!KepsDownloadPolicy.canDownload(before.lastAnswerMillis, now)) {
            return TransponderSyncResult.TooSoon(
                KepsDownloadPolicy.nextAllowedAtMillis(before.lastAnswerMillis, now),
                before.lastSuccess?.millis,
            )
        }

        val response = try {
            downloader.download(REMOTE_URL)
        } catch (e: ResponseTooLargeException) {
            return fail(now, DownloadOutcome.Permanent(e.message ?: "Response too large"), answered = true)
        } catch (e: IOException) {
            return fail(now, KepsDownloadPolicy.classifyNetworkFailure(e.message ?: e.javaClass.simpleName), answered = false)
        }

        val outcome = KepsDownloadPolicy.classify(response.status)
        if (outcome !is DownloadOutcome.Success) {
            return fail(now, outcome, answered = outcome is DownloadOutcome.Permanent)
        }

        val parsed = TransponderDatabase.parse(response.body)
        if (parsed.isEmpty) {
            val reason = "Not a transponder database: ${response.body.trim().take(100)}"
            return fail(now, KepsDownloadPolicy.classifyUnusableBody(reason), answered = true)
        }

        try {
            files.writeTextAtomic(DATABASE_FILE, response.body)
        } catch (e: IOException) {
            return fail(now, DownloadOutcome.Transient("Could not save the database: ${e.message}"), answered = true)
        }
        writeStatus(
            before.copy(
                lastAnswerMillis = now,
                lastSuccess = TransponderSyncStatus.Success(now, parsed.satellites.size),
            )
        )
        return TransponderSyncResult.Updated(parsed.satellites.size, now)
    }

    private fun fail(now: Long, outcome: DownloadOutcome, answered: Boolean): TransponderSyncResult.Failed {
        val before = status()
        val reason = when (outcome) {
            is DownloadOutcome.Transient -> outcome.reason
            is DownloadOutcome.Permanent -> outcome.reason
            DownloadOutcome.Success -> "Success"
        }
        writeStatus(
            before.copy(
                lastAnswerMillis = if (answered) now else before.lastAnswerMillis,
                lastFailure = TransponderSyncStatus.Failure(now, reason),
            )
        )
        return TransponderSyncResult.Failed(outcome)
    }

    private fun writeStatus(status: TransponderSyncStatus) {
        try {
            files.writeTextAtomic(STATUS_FILE, encodeStatus(status))
        } catch (e: IOException) {
            // The status is advisory; losing it only means the minimum-interval rule is forgotten once.
        }
    }

    private fun encodeStatus(s: TransponderSyncStatus): String {
        val root = JSONObject()
        s.lastAnswerMillis?.let { root.put("lastAnswerMillis", it) }
        s.lastSuccess?.let { root.put("lastSuccess", JSONObject().put("millis", it.millis).put("count", it.count)) }
        s.lastFailure?.let { root.put("lastFailure", JSONObject().put("millis", it.millis).put("reason", it.reason)) }
        return root.toString()
    }

    private fun decodeStatus(text: String): TransponderSyncStatus? = try {
        val root = JSONObject(text)
        TransponderSyncStatus(
            lastAnswerMillis = if (root.has("lastAnswerMillis")) root.getLong("lastAnswerMillis") else null,
            lastSuccess = root.optJSONObject("lastSuccess")?.let {
                TransponderSyncStatus.Success(it.getLong("millis"), it.getInt("count"))
            },
            lastFailure = root.optJSONObject("lastFailure")?.let {
                TransponderSyncStatus.Failure(it.getLong("millis"), it.getString("reason"))
            },
        )
    } catch (e: JSONException) {
        null
    }

    companion object {
        /** The published transponder database maintained by OscarWatch (Peter Goodhall, magicbug). */
        const val REMOTE_URL = "https://tle.oscarwatch.org/satellite_database.json"

        const val DATABASE_FILE = "satellite_database.json"
        const val STATUS_FILE = "transponder_sync_status.json"

        /** The activity and the background worker can both ask for a sync; this serialises them, as for the keps. */
        private val SYNC_LOCK = Any()
    }
}
