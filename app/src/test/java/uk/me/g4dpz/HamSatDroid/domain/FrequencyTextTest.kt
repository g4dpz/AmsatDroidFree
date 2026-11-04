package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrequencyTextTest {

    @Test
    fun `parses MHz to whole hertz`() {
        assertEquals(435_120_000L, FrequencyText.megahertzToHz("435.12"))
        assertEquals(145_800_000L, FrequencyText.megahertzToHz("145.8"))
        assertEquals(435_000_001L, FrequencyText.megahertzToHz("435.000001"))
    }

    @Test
    fun `accepts a comma decimal separator`() {
        assertEquals(435_120_000L, FrequencyText.megahertzToHz("435,120"))
    }

    @Test
    fun `rejects nonsense and non-positive values`() {
        assertNull(FrequencyText.megahertzToHz(""))
        assertNull(FrequencyText.megahertzToHz("abc"))
        assertNull(FrequencyText.megahertzToHz("0"))
        assertNull(FrequencyText.megahertzToHz("-1"))
    }

    @Test
    fun `round trips through the editing text`() {
        for (hz in listOf(145_800_000L, 435_120_000L, 29_300_000L, 435_000_001L)) {
            assertEquals(hz, FrequencyText.megahertzToHz(FrequencyText.hzToMegahertzText(hz)))
        }
    }

    @Test
    fun `editing text drops trailing zeros`() {
        assertEquals("145.8", FrequencyText.hzToMegahertzText(145_800_000L))
        assertEquals("435", FrequencyText.hzToMegahertzText(435_000_000L))
    }
}
