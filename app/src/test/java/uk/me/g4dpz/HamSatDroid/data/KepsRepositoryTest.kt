// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.me.g4dpz.satellite.OrbitalElements
import java.io.File
import java.io.IOException

class KepsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeDownloader : KepsDownloader {
        val requested = ArrayList<String>()
        var next: () -> DownloadResponse = { throw IOException("no response scripted") }
        override fun download(url: String): DownloadResponse {
            requested.add(url)
            return next()
        }
    }

    private fun resource(name: String) =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }

    private val celestrakJson get() = resource("celestrak_amateur_sample.json")
    private val bundledTle get() = File("src/main/res/raw/nasabare.txt").readText()
    private val hour = 60L * 60 * 1000

    private var now = 1_800_000_000_000L
    private val downloader = FakeDownloader()
    private fun files() = FileStore(File(tmp.root, "data"))
    private fun repo() = KepsRepository(files(), downloader, { bundledTle }, { now })

    private fun ok(body: String) = { DownloadResponse(200, body) }

    // ---- loading ----

    @Test
    fun firstRunUsesBundledKepsAndStoresThemSilently() {
        val loaded = repo().load()
        assertTrue(loaded.isBundled)
        assertNull(loaded.fetchedAtMillis)
        assertEquals(60, loaded.tles.size)
        val stored = KepsJson.decode(files().readTextOrNull(KepsRepository.KEPS_FILE)!!)!!
        assertEquals(StoredKeps.BUNDLED, stored.source)
        assertEquals(60, stored.elements.size)
        assertTrue(downloader.requested.isEmpty())
    }

    @Test
    fun aCorruptFileFallsBackToBundledAndIsRewritten() {
        files().writeTextAtomic(KepsRepository.KEPS_FILE, "{ this is not valid json")
        val loaded = repo().load()
        assertTrue(loaded.isBundled)
        assertEquals(60, loaded.tles.size)
        assertNotNull(KepsJson.decode(files().readTextOrNull(KepsRepository.KEPS_FILE)!!))
    }

    @Test
    fun aFileWithNoSatellitesFallsBackToBundled() {
        files().writeTextAtomic(KepsRepository.KEPS_FILE, KepsJson.encode(StoredKeps("AMATEUR_CELESTRAK", 5, emptyList())))
        assertTrue(repo().load().isBundled)
    }

    @Test
    fun aWrongSchemaFallsBackToBundled() {
        files().writeTextAtomic(KepsRepository.KEPS_FILE, """{"schema":99,"source":"X","fetchedAtMillis":1,"elements":[]}""")
        assertTrue(repo().load().isBundled)
    }

    // ---- successful refresh ----

    @Test
    fun refreshFromCelestrakJsonStoresTheSatellitesIncludingSixDigitOnes() {
        downloader.next = ok(celestrakJson)
        val result = repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        assertEquals(RefreshResult.Updated(count = 8, skipped = 0, fetchedAtMillis = now), result)
        assertEquals(listOf(KepsSource.AMATEUR_CELESTRAK.url), downloader.requested)

        val loaded = repo().load()
        assertFalse(loaded.isBundled)
        assertEquals("AMATEUR_CELESTRAK", loaded.source)
        assertEquals(now, loaded.fetchedAtMillis)
        assertEquals(8, loaded.tles.size)
        assertTrue(loaded.tles.any { it.catnum == 101098 })
        assertEquals(KepsStatus.Success("AMATEUR_CELESTRAK", now, 8), repo().status().lastSuccess)
    }

    @Test
    fun refreshFromAmsatTleText() {
        downloader.next = ok(bundledTle)
        val result = repo().refresh(KepsSource.AMATEUR_AMSAT)
        assertTrue(result is RefreshResult.Updated)
        assertEquals(60, (result as RefreshResult.Updated).count)
        assertEquals("AMATEUR_AMSAT", repo().load().source)
    }

    @Test
    fun storedElementsSurviveTheJsonRoundTripExactly() {
        val original = OmmJsonParser.parse(celestrakJson).elements
        val decoded = KepsJson.decode(KepsJson.encode(StoredKeps("AMATEUR_CELESTRAK", 42, original)))!!
        assertEquals(original, decoded.elements)
        assertEquals(42L, decoded.fetchedAtMillis)
    }

    @Test
    fun theBundledSetSurvivesTheJsonRoundTripExactly() {
        val original = TleTextParser.parse(bundledTle).elements
        val decoded = KepsJson.decode(KepsJson.encode(StoredKeps(StoredKeps.BUNDLED, 0, original)))!!
        assertEquals(original, decoded.elements)
    }

    // ---- the two hour rule ----

    @Test
    fun aSecondRefreshInsideTwoHoursDoesNotTouchTheNetwork() {
        downloader.next = ok(celestrakJson)
        repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        val fetchedAt = now

        now += hour
        val result = repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        assertEquals(1, downloader.requested.size)
        assertEquals(RefreshResult.TooSoon(fetchedAt + 2 * hour, fetchedAt), result)
    }

    @Test
    fun afterTwoHoursItDownloadsAgain() {
        downloader.next = ok(celestrakJson)
        repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        now += 2 * hour
        assertTrue(repo().refresh(KepsSource.AMATEUR_CELESTRAK) is RefreshResult.Updated)
        assertEquals(2, downloader.requested.size)
    }

    @Test
    fun differentSourcesHaveSeparateWindows() {
        downloader.next = ok(celestrakJson)
        repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        downloader.next = ok(bundledTle)
        assertTrue(repo().refresh(KepsSource.AMATEUR_AMSAT) is RefreshResult.Updated)
        assertEquals(2, downloader.requested.size)
    }

    @Test
    fun theWindowSurvivesARestartBecauseItIsStoredOnDisk() {
        downloader.next = ok(celestrakJson)
        repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        val freshRepository = repo() // new object, same files
        assertTrue(freshRepository.refresh(KepsSource.AMATEUR_CELESTRAK) is RefreshResult.TooSoon)
        assertEquals(1, downloader.requested.size)
    }

    // ---- failures keep the old data ----

    private fun seedWithGoodData(): List<Int> {
        downloader.next = ok(celestrakJson)
        repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        now += 3 * hour // out of the window so the next refresh really tries
        downloader.requested.clear()
        return repo().load().tles.map { it.catnum }
    }

    @Test
    fun permanentHttpErrorsKeepTheOldDataAndAreNotRepeatedInsideTheWindow() {
        for (status in listOf(301, 403, 404, 429)) {
            tmp.delete(); tmp.create()
            val oldCats = seedWithGoodData()
            downloader.next = { DownloadResponse(status, "GP data has not updated since your last successful download") }

            val result = repo().refresh(KepsSource.AMATEUR_CELESTRAK)
            assertTrue("HTTP $status", result is RefreshResult.Failed && result.outcome is DownloadOutcome.Permanent)
            assertEquals("HTTP $status kept data", oldCats, repo().load().tles.map { it.catnum })
            assertNotNull(repo().status().lastFailure)

            // trying again straight away must not reach the server
            now += 10 * 60 * 1000
            val again = repo().refresh(KepsSource.AMATEUR_CELESTRAK)
            assertTrue("HTTP $status second try", again is RefreshResult.TooSoon)
            assertEquals("HTTP $status requests", 1, downloader.requested.size)
        }
    }

    @Test
    fun anErrorMessageDeliveredWithHttp200IsRejectedAndNotRetried() {
        val oldCats = seedWithGoodData()
        downloader.next = ok("Invalid query: \"GROUP=noaa&FORMAT=tle\" (GROUP=noaa not found)")
        val result = repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        assertTrue(result is RefreshResult.Failed && result.outcome is DownloadOutcome.Permanent)
        assertEquals(oldCats, repo().load().tles.map { it.catnum })
        assertTrue((repo().status().lastFailure!!.reason).contains("Invalid query"))
        assertTrue(repo().refresh(KepsSource.AMATEUR_CELESTRAK) is RefreshResult.TooSoon)
    }

    @Test
    fun anEmptyListKeepsTheOldData() {
        val oldCats = seedWithGoodData()
        downloader.next = ok("[]")
        val result = repo().refresh(KepsSource.NEW_CELESTRAK)
        assertTrue(result is RefreshResult.Failed && result.outcome is DownloadOutcome.Permanent)
        assertEquals(oldCats, repo().load().tles.map { it.catnum })
    }

    @Test
    fun serverErrorsAreTemporaryAndMayBeRetriedImmediately() {
        val oldCats = seedWithGoodData()
        downloader.next = { DownloadResponse(503, "busy") }
        val first = repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        assertTrue(first is RefreshResult.Failed && first.outcome is DownloadOutcome.Transient)
        assertEquals(oldCats, repo().load().tles.map { it.catnum })

        // a 5xx does not start the two hour window, so a retry goes to the network
        downloader.next = ok(celestrakJson)
        assertTrue(repo().refresh(KepsSource.AMATEUR_CELESTRAK) is RefreshResult.Updated)
        assertEquals(2, downloader.requested.size)
    }

    @Test
    fun networkFailuresAreTemporaryAndDoNotStartTheWindow() {
        val oldCats = seedWithGoodData()
        downloader.next = { throw IOException("Unable to resolve host") }
        val first = repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        assertTrue(first is RefreshResult.Failed && first.outcome is DownloadOutcome.Transient)
        assertEquals(oldCats, repo().load().tles.map { it.catnum })
        assertEquals("Unable to resolve host", repo().status().lastFailure!!.reason)

        downloader.next = ok(celestrakJson)
        assertTrue(repo().refresh(KepsSource.AMATEUR_CELESTRAK) is RefreshResult.Updated)
    }

    @Test
    fun anOversizedResponseIsPermanent() {
        val oldCats = seedWithGoodData()
        downloader.next = { throw ResponseTooLargeException(1024) }
        val result = repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        assertTrue(result is RefreshResult.Failed && result.outcome is DownloadOutcome.Permanent)
        assertEquals(oldCats, repo().load().tles.map { it.catnum })
    }

    @Test
    fun aFailureToSaveKeepsTheOldFileAndReportsATemporaryProblem() {
        val oldCats = seedWithGoodData()
        // A directory in the way of the temporary file makes the write fail.
        File(tmp.root, "data/keps.json.tmp").mkdirs()
        downloader.next = ok(celestrakJson.replace("OSCAR 7 (AO-7)", "CHANGED"))
        val result = repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        assertTrue(result is RefreshResult.Failed && result.outcome is DownloadOutcome.Transient)
        val after = repo().load()
        assertEquals(oldCats, after.tles.map { it.catnum })
        assertTrue(after.tles.none { it.name == "CHANGED" })
    }

    @Test
    fun aPartlyBadDownloadStillStoresTheGoodSatellitesAndCountsTheRest() {
        val json = """[
          {"OBJECT_NAME":"GOOD","NORAD_CAT_ID":1,"EPOCH":"2026-11-04T00:00:00","MEAN_MOTION":14.0,"ECCENTRICITY":0.001,
           "INCLINATION":98.0,"RA_OF_ASC_NODE":10.0,"ARG_OF_PERICENTER":20.0,"MEAN_ANOMALY":30.0},
          {"OBJECT_NAME":"BAD","NORAD_CAT_ID":2}
        ]"""
        downloader.next = ok(json)
        val result = repo().refresh(KepsSource.AMATEUR_CELESTRAK)
        assertEquals(RefreshResult.Updated(1, 1, now), result)
        assertEquals(listOf("GOOD"), repo().load().tles.map { it.name })
    }

    // ---- concurrency ----

    @Test
    fun twoRepositoriesRefreshingAtOnceMakeOnlyOneRequest() {
        // The activity and the background worker each have their own repository object over the same files.
        val calls = java.util.concurrent.atomic.AtomicInteger(0)
        val slow = KepsDownloader {
            calls.incrementAndGet()
            Thread.sleep(300) // long enough for the other caller to arrive while this one is still downloading
            DownloadResponse(200, celestrakJson)
        }
        val a = KepsRepository(files(), slow, { bundledTle }, { now })
        val b = KepsRepository(files(), slow, { bundledTle }, { now })
        val start = java.util.concurrent.CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(ArrayList<RefreshResult>())
        val threads = listOf(a, b).map { repo ->
            Thread {
                start.await()
                results.add(repo.refresh(KepsSource.AMATEUR_CELESTRAK))
            }.also { it.start() }
        }
        start.countDown()
        threads.forEach { it.join(10_000) }

        assertEquals("only one download", 1, calls.get())
        assertEquals(1, results.count { it is RefreshResult.Updated })
        assertEquals(1, results.count { it is RefreshResult.TooSoon })
    }

    // ---- misc ----

    @Test
    fun lastModifiedChangesWhenTheElementsAreReplaced() {
        assertNull(repo().lastModifiedMillis())
        repo().load()
        assertNotNull(repo().lastModifiedMillis())
    }

    @Test
    fun aMissingOrCorruptStatusFileIsTreatedAsNoHistory() {
        assertEquals(KepsStatus(), repo().status())
        files().writeTextAtomic(KepsRepository.STATUS_FILE, "garbage")
        assertEquals(KepsStatus(), repo().status())
    }

    // ---- the real downloader ----

    @Test
    fun theRealDownloaderRefusesPlainHttp() {
        try {
            HttpsKepsDownloader("test").download("http://celestrak.org/NORAD/elements/gp.php?GROUP=amateur&FORMAT=JSON")
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // fine
        }
    }

    @Test
    fun theUserAgentNamesTheAppAndItsVersion() {
        val ua = HttpsKepsDownloader.userAgent("1.0.14", "15")
        assertTrue(ua, ua.startsWith("AmsatDroid/1.0.14 "))
        assertTrue(ua.contains("Android 15"))
    }

    @Test
    fun elementsStillValidateAfterDecoding() {
        val decoded = KepsJson.decode(KepsJson.encode(StoredKeps("X", 1, OmmJsonParser.parse(celestrakJson).elements)))!!
        decoded.elements.forEach(OrbitalElements::validate)
    }
}
