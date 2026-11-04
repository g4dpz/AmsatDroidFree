package uk.me.g4dpz.HamSatDroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.SatelliteFactory
import uk.me.g4dpz.satellite.TLE
import java.io.File
import java.util.Date
import kotlin.math.abs

class KepsParsingTest {

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().use { it.readText() }

    private val sample get() = OmmJsonParser.parse(resource("celestrak_amateur_sample.json"))

    @Test
    fun parsesTheRecordedCelestrakSample() {
        val parsed = sample
        assertEquals(0, parsed.skipped)
        assertEquals(8, parsed.elements.size)
        val ao7 = parsed.elements.first { it.catnum == 7530 }
        assertEquals("OSCAR 7 (AO-7)", ao7.name)
        assertEquals(2026, ao7.epochYear)
        assertEquals(12.53700304, ao7.meanMotion, 0.0)
        assertEquals(0.0012062, ao7.eccentricity, 0.0)
        assertEquals(4.6821e-5, ao7.bstar, 1e-12)
        assertEquals(-3.9e-7, ao7.meanMotionDot, 1e-15)
        assertEquals(999, ao7.elementSetNo)
        assertEquals(37804, ao7.revAtEpoch)
    }

    @Test
    fun epochBecomesYearAndFractionalDayOfYear() {
        // 2026-11-04 is day 308; 00:29:23.112096 is 1763.112096 s = 0.020406389 of a day.
        val (year, day) = OmmJsonParser.parseEpoch("2026-11-04T00:29:23.112096")
        assertEquals(2026, year)
        assertEquals(308.0 + 1763.112096 / 86400.0, day, 1e-12)
        assertEquals(1.0, OmmJsonParser.parseEpoch("2027-01-01T00:00:00").second, 0.0)
        assertEquals(366.0, OmmJsonParser.parseEpoch("2028-12-31T00:00:00").second, 0.0) // leap year
    }

    @Test
    fun sixDigitCatalogueNumbersAreKept() {
        val parsed = sample
        val big = parsed.elements.filter { it.catnum > 99999 }.map { it.catnum }.sorted()
        assertEquals(listOf(100465, 101095, 101098), big)
        // and they can be turned into satellites that predict positions
        val tle = TLE.fromElements(parsed.elements.first { it.catnum == 101098 })
        assertEquals(101098, tle.catnum)
        val sat = SatelliteFactory.createSatellite(tle)
        val pos = sat.getPosition(GroundStationPosition(52.0, -1.0, 0.0), Date(1793000000000L))
        assertTrue(pos.altitude > 100.0 && pos.altitude < 2000.0)
    }

    @Test
    fun anUnnamedObjectGetsItsInternationalDesignatorAsName() {
        assertEquals("2026-195A", sample.elements.first { it.catnum == 100465 }.name)
    }

    @Test
    fun aDeepSpaceCelestrakRecordIsRecognisedAsDeepSpace() {
        val ao10 = TLE.fromElements(sample.elements.first { it.catnum == 14129 })
        assertTrue(ao10.isDeepspace)
        val ao7 = TLE.fromElements(sample.elements.first { it.catnum == 7530 })
        assertTrue(!ao7.isDeepspace)
    }

