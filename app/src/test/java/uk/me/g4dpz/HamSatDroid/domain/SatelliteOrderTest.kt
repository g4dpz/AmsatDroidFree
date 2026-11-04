package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SatelliteOrderTest {

    private fun sorted(vararg names: String) = names.toList().sortedWith(SatelliteOrder.byName { it })

    @Test
    fun `reads the OSCAR tag from the name`() {
        assertEquals("AO" to 7, SatelliteOrder.oscarTag("OSCAR 7 (AO-7)"))
        assertEquals("SO" to 50, SatelliteOrder.oscarTag("SAUDISAT 1C (SO-50)"))
        assertEquals("IO" to 117, SatelliteOrder.oscarTag("GREENCUBE (IO-117)"))
        assertNull(SatelliteOrder.oscarTag("ES'HAIL 2"))
        assertNull(SatelliteOrder.oscarTag("ISS (ZARYA)"))
    }

    @Test
    fun `tags without a trailing O letter are not OSCAR designations`() {
        // These all contain "letters-number" but are not OSCAR numbers
        assertNull(SatelliteOrder.oscarTag("KKS-1 (KISEKI)"))
        assertNull(SatelliteOrder.oscarTag("UWE-3"))
        assertNull(SatelliteOrder.oscarTag("OTP-2"))
        assertNull(SatelliteOrder.oscarTag("RS-44 & BREEZE-KM R/B"))
    }

    @Test
    fun `OSCAR satellites sort by number then letters, across prefixes`() {
        assertEquals(
            listOf("OSCAR 7 (AO-7)", "UOSAT 2 (UO-11)", "JAS-2 (FO-29)", "SAUDISAT 1C (SO-50)", "CUTE-1 (CO-55)"),
            sorted("SAUDISAT 1C (SO-50)", "CUTE-1 (CO-55)", "JAS-2 (FO-29)", "OSCAR 7 (AO-7)", "UOSAT 2 (UO-11)"),
        )
    }

    @Test
    fun `the number is compared numerically, not as text`() {
        assertEquals(listOf("AO-7", "AO-10", "AO-73"), sorted("AO-73", "AO-7", "AO-10"))
    }

    @Test
    fun `the same number is broken by the prefix letters`() {
        assertEquals(listOf("AO-10", "LO-10", "SO-10"), sorted("SO-10", "LO-10", "AO-10"))
    }

    @Test
    fun `OSCAR satellites come before the rest, which are alphabetical`() {
        assertEquals(
            listOf("FUNCUBE-1 (AO-73)", "ES'HAIL 2", "ISS (ZARYA)", "ZARYA TEST"),
            sorted("ISS (ZARYA)", "ZARYA TEST", "ES'HAIL 2", "FUNCUBE-1 (AO-73)"),
        )
    }

    @Test
    fun `names are compared ignoring case and spaces`() {
        val list = sorted("zeta sat", "Alpha Sat", "ALPHASAT")
        assertEquals("Alpha Sat", list[0]) // "Alpha Sat" and "ALPHASAT" are equal; first stays first (stable sort)
        assertEquals("zeta sat", list[2])
    }

    @Test
    fun `a name with two tags uses the lowest number`() {
        assertEquals("AO" to 7, SatelliteOrder.oscarTag("AO-10 / AO-7 combo"))
    }
}
