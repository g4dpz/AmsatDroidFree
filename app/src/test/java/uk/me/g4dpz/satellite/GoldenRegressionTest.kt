package uk.me.g4dpz.satellite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.utils.IaruLocator
import java.io.File
import java.util.Date
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max

/**
 * Regression test for the orbit-prediction code. It computes a large set of numbers (TLE parsing, satellite
 * positions, pass predictions, locator maths) for every satellite in the bundled keps file and compares them with
 * a golden file captured from the original Java implementation.
 *
 * To regenerate the golden file deliberately: UPDATE_GOLDEN=1 ./gradlew :app:testDebugUnitTest
 */
class GoldenRegressionTest {

    private val kepsFile = File("src/main/res/raw/nasabare.txt")
    private val deepSpaceFile = File("src/test/resources/deepspace.txt")
    private val goldenFile = File("src/test/resources/golden.txt")

    // 2010-01-29 00:00:00 UTC, shortly after the epoch of the bundled keps.
    private val baseTime = 1264723200000L
    private val station = GroundStationPosition(52.0, -1.0, 0.0)

    private fun snapshot(): List<String> {
        val out = ArrayList<String>()
        val tles = kepsFile.inputStream().use { TLE.importSat(it) } + deepSpaceFile.inputStream().use { TLE.importSat(it) }
        out += "tleCount=${tles.size}"

        for (tle in tles) {
            val id = "${tle.catnum}"
            out += "$id.tle name=${tle.name} set=${tle.setnum} year=${tle.year} deep=${tle.isDeepspace} orbit=${tle.orbitnum}"
            out += "$id.tle.d refepoch=${tle.refepoch} incl=${tle.incl} raan=${tle.raan} eccn=${tle.eccn} argper=${tle.argper} " +
                "meanan=${tle.meanan} meanmo=${tle.meanmo} drag=${tle.drag} nddot6=${tle.nddot6} bstar=${tle.bstar} " +
                "eo=${tle.eo} epoch=${tle.epoch} omegao=${tle.omegao} xincl=${tle.xincl} xmo=${tle.xmo} " +
                "xndt2o=${tle.xndt2o} xno=${tle.xno} xnodeo=${tle.xnodeo}"

            try {
                val sat = SatelliteFactory.createSatellite(tle)
                out += "$id.class=${sat.javaClass.simpleName}"
                val offsetsMinutes = listOf(0L, 17L, 95L, 7 * 60L, 31 * 60L, 5 * 24 * 60L, 30 * 24 * 60L, -3 * 24 * 60L)
                for (m in offsetsMinutes) {
                    val pos = sat.getPosition(station, Date(baseTime + m * 60_000L))
                    out += "$id.pos[$m] az=${pos.azimuth} el=${pos.elevation} range=${pos.range} rr=${pos.rangeRate} " +
                        "lat=${pos.latitude} lon=${pos.longitude} alt=${pos.altitude} phase=${pos.phase} " +
                        "theta=${pos.theta} above=${pos.isAboveHorizon} willBeSeen=${sat.willBeSeen(station)}"
                }
                val circle = sat.getPosition(station, Date(baseTime)).rangeCircle
                out += "$id.circle n=${circle.size} first=${circle[0][0]},${circle[0][1]} mid=${circle[180][0]},${circle[180][1]}"
            } catch (e: Exception) {
                out += "$id.sat EXC ${e.javaClass.simpleName}"
            }

            try {
                val predictor = PassPredictor(tle, station)
                val passes = predictor.getPasses(Date(baseTime), 24, true)
                out += "$id.passes n=${passes.size}"
                for ((i, p) in passes.withIndex()) {
                    out += "$id.pass[$i] start=${p.startTime.time} end=${p.endTime.time} tca=${tryTca(p)} " +
                        "pole=${p.polePassed} aos=${p.aosAzimuth} los=${p.losAzimuth} maxEl=${p.maxEl}"
                }
                val next = predictor.nextSatPass(Date(baseTime))
                out += "$id.next start=${next.startTime.time} end=${next.endTime.time} maxEl=${next.maxEl}"
                val track = predictor.getPositions(Date(baseTime), 30, 0, 100)
                out += "$id.track n=${track.size} first=${track.first().latitude},${track.first().longitude} " +
                    "last=${track.last().latitude},${track.last().longitude}"
            } catch (e: Exception) {
                out += "$id.pred EXC ${e.javaClass.simpleName}"
            }
        }

        // Locator maths
        for ((lat, lon) in listOf(51.5 to -0.1, 0.0 to 0.0, -33.9 to 151.2, 43.0 to -79.0, 90.0 to 180.0, -89.9 to -179.9)) {
            val loc = IaruLocator(lat, lon)
            out += "loc[$lat,$lon] grid=${loc.toMaidenhead()} lat=${loc.latitude.toDegrees()} lon=${loc.longitude.toDegrees()}"
        }
        val a = IaruLocator(51.5, -0.1)
        val b = IaruLocator("FN03fh")
        out += "locFN03fh lat=${b.latitude.toDegrees()} lon=${b.longitude.toDegrees()}"
        out += "dist mi=${a.getDistanceMi(b)} km=${a.getDistanceKm(b)} nm=${a.getDistanceNm(b)} bearing=${a.getBearing(b)}"
        return out
    }

    private fun tryTca(p: SatPassTime): Any = try {
        p.tca.time
    } catch (e: Exception) {
        "EXC ${e.javaClass.simpleName}"
    }

    @Test
    fun matchesGoldenOutput() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val actual = snapshot()

        if (System.getenv("UPDATE_GOLDEN") == "1") {
            goldenFile.parentFile!!.mkdirs()
            goldenFile.writeText(actual.joinToString("\n") + "\n")
            return
        }

        val expected = goldenFile.readLines().filter { it.isNotEmpty() }
        assertEquals("line count", expected.size, actual.size)
        for (i in expected.indices) assertLineEquals(expected[i], actual[i])
    }

    @Test
    fun goldenFileIsSubstantial() {
        val lines = goldenFile.readLines()
        assertTrue("golden file looks too small: ${lines.size}", lines.size > 500)
        assertTrue(lines.any { it.contains(".pass[0]") })
    }

    /** Lines are compared token by token; numeric tokens use a tight relative tolerance. */
    private fun assertLineEquals(expected: String, actual: String) {
        val e = expected.split(' ', '=', ',', '[', ']')
        val a = actual.split(' ', '=', ',', '[', ']')
        assertEquals("token count\n  expected: $expected\n  actual:   $actual", e.size, a.size)
        for (i in e.indices) {
            val x = e[i].toDoubleOrNull()
            val y = a[i].toDoubleOrNull()
            if (x != null && y != null) {
                val tol = 1e-9 * max(1.0, max(abs(x), abs(y)))
                assertTrue("expected $x but was $y\n  expected: $expected\n  actual:   $actual", abs(x - y) <= tol || (x.isNaN() && y.isNaN()))
            } else {
                assertEquals("\n  expected: $expected\n  actual:   $actual", e[i], a[i])
            }
        }
    }
}
