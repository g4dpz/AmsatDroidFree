// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class TransponderDatabaseRepositoryTest {

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

    private val sampleJson get() = resource("satellite_database_sample.json")
    private val bundledJson get() = File("src/main/res/raw/satellite_database.json").readText()
    private val hour = 60L * 60 * 1000

    private var now = 1_800_000_000_000L
    private val downloader = FakeDownloader()
    private fun files() = FileStore(File(tmp.root, "data"))
    private fun repo() = TransponderDatabaseRepository(files(), downloader, { bundledJson }, { now })

    private fun ok(body: String) = { DownloadResponse(200, body) }

    // ---- loading ----

    @Test
    fun `first run uses the bundled database`() {
        val loaded = repo().load()
        assertTrue(loaded.isBundled)
        assertNull(loaded.fetchedAtMillis)
        assertTrue(loaded.database.satellites.size > 40)
        assertTrue(downloader.requested.isEmpty())
    }

    @Test
    fun `a stored synced copy is preferred over the bundled one`() {
        files().writeTextAtomic(TransponderDatabaseRepository.DATABASE_FILE, sampleJson)
        val loaded = repo().load()
        assertFalse(loaded.isBundled)
        assertEquals(setOf(7530, 25544, 44909), loaded.database.satellites.map { it.noradId }.toSet())
    }

    @Test
    fun `a corrupt synced copy falls back to the bundled database`() {
        files().writeTextAtomic(TransponderDatabaseRepository.DATABASE_FILE, "not json")
        assertTrue(repo().load().isBundled)
    }

    // ---- syncing ----

    @Test
    fun `a successful sync stores the synced copy atomically and records success`() {
        downloader.next = ok(sampleJson)
        val result = repo().sync()
        assertTrue(result is TransponderSyncResult.Updated)
        assertEquals(3, (result as TransponderSyncResult.Updated).count)
        assertEquals(TransponderDatabaseRepository.REMOTE_URL, downloader.requested.single())
        // The stored bytes are exactly what the server sent.
        assertEquals(sampleJson, files().readTextOrNull(TransponderDatabaseRepository.DATABASE_FILE))
        // And load now returns the synced copy.
        val loaded = repo().load()
        assertFalse(loaded.isBundled)
        assertEquals(now, loaded.fetchedAtMillis)
        assertEquals(now, repo().status().lastSuccess!!.millis)
    }

    @Test
    fun `the minimum interval blocks a second sync, shared with the keps rule`() {
        downloader.next = ok(sampleJson)
        repo().sync()
        now += 30 * 60 * 1000 // half an hour later
        val result = repo().sync()
        assertTrue(result is TransponderSyncResult.TooSoon)
        assertEquals(1, downloader.requested.size) // no second request
    }

    @Test
    fun `after the interval a sync runs again`() {
        downloader.next = ok(sampleJson)
        repo().sync()
        now += 3 * hour
        repo().sync()
        assertEquals(2, downloader.requested.size)
    }

    @Test
    fun `a network failure keeps the existing database and records the failure`() {
        // Seed a good synced copy first.
        downloader.next = ok(sampleJson)
        repo().sync()
        val storedBefore = files().readTextOrNull(TransponderDatabaseRepository.DATABASE_FILE)
        now += 3 * hour

        downloader.next = { throw IOException("timeout") }
        val result = repo().sync()
        assertTrue(result is TransponderSyncResult.Failed)
        assertTrue((result as TransponderSyncResult.Failed).outcome is DownloadOutcome.Transient)
        // The synced copy is untouched.
        assertEquals(storedBefore, files().readTextOrNull(TransponderDatabaseRepository.DATABASE_FILE))
        assertFalse(repo().load().isBundled)
    }

    @Test
    fun `an HTTP error keeps the existing database`() {
        downloader.next = { DownloadResponse(403, "forbidden") }
        val result = repo().sync()
        assertTrue(result is TransponderSyncResult.Failed)
        // Nothing was stored, so load falls back to bundled.
        assertTrue(repo().load().isBundled)
    }

    @Test
    fun `an invalid body with HTTP 200 keeps the existing database`() {
        downloader.next = ok("<html>maintenance</html>")
        val result = repo().sync()
        assertTrue(result is TransponderSyncResult.Failed)
        assertTrue((result as TransponderSyncResult.Failed).outcome is DownloadOutcome.Permanent)
        assertTrue(repo().load().isBundled)
        assertNull(files().readTextOrNull(TransponderDatabaseRepository.DATABASE_FILE))
    }

    @Test
    fun `an empty array with HTTP 200 is treated as unusable`() {
        downloader.next = ok("[]")
        assertTrue(repo().sync() is TransponderSyncResult.Failed)
        assertTrue(repo().load().isBundled)
    }

    @Test
    fun `a transient failure does not start the interval, so a retry is allowed`() {
        downloader.next = { throw IOException("timeout") }
        assertTrue(repo().sync() is TransponderSyncResult.Failed)
        // Immediately retrying is allowed because the server never answered.
        downloader.next = ok(sampleJson)
        assertTrue(repo().sync() is TransponderSyncResult.Updated)
        assertEquals(2, downloader.requested.size)
    }
}
