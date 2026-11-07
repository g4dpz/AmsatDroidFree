// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.me.g4dpz.HamSatDroid.data.FileStore
import java.io.File

class PassAlertStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = PassAlertStore(FileStore(File(tmp.root, "data")))

    private fun alert(catnum: Int = 25544, aos: Long = 2_000_000L, los: Long = 2_600_000L) =
        PassAlert(catnum, "SAT$catnum", aos, los, 30.0, 100, 200)

    @Test
    fun `starts empty and reads back what was added`() {
        val s = store()
        assertTrue(s.all().isEmpty())
        val a = alert()
        s.add(a)
        assertEquals(listOf(a), s.all())
        assertTrue(s.contains(a.id))
    }

    @Test
    fun `survives a new store on the same directory`() {
        val a = alert()
        store().add(a)
        assertEquals(listOf(a), store().all())
    }

    @Test
    fun `adding the same pass again replaces it rather than duplicating`() {
        val s = store()
        assertTrue(s.add(alert(aos = 2_000_000L, los = 2_600_000L)).isEmpty())
        // An identical alert is nothing to cancel: it is not reported as replaced.
        assertTrue(s.add(alert(aos = 2_000_000L, los = 2_600_000L)).isEmpty())
        assertEquals(1, s.all().size)
    }

    @Test
    fun `adding the same pass predicted a few seconds different replaces the stored one and reports it`() {
        val s = store()
        val first = alert(aos = 2_000_000L, los = 2_600_000L)
        val again = alert(aos = 2_004_000L, los = 2_597_000L) // another prediction of the same pass
        assertNotEquals(first.id, again.id)
        s.add(first)
        assertEquals(listOf(first), s.add(again))
        assertEquals(listOf(again), s.all())
    }

    @Test
    fun `find matches by satellite and overlapping time, not by exact AOS`() {
        val s = store()
        val stored = alert(catnum = 25544, aos = 2_000_000L, los = 2_600_000L)
        s.add(stored)
        assertEquals(stored, s.find(25544, 2_004_000L, 2_597_000L))
        assertNull(s.find(7530, 2_004_000L, 2_597_000L)) // another satellite
        assertNull(s.find(25544, 2_600_000L, 3_200_000L)) // the next pass, touching but not overlapping
        assertNull(s.find(25544, 8_000_000L, 8_600_000L))
    }

    @Test
    fun `remove deletes one alert`() {
        val s = store()
        val a = alert(catnum = 1)
        val b = alert(catnum = 2)
        s.add(a); s.add(b)
        s.remove(a.id)
        assertEquals(listOf(b), s.all())
        assertFalse(s.contains(a.id))
    }

    @Test
    fun `pruneEnded removes passes that are over and returns them`() {
        val s = store()
        val over = alert(catnum = 1, aos = 1_000L, los = 2_000L)
        val future = alert(catnum = 2, aos = 5_000_000L, los = 5_600_000L)
        s.add(over); s.add(future)
        val removed = s.pruneEnded(nowMillis = 3_000L)
        assertEquals(listOf(over), removed)
        assertEquals(listOf(future), s.all())
    }

    @Test
    fun `a write that fails throws IOException and leaves nothing behind`() {
        // The "directory" is a plain file, so the store can neither create it nor write into it.
        val blocker = File(tmp.root, "data")
        blocker.writeText("in the way")
        val s = PassAlertStore(FileStore(blocker))
        try {
            s.add(alert())
            org.junit.Assert.fail("expected IOException")
        } catch (expected: java.io.IOException) {
            // callers at a boundary (click handler, receiver) must catch this
        }
        assertTrue(s.all().isEmpty())
    }

    @Test
    fun `a corrupt file reads as empty`() {
        val files = FileStore(File(tmp.root, "data"))
        files.writeTextAtomic("alerts.json", "{ not valid")
        assertTrue(PassAlertStore(files).all().isEmpty())
    }
}
