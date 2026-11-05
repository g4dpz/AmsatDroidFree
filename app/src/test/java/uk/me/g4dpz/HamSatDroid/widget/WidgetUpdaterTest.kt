// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import uk.me.g4dpz.HamSatDroid.domain.PoleCrossing
import uk.me.g4dpz.satellite.TLE
import java.io.File
import java.time.Instant

class WidgetUpdaterTest {

    private val tle = File("src/main/res/raw/nasabare.txt").inputStream().use { TLE.importSat(it) }
        .first { it.name == "AO-07" }

    private val pass = PassSummary(
        catnum = 7530, satName = "AO-07",
        start = Instant.parse("2026-11-04T18:00:00Z"), end = Instant.parse("2026-11-04T18:12:00Z"),
        tca = Instant.parse("2026-11-04T18:06:00Z"), pole = PoleCrossing.NONE,
        aosAzimuthDeg = 114, losAzimuthDeg = 339, maxElevationDeg = 33.1,
    )

    @Test
    fun `no location wins over everything`() {
        assertEquals(WidgetState.NoLocation, WidgetUpdater.decide(null, null, tle, pass, false))
        assertEquals(WidgetState.NoLocation, WidgetUpdater.decide(null, -0.1, tle, pass, false))
    }

    @Test
    fun `no satellite when the tle is missing`() {
        assertEquals(WidgetState.NoSatellite, WidgetUpdater.decide(51.5, -0.1, null, null, false))
    }

    @Test
    fun `error when the prediction failed`() {
        assertEquals(WidgetState.Error, WidgetUpdater.decide(51.5, -0.1, tle, null, predictionFailed = true))
    }

    @Test
    fun `error when no pass was found`() {
        assertEquals(WidgetState.Error, WidgetUpdater.decide(51.5, -0.1, tle, null, predictionFailed = false))
    }

    @Test
    fun `ready with the pass and the keps name`() {
        val state = WidgetUpdater.decide(51.5, -0.1, tle, pass, false)
        assertEquals(WidgetState.Ready("AO-07", pass), state)
    }
}
