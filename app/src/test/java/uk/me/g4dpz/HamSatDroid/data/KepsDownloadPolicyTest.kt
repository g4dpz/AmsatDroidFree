// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KepsDownloadPolicyTest {

    private val hour = 60L * 60 * 1000
    private val window = KepsSource.MIN_DOWNLOAD_INTERVAL_MILLIS
    private val t0 = 1_700_000_000_000L

    @Test
    fun neverFetchedMayDownload() {
        assertTrue(KepsDownloadPolicy.canDownload(null, t0))
    }

    @Test
    fun insideTheTwoHourWindowMayNotDownload() {
        assertFalse(KepsDownloadPolicy.canDownload(t0, t0))
        assertFalse(KepsDownloadPolicy.canDownload(t0, t0 + 1))
        assertFalse(KepsDownloadPolicy.canDownload(t0, t0 + hour))
        assertFalse(KepsDownloadPolicy.canDownload(t0, t0 + window - 1))
    }

    @Test
    fun atAndAfterTwoHoursMayDownload() {
        assertTrue(KepsDownloadPolicy.canDownload(t0, t0 + window))
        assertTrue(KepsDownloadPolicy.canDownload(t0, t0 + window + 1))
        assertTrue(KepsDownloadPolicy.canDownload(t0, t0 + 24 * hour))
    }

    @Test
    fun aClockMovedBackwardsDoesNotBlockForever() {
        assertTrue(KepsDownloadPolicy.canDownload(t0, t0 - 5 * hour))
    }

    @Test
    fun nextAllowedTime() {
        assertEquals(t0 + window, KepsDownloadPolicy.nextAllowedAtMillis(t0, t0 + hour))
        assertEquals(t0 + 3 * hour, KepsDownloadPolicy.nextAllowedAtMillis(t0, t0 + 3 * hour))
        assertEquals(t0, KepsDownloadPolicy.nextAllowedAtMillis(null, t0))
    }

    @Test
    fun successStatusesAreSuccess() {
        for (code in listOf(200, 201, 204, 299)) {
            assertEquals("HTTP $code", DownloadOutcome.Success, KepsDownloadPolicy.classify(code))
        }
    }

    @Test
    fun theStatusesCelestrakWarnsAboutAreNeverRetried() {
        for (code in listOf(301, 302, 307, 308, 403, 404, 429)) {
            val outcome = KepsDownloadPolicy.classify(code)
            assertTrue("HTTP $code should be permanent but was $outcome", outcome is DownloadOutcome.Permanent)
            for (previousRuns in 0..5) {
                assertFalse("HTTP $code must not retry", KepsDownloadPolicy.shouldRetry(outcome, previousRuns))
            }
        }
    }

    @Test
    fun otherClientErrorsArePermanentToo() {
        for (code in listOf(400, 401, 408, 410, 451, 499)) {
            assertTrue("HTTP $code", KepsDownloadPolicy.classify(code) is DownloadOutcome.Permanent)
        }
    }

    @Test
    fun serverErrorsAreTransient() {
        for (code in listOf(500, 502, 503, 504, 599)) {
            assertTrue("HTTP $code", KepsDownloadPolicy.classify(code) is DownloadOutcome.Transient)
        }
    }

    @Test
    fun oddStatusesAreNotRetried() {
        for (code in listOf(0, 100, 199, 600, -1)) {
            assertTrue("HTTP $code", KepsDownloadPolicy.classify(code) is DownloadOutcome.Permanent)
        }
    }

    @Test
    fun networkFailuresAreTransient() {
        assertTrue(KepsDownloadPolicy.classifyNetworkFailure("timeout") is DownloadOutcome.Transient)
    }

    @Test
    fun anUnusableBodyIsPermanent() {
        val outcome = KepsDownloadPolicy.classifyUnusableBody("Invalid query: GROUP=noaa not found")
        assertTrue(outcome is DownloadOutcome.Permanent)
        assertFalse(KepsDownloadPolicy.shouldRetry(outcome, 0))
    }

    @Test
    fun transientFailuresRetryAtMostThreeAttemptsInTotal() {
        val transient = KepsDownloadPolicy.classifyNetworkFailure("down")
        assertTrue(KepsDownloadPolicy.shouldRetry(transient, 0)) // attempt 1 failed -> try attempt 2
        assertTrue(KepsDownloadPolicy.shouldRetry(transient, 1)) // attempt 2 failed -> try attempt 3
        assertFalse(KepsDownloadPolicy.shouldRetry(transient, 2)) // attempt 3 failed -> give up
        assertFalse(KepsDownloadPolicy.shouldRetry(transient, 10))
        assertEquals(3, KepsDownloadPolicy.MAX_ATTEMPTS)
    }

    @Test
    fun successIsNeverRetried() {
        assertFalse(KepsDownloadPolicy.shouldRetry(DownloadOutcome.Success, 0))
    }
}