    @Test
    fun jsonElementsMatchTheSameSatellitesTle() {
        // The real TLE for AO-7 from Celestrak at the same time as the JSON sample.
        val tleText = """
            OSCAR 7 (AO-7)          
            1 07530U 74089B   26308.02040639 -.00000039  00000+0  46821-4 0  9993
            2 07530 101.9884 322.7152 0012062 223.6405 162.0645 12.53700304378044
        """.trimIndent()
        val fromText = TleTextParser.parse(tleText).elements.single()
        val fromJson = sample.elements.first { it.catnum == 7530 }
        assertEquals(fromText.catnum, fromJson.catnum)
        assertEquals(fromText.epochYear, fromJson.epochYear)
        assertEquals(fromText.epochDay, fromJson.epochDay, 1e-8)
        assertEquals(fromText.meanMotion, fromJson.meanMotion, 1e-9)
        assertEquals(fromText.eccentricity, fromJson.eccentricity, 1e-9)
        assertEquals(fromText.inclination, fromJson.inclination, 1e-9)
        assertEquals(fromText.raan, fromJson.raan, 1e-9)
        assertEquals(fromText.argPerigee, fromJson.argPerigee, 1e-9)
        assertEquals(fromText.meanAnomaly, fromJson.meanAnomaly, 1e-9)
        assertEquals(fromText.bstar, fromJson.bstar, 1e-12)
        assertEquals(fromText.meanMotionDot, fromJson.meanMotionDot, 1e-12)
        assertEquals(fromText.meanMotionDdot, fromJson.meanMotionDdot, 1e-12)
        assertEquals(fromText.elementSetNo, fromJson.elementSetNo)
        assertEquals(fromText.revAtEpoch, fromJson.revAtEpoch)

        // The two routes must also predict the same sky position (epoch differs by under a millisecond).
        val station = GroundStationPosition(52.0, -1.0, 0.0)
        val when1 = Date(1793000000000L)
        val a = SatelliteFactory.createSatellite(TLE.fromElements(fromText)).getPosition(station, when1)
        val b = SatelliteFactory.createSatellite(TLE.fromElements(fromJson)).getPosition(station, when1)
        assertEquals(a.azimuth, b.azimuth, 1e-5)
        assertEquals(a.elevation, b.elevation, 1e-5)
        assertEquals(a.range, b.range, 0.01)
    }

    @Test
    fun badRecordsAreSkippedAndCountedNotFatal() {
        val json = """[
          {"OBJECT_NAME":"GOOD","NORAD_CAT_ID":1,"EPOCH":"2026-11-04T00:00:00","MEAN_MOTION":14.0,"ECCENTRICITY":0.001,
           "INCLINATION":98.0,"RA_OF_ASC_NODE":10.0,"ARG_OF_PERICENTER":20.0,"MEAN_ANOMALY":30.0},
          {"OBJECT_NAME":"NO_MEAN_MOTION","NORAD_CAT_ID":2,"EPOCH":"2026-11-04T00:00:00","ECCENTRICITY":0.001,
           "INCLINATION":98.0,"RA_OF_ASC_NODE":10.0,"ARG_OF_PERICENTER":20.0,"MEAN_ANOMALY":30.0},
          {"OBJECT_NAME":"BAD_EPOCH","NORAD_CAT_ID":3,"EPOCH":"yesterday","MEAN_MOTION":14.0,"ECCENTRICITY":0.001,
           "INCLINATION":98.0,"RA_OF_ASC_NODE":10.0,"ARG_OF_PERICENTER":20.0,"MEAN_ANOMALY":30.0},
          {"OBJECT_NAME":"BAD_ECC","NORAD_CAT_ID":4,"EPOCH":"2026-11-04T00:00:00","MEAN_MOTION":14.0,"ECCENTRICITY":1.5,
           "INCLINATION":98.0,"RA_OF_ASC_NODE":10.0,"ARG_OF_PERICENTER":20.0,"MEAN_ANOMALY":30.0},
          {"OBJECT_NAME":"TEXT_NUMBER","NORAD_CAT_ID":5,"EPOCH":"2026-11-04T00:00:00","MEAN_MOTION":"abc","ECCENTRICITY":0.001,
           "INCLINATION":98.0,"RA_OF_ASC_NODE":10.0,"ARG_OF_PERICENTER":20.0,"MEAN_ANOMALY":30.0},
          "not an object",
          {"OBJECT_NAME":"OLD","NORAD_CAT_ID":6,"EPOCH":"1900-01-01T00:00:00","MEAN_MOTION":14.0,"ECCENTRICITY":0.001,
           "INCLINATION":98.0,"RA_OF_ASC_NODE":10.0,"ARG_OF_PERICENTER":20.0,"MEAN_ANOMALY":30.0}
        ]"""
        val parsed = OmmJsonParser.parse(json)
        assertEquals(listOf("GOOD"), parsed.elements.map { it.name })
        assertEquals(6, parsed.skipped)
    }

