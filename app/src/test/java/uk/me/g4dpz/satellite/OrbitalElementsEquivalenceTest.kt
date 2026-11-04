package uk.me.g4dpz.satellite

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Date

/**
 * Proves that building a [TLE] from [OrbitalElements] gives exactly the same prediction inputs as parsing the TLE text
 * it came from, for every satellite in the bundled keps and the deep-space test set.
 */
class OrbitalElementsEquivalenceTest {

    private val station = GroundStationPosition(52.0, -1.0, 0.0)
    private val base = 1264723200000L // 2010-01-29 00:00 UTC, close to the epochs of the bundled keps

    private fun tles(): List<TLE> =
        File("src/main/res/raw/nasabare.txt").inputStream().use { TLE.importSat(it) } +
            File("src/test/resources/deepspace.txt").inputStream().use { TLE.importSat(it) }

    private fun assertSameFields(a: TLE, b: TLE) {
        val id = "${a.catnum} ${a.name}"
        assertEquals(id, a.catnum, b.catnum)
        assertEquals(id, a.name, b.name)
        assertEquals(id, a.setnum, b.setnum)
        assertEquals(id, a.year, b.year)
        assertEquals(id, a.orbitnum, b.orbitnum)
        assertEquals(id, a.isDeepspace, b.isDeepspace)
        assertEquals(id, a.refepoch, b.refepoch, 0.0)
        assertEquals(id, a.incl, b.incl, 0.0)
        assertEquals(id, a.raan, b.raan, 0.0)
        assertEquals(id, a.eccn, b.eccn, 0.0)
        assertEquals(id, a.argper, b.argper, 0.0)
        assertEquals(id, a.meanan, b.meanan, 0.0)
        assertEquals(id, a.meanmo, b.meanmo, 0.0)
        assertEquals(id, a.drag, b.drag, 0.0)
        assertEquals(id, a.nddot6, b.nddot6, 0.0)
        assertEquals(id, a.bstar, b.bstar, 0.0)
        // values derived for the prediction code
        assertEquals(id, a.epoch, b.epoch, 0.0)
        assertEquals(id, a.xndt2o, b.xndt2o, 0.0)
        assertEquals(id, a.xincl, b.xincl, 0.0)
        assertEquals(id, a.xnodeo, b.xnodeo, 0.0)
        assertEquals(id, a.eo, b.eo, 0.0)
        assertEquals(id, a.omegao, b.omegao, 0.0)
        assertEquals(id, a.xmo, b.xmo, 0.0)
        assertEquals(id, a.xno, b.xno, 0.0)
    }

    @Test
    fun everyBundledSatelliteRoundTripsExactly() {
        val all = tles()
        assertEquals(66, all.size)
        for (original in all) {
            val rebuilt = TLE.fromElements(OrbitalElements.from(original))
            assertSameFields(original, rebuilt)
        }
    }

    @Test
    fun rebuiltSatellitesPredictIdenticalPositions() {
        for (original in tles()) {
            val rebuilt = TLE.fromElements(OrbitalElements.from(original))
            val s1 = SatelliteFactory.createSatellite(original)
            val s2 = SatelliteFactory.createSatellite(rebuilt)
            for (minutes in listOf(0L, 95L, 31 * 60L, 5 * 24 * 60L)) {
                val t = Date(base + minutes * 60_000L)
                val p1 = s1.getPosition(station, t)
                val p2 = s2.getPosition(station, t)
                val id = "${original.catnum} +${minutes}min"
                assertEquals(id, p1.azimuth, p2.azimuth, 0.0)
                assertEquals(id, p1.elevation, p2.elevation, 0.0)
                assertEquals(id, p1.range, p2.range, 0.0)
                assertEquals(id, p1.latitude, p2.latitude, 0.0)
                assertEquals(id, p1.longitude, p2.longitude, 0.0)
            }
        }
    }

    @Test
    fun aSixDigitCatalogueNumberSurvives() {
        val e = OrbitalElements.from(tles().first()).copy(catnum = 987654321, name = "NINE DIGITS")
        val tle = TLE.fromElements(e)
        assertEquals(987654321, tle.catnum)
        assertEquals("NINE DIGITS", tle.name)
    }

    @Test
    fun twoDigitYearMapsBackToTheRightCentury() {
        val e = OrbitalElements.from(tles().first())
        assertEquals(2010, e.epochYear)
        assertEquals(1999, TLE.fromElements(e.copy(epochYear = 1999)).let { OrbitalElements.from(it).epochYear })
        assertEquals(2056, TLE.fromElements(e.copy(epochYear = 2056)).let { OrbitalElements.from(it).epochYear })
        assertEquals(1957, TLE.fromElements(e.copy(epochYear = 1957)).let { OrbitalElements.from(it).epochYear })
    }
}
