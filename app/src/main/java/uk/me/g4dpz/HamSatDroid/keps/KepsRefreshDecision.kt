package uk.me.g4dpz.HamSatDroid.keps

import uk.me.g4dpz.HamSatDroid.data.DownloadOutcome
import uk.me.g4dpz.HamSatDroid.data.KepsDownloadPolicy
import uk.me.g4dpz.HamSatDroid.data.RefreshResult

/** What the background worker should tell WorkManager. */
enum class WorkerDecision {
    /** Finished (including "nothing to do because the keps are already current"). */
    SUCCESS,

    /** A temporary problem; try again later with backoff. */
    RETRY,

    /** Gave up for this run. Periodic work still runs again at the next period; nothing retries sooner. */
    FAILURE
}

/**
 * Maps a refresh result to a worker decision, following the download rules: only temporary problems are retried, and
 * at most [KepsDownloadPolicy.MAX_ATTEMPTS] times in total. A refusal such as HTTP 403 or 404, or a response that is
 * not satellite data, is never retried, because repeating it cannot help and Celestrak blocks clients that do.
 * Pure function, no Android dependencies.
 *
 * @param previousRuns how many times this work has already run (`runAttemptCount`; 0 on the first run)
 */
object KepsRefreshDecision {
    fun decide(result: RefreshResult, previousRuns: Int): WorkerDecision = when (result) {
        is RefreshResult.Updated, is RefreshResult.TooSoon -> WorkerDecision.SUCCESS
        is RefreshResult.Failed ->
            if (result.outcome is DownloadOutcome.Transient && KepsDownloadPolicy.shouldRetry(result.outcome, previousRuns)) {
                WorkerDecision.RETRY
            } else {
                WorkerDecision.FAILURE
            }
    }
}
