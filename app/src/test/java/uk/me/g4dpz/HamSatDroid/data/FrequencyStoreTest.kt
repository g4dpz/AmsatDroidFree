package uk.me.g4dpz.HamSatDroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FrequencyStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = FrequencyStore(FileStore(File(tmp.root, "data")))

    @Test
    fun `starts empty`() {
        assertTrue(store().all().isEmpty())
    }

    @Test
    fun `adds and reads back, surviving a new store`() {
        val e = FrequencyEntry(25544, "FM voice", 145_800_000L, 437_800_000L)
        store().put(e)
        assertEquals(listOf(e), store().all())
    }

    @Test
    fun `an optional uplink can be absent`() {
        val e = FrequencyEntry(25544, "beacon", 145_825_000L, null)
        store().put(e)
        val read = store().all().single()
        assertNull(read.uplinkHz)
        assertEquals(145_825_000L, read.downlinkHz)
    }

    @Test
    fun `forSatellite filters and sorts by label`() {
        val s = store()
        s.put(FrequencyEntry(1, "SSB", 1L, null))
        s.put(FrequencyEntry(1, "FM", 2L, null))
        s.put(FrequencyEntry(2, "FM", 3L, null))
        assertEquals(listOf("FM", "SSB"), s.forSatellite(1).map { it.label })
        assertEquals(listOf(2), s.forSatellite(2).map { it.catnum })
    }

    @Test
    fun `put with the same satellite and label replaces`() {
        val s = store()
        s.put(FrequencyEntry(1, "FM", 100L, null))
        s.put(FrequencyEntry(1, "FM", 200L, 300L))
        val entries = s.forSatellite(1)
        assertEquals(1, entries.size)
        assertEquals(200L, entries.single().downlinkHz)
        assertEquals(300L, entries.single().uplinkHz)
    }

    @Test
    fun `remove deletes one entry`() {
        val s = store()
        s.put(FrequencyEntry(1, "FM", 1L, null))
        s.put(FrequencyEntry(1, "SSB", 2L, null))
        s.remove(1, "FM")
        assertEquals(listOf("SSB"), s.forSatellite(1).map { it.label })
    }

    @Test
    fun `a corrupt file reads as empty`() {
        val files = FileStore(File(tmp.root, "data"))
        files.writeTextAtomic("frequencies.json", "not json")
        assertTrue(FrequencyStore(files).all().isEmpty())
    }
}
