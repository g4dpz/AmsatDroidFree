// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.satellite.SatPassTime
import java.time.Duration
import java.time.Instant
import java.util.Date

class PassSummaryTest {

    private fun library(pole: String? = "north") = SatPassTime(
        Date(1_800_000_000_000L), Date(1_800_000_600_000L), Date(1_800_000_300_000L), pole, 45, 310, 62.5
    )

    @Test
    fun convertsEveryFieldFromTheLibraryPass() {
        val s = PassSummary.from(library(), 7530, "AO-07")
        assertEquals(7530, s.catnum)
        assertEquals("AO-07", s.satName)
        assertEquals(Instant.ofEpochMilli(1_800_000_000_000L), s.start)
        assertEquals(Instant.ofEpochMilli(1_800_000_600_000L), s.end)
        assertEquals(Instant.ofEpochMilli(1_800_000_300_000L), s.tca)
        assertEquals(PoleCrossing.NORTH, s.pole)
        assertEquals(45, s.aosAzimuthDeg)
        assertEquals(310, s.losAzimuthDeg)
        assertEquals(62.5, s.maxElevationDeg, 0.0)
        assertEquals(Duration.ofMinutes(10), s.duration)
    }

    @Test
    fun roundTripsBackToALibraryPass() {
        val back = PassSummary.from(library("south"), 1, "X").toSatPassTime()
        assertEquals(Date(1_800_000_000_000L), back.startTime)
        assertEquals(Date(1_800_000_600_000L), back.endTime)
        assertEquals(Date(1_800_000_300_000L), back.tca)
        assertEquals("south", back.polePassed)
        assertEquals(45, back.aosAzimuth)
        assertEquals(310, back.losAzimuth)
        assertEquals(62.5, back.maxEl, 0.0)
    }

    @Test
    fun poleCrossingParsesTheLibraryWordsAndToleratesAnythingElse() {
        assertEquals(PoleCrossing.NORTH, PoleCrossing.fromLibrary("north"))
        assertEquals(PoleCrossing.NORTH, PoleCrossing.fromLibrary("NORTH"))
        assertEquals(PoleCrossing.SOUTH, PoleCrossing.fromLibrary("south"))
        assertEquals(PoleCrossing.NONE, PoleCrossing.fromLibrary("none"))
        assertEquals(PoleCrossing.NONE, PoleCrossing.fromLibrary(null))
        assertEquals(PoleCrossing.NONE, PoleCrossing.fromLibrary("sideways"))
        assertEquals("north", PoleCrossing.NORTH.toLibrary())
    }

    @Test
    fun idIdentifiesTheSatelliteAndStartSecond() {
        val a = PassSummary.from(library(), 7530, "AO-07")
        assertEquals("7530@1800000000", a.id)
        assertEquals(a.id, PassSummary.from(library("south"), 7530, "AO-07").id) // same pass, different detail
        assertFalse(a.id == PassSummary.from(library(), 25509, "SO-33").id)
    }

    @Test
    fun inProgressAndOverUseHalfOpenIntervals() {
        val s = PassSummary.from(library(), 1, "X")
        val start = s.start
        val end = s.end
        assertFalse(s.isInProgress(start.minusSeconds(1)))
        assertTrue(s.isInProgress(start))
        assertTrue(s.isInProgress(end.minusSeconds(1)))
        assertFalse(s.isInProgress(end))
        assertFalse(s.isOver(end.minusSeconds(1)))
        assertTrue(s.isOver(end))
    }
}
