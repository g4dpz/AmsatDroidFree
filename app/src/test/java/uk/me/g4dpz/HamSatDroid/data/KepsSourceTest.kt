// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class KepsSourceTest {

    @Test
    fun allUrlsAreHttpsWithAHost() {
        for (source in KepsSource.entries) {
            val uri = URI(source.url)
            assertEquals("${source.name} must use https", "https", uri.scheme)
            assertTrue("${source.name} needs a host", !uri.host.isNullOrEmpty())
        }
    }

    @Test
    fun urlsAreUnique() {
        assertEquals(KepsSource.entries.size, KepsSource.entries.map { it.url }.toSet().size)
    }

    @Test
    fun keyRoundTrips() {
        for (source in KepsSource.entries) assertEquals(source, KepsSource.fromKey(source.key))
    }

    @Test
    fun unknownOrMissingKeyGivesDefault() {
        assertEquals(KepsSource.DEFAULT, KepsSource.fromKey(null))
        assertEquals(KepsSource.DEFAULT, KepsSource.fromKey(""))
        assertEquals(KepsSource.DEFAULT, KepsSource.fromKey("NOT_A_SOURCE"))
    }

    @Test
    fun keysMatchThoseStoredByEarlierVersions() {
        // These strings were used as constants in the original app; they must stay stable.
        val legacy = listOf(
            "AMATEUR_AMSAT", "AMATEUR_CELESTRAK", "WEATHER_CELESTRAK",
            "CUBESAT_CELESTRAK", "RESOURCES_CELESTRAK", "NEW_CELESTRAK"
        )
        assertEquals(legacy.toSet(), KepsSource.entries.map { it.key }.toSet())
    }

    @Test
    fun celestrakUrlsNoLongerUseTheRetiredTextFiles() {
        for (source in KepsSource.entries.filter { it.name.endsWith("CELESTRAK") }) {
            assertFalse("${source.name} uses a retired URL", source.url.endsWith(".txt"))
            assertTrue(source.url.contains("gp.php"))
        }
    }

    @Test
    fun celestrakSourcesUseJsonSoSixDigitCatalogueNumbersAreIncluded() {
        for (source in KepsSource.entries.filter { it.name.endsWith("CELESTRAK") }) {
            assertEquals(KepsFormat.OMM_JSON, source.format)
            assertTrue("${source.name} must ask for JSON", source.url.endsWith("FORMAT=JSON"))
            // Celestrak requires upper-case query names and the .org host (the .com host only redirects).
            assertEquals("celestrak.org", URI(source.url).host)
        }
    }

    @Test
    fun defaultIsCelestrakAmateurJson() {
        assertEquals(KepsSource.AMATEUR_CELESTRAK, KepsSource.DEFAULT)
        assertEquals(KepsFormat.OMM_JSON, KepsSource.DEFAULT.format)
    }

    @Test
    fun amsatIsTleText() {
        assertEquals(KepsFormat.TLE_TEXT, KepsSource.AMATEUR_AMSAT.format)
    }

    @Test
    fun minimumDownloadIntervalIsTwoHours() {
        assertEquals(2 * 60 * 60 * 1000L, KepsSource.MIN_DOWNLOAD_INTERVAL_MILLIS)
    }
}
