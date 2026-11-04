package uk.me.g4dpz.HamSatDroid.keps

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.data.DownloadOutcome
import uk.me.g4dpz.HamSatDroid.data.RefreshResult

class KepsRefreshDecisionTest {

    private val updated = RefreshResult.Updated(count = 98, skipped = 0, fetchedAtMillis = 1L)
    private val tooSoon = RefreshResult.TooSoon(nextAllowedAtMillis = 2L, lastSuccessMillis = 1L)
    private fun transient() = RefreshResult.Failed(DownloadOutcome.Transient("HTTP 503"))
    private fun permanent() = RefreshResult.Failed(DownloadOutcome.Permanent("HTTP 403"))

    @Test
    fun updatedIsSuccessAtEveryAttempt() {
        for (run in 0..5) assertEquals(WorkerDecision.SUCCESS, KepsRefreshDecision.decide(updated, run))
    }

    @Test
    fun alreadyCurrentIsSuccessNotAnError() {
        for (run in 0..5) assertEquals(WorkerDecision.SUCCESS, KepsRefreshDecision.decide(tooSoon, run))
    }

    @Test
    fun temporaryFailuresRetryTwiceThenGiveUp() {
        assertEquals(WorkerDecision.RETRY, KepsRefreshDecision.decide(transient(), 0))
        assertEquals(WorkerDecision.RETRY, KepsRefreshDecision.decide(transient(), 1))
        assertEquals(WorkerDecision.FAILURE, KepsRefreshDecision.decide(transient(), 2))
        assertEquals(WorkerDecision.FAILURE, KepsRefreshDecision.decide(transient(), 9))
    }

    @Test
    fun refusalsAreNeverRetried() {
        for (run in 0..5) assertEquals(WorkerDecision.FAILURE, KepsRefreshDecision.decide(permanent(), run))
    }
}