    @Test
    fun optionalFieldsDefaultToZero() {
        val json = """[{"OBJECT_NAME":"MIN","NORAD_CAT_ID":9,"EPOCH":"2026-11-04T12:00:00Z","MEAN_MOTION":14.0,
          "ECCENTRICITY":0.001,"INCLINATION":98.0,"RA_OF_ASC_NODE":10.0,"ARG_OF_PERICENTER":20.0,"MEAN_ANOMALY":30.0}]"""
        val e = OmmJsonParser.parse(json).elements.single()
        assertEquals(0.0, e.bstar, 0.0)
        assertEquals(0.0, e.meanMotionDot, 0.0)
        assertEquals(0, e.elementSetNo)
        assertEquals(308.5, e.epochDay, 1e-12)
    }

    @Test
    fun emptyArrayIsValidButEmpty() {
        val parsed = OmmJsonParser.parse("[]")
        assertEquals(0, parsed.elements.size)
        assertEquals(0, parsed.skipped)
    }

    @Test
    fun responsesThatAreNotSatelliteListsAreRejected() {
        val bad = listOf(
            "",
            "No GP data found",
            "Invalid query: \"GROUP=noaa&FORMAT=tle\" (GROUP=noaa not found)",
            "GP data has not updated since your last successful\ndownload of GROUP=active at 2026-03-26 08:10:22 UTC.",
            "<!DOCTYPE html><html><head></head></html>",
            "{\"error\":\"nope\"}",
            "[ {broken json"
        )
        for (text in bad) {
            try {
                OmmJsonParser.parse(text)
                fail("expected InvalidKepsException for: $text")
            } catch (expected: InvalidKepsException) {
                // fine
            }
        }
    }

    // ---- TLE text ----

    @Test
    fun tleParserReadsTheBundledKeps() {
        val parsed = TleTextParser.parse(File("src/main/res/raw/nasabare.txt").readText())
        assertEquals(0, parsed.skipped)
        assertEquals(60, parsed.elements.size)
        assertTrue(parsed.elements.any { it.name == "AO-07" && it.catnum == 7530 })
    }

    @Test
    fun tleParserToleratesBlankLinesAndStrayText() {
        val text = """

            some banner line
            AO-07
            1 07530U 74089B   10027.66381990 -.00000027  00000-0  10000-3 0 06918
            2 07530 101.4121 048.9412 0012004 188.8786 171.2068 12.53577472610845

            trailing junk
        """.trimIndent()
        val parsed = TleTextParser.parse(text)
        assertEquals(listOf("AO-07"), parsed.elements.map { it.name })
    }

    @Test
    fun tleParserSkipsARecordThatCannotBeParsed() {
        val text = """
            GOOD
            1 07530U 74089B   10027.66381990 -.00000027  00000-0  10000-3 0 06918
            2 07530 101.4121 048.9412 0012004 188.8786 171.2068 12.53577472610845
            BROKEN
            1 99999U SHORT
            2 99999 SHORT
        """.trimIndent()
        val parsed = TleTextParser.parse(text)
        assertEquals(listOf("GOOD"), parsed.elements.map { it.name })
        assertEquals(1, parsed.skipped)
    }

    @Test
    fun tleParserGivesNothingForCelestrakErrorText() {
        assertEquals(0, TleTextParser.parse("Invalid query: \"GROUP=noaa&FORMAT=tle\" (GROUP=noaa not found)").elements.size)
        assertEquals(0, TleTextParser.parse("").elements.size)
    }

    @Test
    fun fromElementsRejectsInvalidValues() {
        val ok = sample.elements.first()
        for (bad in listOf(
            ok.copy(catnum = 0), ok.copy(epochYear = 1900), ok.copy(epochYear = 2100), ok.copy(epochDay = 0.5),
            ok.copy(epochDay = 400.0), ok.copy(meanMotion = 0.0), ok.copy(meanMotion = Double.NaN),
            ok.copy(eccentricity = 1.0), ok.copy(eccentricity = -0.1), ok.copy(inclination = Double.POSITIVE_INFINITY)
        )) {
            try {
                TLE.fromElements(bad)
                fail("expected IllegalArgumentException for $bad")
            } catch (expected: IllegalArgumentException) {
                // fine
            }
        }
    }

    @Test
    fun differenceInDaysHelperSanity() {
        // guards the assumption used above: 1e-8 day is under a millisecond
        assertTrue(abs(1e-8 * 86400.0) < 0.001)
    }
}
