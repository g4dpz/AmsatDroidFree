// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

/** What a download attempt's outcome means for whether and when to try again. */
sealed interface DownloadOutcome {
    /** The server answered with data (the body still has to be checked by the parser). */
    data object Success : DownloadOutcome

    /** A temporary problem (network failure, timeout, server error). Worth retrying a few times. */
    data class Transient(val reason: String) : DownloadOutcome

    /**
     * The request will not succeed by repeating it (redirect, forbidden, not found, rate limited, other client error,
     * or a body that is not satellite data). Never retried before the next scheduled run or a user action.
     */
    data class Permanent(val reason: String) : DownloadOutcome
}

/**
 * Rules for downloading keps politely.
 *
 * Celestrak refreshes its data every 2 hours, refuses (HTTP 403) a repeat download of the same data inside that time,
 * and puts an IP address into its firewall after more than 50 errors of 301, 403 or 404 in 2 hours. So:
 *  - the same source is never fetched more than once per [KepsSource.MIN_DOWNLOAD_INTERVAL_MILLIS];
 *  - only temporary problems are retried, and only a few times;
 *  - 301, 403, 404, 429 (and other client errors) are never retried.
 *
 * Pure functions, no Android dependencies.
 */
object KepsDownloadPolicy {

    /** Total attempts for one scheduled run, including the first. */
    const val MAX_ATTEMPTS = 3

    /**
     * True if [source] may be downloaded now.
     *
     * [lastAttemptMillis] is the time the server last *answered* a request for that source with data or with a
     * permanent refusal (so repeated errors cannot be hammered); null if there has been no such answer. A network
     * failure or a 5xx is not an answer in this sense and does not start the window, otherwise retrying after a
     * temporary problem would be blocked. If the clock has moved backwards, the download is allowed so it cannot be
     * blocked for an unbounded time.
     */
    fun canDownload(lastAttemptMillis: Long?, nowMillis: Long): Boolean {
        if (lastAttemptMillis == null) return true
        val elapsed = nowMillis - lastAttemptMillis
        return elapsed < 0 || elapsed >= KepsSource.MIN_DOWNLOAD_INTERVAL_MILLIS
    }

    /** When the next download is allowed, or [nowMillis] if one is allowed already. */
    fun nextAllowedAtMillis(lastAttemptMillis: Long?, nowMillis: Long): Long =
        if (canDownload(lastAttemptMillis, nowMillis)) nowMillis
        else lastAttemptMillis!! + KepsSource.MIN_DOWNLOAD_INTERVAL_MILLIS

    /** Classifies an HTTP status code. */
    fun classify(httpStatus: Int): DownloadOutcome = when (httpStatus) {
        in 200..299 -> DownloadOutcome.Success
        in 300..399 -> DownloadOutcome.Permanent("HTTP $httpStatus (redirect; redirects are not followed)")
        429 -> DownloadOutcome.Permanent("HTTP 429 (rate limited)")
        in 400..499 -> DownloadOutcome.Permanent("HTTP $httpStatus")
        in 500..599 -> DownloadOutcome.Transient("HTTP $httpStatus (server error)")
        else -> DownloadOutcome.Permanent("Unexpected HTTP status $httpStatus")
    }

    /** A network-level failure such as a timeout, unreachable host or dropped connection. */
    fun classifyNetworkFailure(description: String): DownloadOutcome = DownloadOutcome.Transient(description)

    /** A response with a success status whose body is not satellite data (for example an error message). */
    fun classifyUnusableBody(description: String): DownloadOutcome = DownloadOutcome.Permanent(description)

    /**
     * Whether a failed run should be retried.
     *
     * @param previousRuns how many times this work has already run (0 on the first run)
     */
    fun shouldRetry(outcome: DownloadOutcome, previousRuns: Int): Boolean =
        outcome is DownloadOutcome.Transient && previousRuns + 1 < MAX_ATTEMPTS
}
