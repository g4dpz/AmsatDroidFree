package uk.me.g4dpz.HamSatDroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TransponderDatabaseTest {

    private fun sample(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }

    private val db = TransponderDatabase.parse(sample("satellite_database_sample.json"))

    @Test
    fun `parses the satellites with modes and skips those without`() {
        // AO-07, ISS and RS-44 have modes; "No modes sat" and "Missing norad" are skipped.
        assertEquals(setOf(7530, 25544, 44909), db.satellites.map { it.noradId }.toSet())
    }

    @Test
    fun `looks modes up by NORAD id`() {
        assertEquals(2, db.modesForNoradId(7530).size)
        assertTrue(db.modesForNoradId(12345).isEmpty())
    }

    @Test
    fun `converts kilohertz to whole hertz, including decimals`() {
        val modeB = db.modesForNoradId(7530).first { it.type == "Mode B" }
        assertEquals(145_950_000L, modeB.downlinkHz)
        assertEquals(432_146_000L, modeB.uplinkHz)
        val ssb = db.modesForNoradId(44909).single()
        assertEquals(435_850_450L, ssb.downlinkHz) // 435850.45 kHz
        assertEquals(145_952_650L, ssb.uplinkHz)
    }

    @Test
    fun `a zero uplink is a beacon with no uplink`() {
        val beacon = db.modesForNoradId(7530).first { it.type == "CW Beacon" }
        assertNull(beacon.uplinkHz)
        assertTrue(beacon.isBeaconOnly)
    }

    @Test
    fun `reads modulation modes, doppler direction and ctcss`() {
        val modeB = db.modesForNoradId(7530).first { it.type == "Mode B" }
        assertEquals("USB", modeB.downlinkMode)
        assertEquals("LSB", modeB.uplinkMode)
        assertEquals(DopplerDirection.REV, modeB.doppler)
        assertNull(modeB.ctcssHz)

        val iss = db.modesForNoradId(25544).single()
        assertEquals(DopplerDirection.NOR, iss.doppler)
        assertEquals(67.0, iss.ctcssHz!!, 0.001)
    }

    @Test
    fun `ignores unknown fields such as sstv_modes`() {
        // ISS carries an sstv_modes array the app does not model; it must still parse.
        assertNotNull(db.modesForNoradId(25544).single())
    }

    @Test
    fun `garbage and empty input yield an empty database, never a crash`() {
        assertTrue(TransponderDatabase.parse("not json").isEmpty)
        assertTrue(TransponderDatabase.parse("{}").isEmpty) // an object, not the expected array
        assertTrue(TransponderDatabase.parse("[]").isEmpty)
        assertFalse(db.isEmpty)
    }

    @Test
    fun `the bundled database parses to many satellites`() {
        val bundled = File("src/main/res/raw/satellite_database.json").readText()
        val parsed = TransponderDatabase.parse(bundled)
        assertTrue("bundled parsed ${parsed.satellites.size}", parsed.satellites.size > 40)
    }
}
