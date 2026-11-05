// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class FileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = FileStore(File(tmp.root, "data"))

    @Test
    fun writeThenRead() {
        val s = store()
        s.writeTextAtomic("keps.txt", "AO-07\nline1\nline2\n")
        assertEquals("AO-07\nline1\nline2\n", s.readTextOrNull("keps.txt"))
        assertTrue(s.exists("keps.txt"))
    }

    @Test
    fun createsTheDirectoryOnDemand() {
        val s = FileStore(File(tmp.root, "a/b/c"))
        s.writeAtomic("x", byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), s.readBytesOrNull("x"))
    }

    @Test
    fun overwriteReplacesContentsAndLeavesNoTempFile() {
        val s = store()
        s.writeTextAtomic("f", "old")
        s.writeTextAtomic("f", "new")
        assertEquals("new", s.readTextOrNull("f"))
        assertEquals(listOf("f"), File(tmp.root, "data").list()!!.sorted())
    }

    @Test
    fun missingFileReadsAsNull() {
        val s = store()
        assertNull(s.readTextOrNull("nope"))
        assertNull(s.readBytesOrNull("nope"))
        assertNull(s.lastModified("nope"))
        assertFalse(s.exists("nope"))
    }

    @Test
    fun failedWriteKeepsThePreviousFile() {
        val s = store()
        s.writeTextAtomic("keep", "good data")
        // A directory where the temporary file should go makes the write fail part-way.
        File(tmp.root, "data/keep.tmp").mkdirs()
        try {
            s.writeTextAtomic("keep", "bad data")
            fail("expected IOException")
        } catch (expected: IOException) {
            // fine
        }
        assertEquals("good data", s.readTextOrNull("keep"))
    }

    @Test
    fun readingADirectoryGivesNull() {
        val s = store()
        File(tmp.root, "data/adir").mkdirs()
        assertNull(s.readTextOrNull("adir"))
    }

    @Test
    fun deleteRemovesFileAndLeftoverTemp() {
        val s = store()
        s.writeTextAtomic("f", "x")
        File(tmp.root, "data/f.tmp").writeText("leftover")
        assertTrue(s.delete("f"))
        assertFalse(s.exists("f"))
        assertFalse(File(tmp.root, "data/f.tmp").exists())
        assertTrue("deleting a missing file is fine", s.delete("f"))
    }

    @Test
    fun lastModifiedIsReportedForExistingFiles() {
        val s = store()
        s.writeTextAtomic("f", "x")
        assertNotNull(s.lastModified("f"))
    }

    @Test
    fun rejectsNamesThatCouldEscapeTheDirectory() {
        val s = store()
        for (bad in listOf("", ".", "..", "../x", "a/b", "a\\b", "x\u0000y")) {
            try {
                s.writeTextAtomic(bad, "x")
                fail("expected IllegalArgumentException for '$bad'")
            } catch (expected: IllegalArgumentException) {
                // fine
            }
        }
        assertFalse(File(tmp.root, "x").exists())
    }

    @Test
    fun unicodeSurvivesARoundTrip() {
        val s = store()
        val text = "Satellite \u00B0 \u2605 \u00E9"
        s.writeTextAtomic("u", text)
        assertEquals(text, s.readTextOrNull("u"))
    }
}
