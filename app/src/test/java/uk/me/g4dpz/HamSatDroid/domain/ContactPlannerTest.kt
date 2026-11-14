// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.OrbitalElements
import uk.me.g4dpz.satellite.PassPredictor
import uk.me.g4dpz.satellite.SatelliteFactory
import uk.me.g4dpz.satellite.TLE
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.min

/**
 * The contact planner's search. The orbital elements are fixed copies in `src/test/resources` and every start time is
 * fixed, so each result can be reproduced; nothing is downloaded when the tests run (requirement 10.4).
 */
class ContactPlannerTest {
    private val uk = PlannerStation(52.4375, -2.0417) // IO82xk
    private val kansasCity = PlannerStation(39.5, -95.0) // EM29mm
    private val paris = PlannerStation(48.85, 2.35)
    private val moscow = PlannerStation(55.75, 37.6)
    private val sydney = PlannerStation(-33.87, 151.21)

    private fun tle(resource: String): TLE =
        checkNotNull(javaClass.getResourceAsStream("/$resource")) { "missing test resource $resource" }
            .use { TLE.importSat(it) }.first()

    private val rs44 get() = tle("rs44_20261114.tle")
    private val iss get() = tle("iss_20261114.tle")
    private val ao7 get() = tle("ao7_20261114.tle")

    /** QO-100 (Es'hail 2), geostationary at 25.9 E, as in PassPredictorSearchBoundTest. */
    private val qo100 = TLE.fromElements(
        OrbitalElements(
            name = "ES'HAIL 2", catnum = 43700, epochYear = 2026, epochDay = 308.11433122,
            meanMotion = 1.00272722, eccentricity = 0.00016, inclination = 0.0116, raan = 244.1212,
            argPerigee = 23.3346, meanAnomaly = 202.8356, elementSetNo = 999, revAtEpoch = 2908,
        )
    )

    private fun windows(
        tle: TLE, home: PlannerStation, other: PlannerStation, min: Double, start: String, hours: Int,
        checkpoint: () -> Unit = {},
    ): List<MutualWindow> {
        val item = ContactPlanner.windows(tle, home, other, min, Instant.parse(start), hours, checkpoint)
        return (item as PlannerItem.Windows).windows
    }

    private fun seconds(a: Instant, b: Instant): Long = abs(Duration.between(a, b).seconds)

    // ---- the worked example from the requirements --------------------------------------------------------------

    @Test
    fun `RS-44 from the UK to Kansas City has three windows in 72 hours`() {
        val found = windows(rs44, uk, kansasCity, 0.0, "2026-11-14T14:55:24Z", 72)
        assertEquals(found.toString(), 3, found.size)
        // start, end and best moment (UTC) of the example; each within 10 s (the example was worked out on another grid)
        val expected = listOf(
            Triple("2026-11-14T18:01:24Z", "2026-11-14T18:02:34Z", "2026-11-14T18:02:14Z") to 1.0,
            Triple("2026-11-15T16:25:34Z", "2026-11-15T16:30:24Z", "2026-11-15T16:27:34Z") to 3.7,
            Triple("2026-11-16T16:51:39Z", "2026-11-16T16:55:29Z", "2026-11-16T16:53:54Z") to 3.5,
        )
        for ((window, e) in found.zip(expected)) {
            val (times, lower) = e
            assertTrue("start ${window.start} vs ${times.first}", seconds(window.start, Instant.parse(times.first)) <= 10)
            assertTrue("end ${window.end} vs ${times.second}", seconds(window.end, Instant.parse(times.second)) <= 10)
            assertTrue("best ${window.best.time} vs ${times.third}", seconds(window.best.time, Instant.parse(times.third)) <= 15)
            assertEquals(lower, window.best.lowerElevationDeg, 0.3)
        }
        // Sunday's best moment: the UK points west-north-west and Kansas City north-east, both about 4,000 km away
        val sunday = found[1].best
        assertEquals(289.0, sunday.home.azimuthDeg, 3.0)
        assertEquals(54.0, sunday.other.azimuthDeg, 3.0)
        assertEquals(4000.0, sunday.home.rangeKm, 150.0)
        assertEquals(4000.0, sunday.other.rangeKm, 150.0)
    }

    // ---- against a brute-force search that steps every second -----------------------------------------------

    /** The windows from stepping every second: a run of seconds with both elevations at or above [min]. */
    private fun bruteForce(tle: TLE, home: PlannerStation, other: PlannerStation, min: Double, start: String, hours: Int): List<Pair<Instant, Instant>> {
        val satellite = SatelliteFactory.createSatellite(tle)
        val a = GroundStationPosition(home.latitudeDeg, home.longitudeDeg, 0.0)
        val b = GroundStationPosition(other.latitudeDeg, other.longitudeDeg, 0.0)
        val out = ArrayList<Pair<Instant, Instant>>()
        var runStart = -1L
        var last = 0L
        var t = Instant.parse(start).toEpochMilli()
        val end = t + hours * 3_600_000L
        while (t <= end) {
            val ea = satellite.getPosition(a, Date(t)).elevation
            val eb = satellite.getPosition(b, Date(t)).elevation
            if (Math.toDegrees(min(ea, eb)) >= min) {
                if (runStart < 0) runStart = t
                last = t
            } else if (runStart >= 0) {
                out += Instant.ofEpochMilli(runStart) to Instant.ofEpochMilli(last)
                runStart = -1
            }
            t += 1000
        }
        if (runStart >= 0) out += Instant.ofEpochMilli(runStart) to Instant.ofEpochMilli(last)
        return out
    }

    /**
     * Every window of 60 seconds or more found by stepping every second is found by the planner, with both ends within
     * 5 seconds, and the planner reports nothing that the brute-force search does not (requirement 10.1).
     */
    private fun assertAgreesWithBruteForce(label: String, tle: TLE, home: PlannerStation, other: PlannerStation, min: Double, start: String, hours: Int): Int {
        val planner = windows(tle, home, other, min, start, hours)
        val brute = bruteForce(tle, home, other, min, start, hours)
        for ((s, e) in brute) {
            if (Duration.between(s, e).seconds < 60) continue
            assertTrue(
                "$label: brute-force window $s to $e not found in ${planner.map { it.start to it.end }}",
                planner.any { seconds(it.start, s) <= 5 && seconds(it.end, e) <= 5 },
            )
        }
        for (w in planner) {
            assertTrue(
                "$label: planner window ${w.start} to ${w.end} not in brute force ${brute}",
                brute.any { (s, e) -> seconds(w.start, s) <= 5 && seconds(w.end, e) <= 5 },
            )
        }
        return planner.size
    }

    @Test
    fun `ISS between the UK and Paris agrees with a one-second search`() {
        val n = assertAgreesWithBruteForce("ISS UK-Paris", iss, uk, paris, 0.0, "2026-11-14T15:00:00Z", 24)
        assertTrue("fixture: the ISS has several passes a day over both", n >= 4)
        assertAgreesWithBruteForce("ISS UK-Paris 10 deg", iss, uk, paris, 10.0, "2026-11-14T15:00:00Z", 24)
    }

    @Test
    fun `AO-7 between the UK and Kansas City agrees with a one-second search`() {
        val n = assertAgreesWithBruteForce("AO-7 UK-KC", ao7, uk, kansasCity, 0.0, "2026-11-14T15:00:00Z", 48)
        assertTrue("fixture: AO-7 has a few mutual windows in two days", n >= 2)
    }

    @Test
    fun `RS-44 between the UK and Moscow agrees with a one-second search`() {
        val n = assertAgreesWithBruteForce("RS-44 UK-Moscow", rs44, uk, moscow, 0.0, "2026-11-14T15:00:00Z", 24)
        assertTrue("fixture: RS-44 has several mutual windows a day over stations 2,500 km apart", n >= 4)
    }

    @Test
    fun `RS-44 between the UK and Kansas City agrees with a one-second search`() {
        assertAgreesWithBruteForce("RS-44 UK-KC", rs44, uk, kansasCity, 0.0, "2026-11-14T14:55:24Z", 48)
    }

    // ---- the edges of the search ------------------------------------------------------------------------

    @Test
    fun `a window already open at the start begins at the start`() {
        val found = windows(rs44, uk, kansasCity, 0.0, "2026-11-15T16:27:00Z", 1)
        assertEquals(found.toString(), 1, found.size)
        assertEquals(Instant.parse("2026-11-15T16:27:00Z"), found[0].start)
        assertTrue(seconds(found[0].end, Instant.parse("2026-11-15T16:30:24Z")) <= 10)
    }

    @Test
    fun `a pass that runs past the end of the search is cut at the end`() {
        // Sunday's window runs to about 16:30:24; the search ends at 16:27:34
        val found = windows(rs44, uk, kansasCity, 0.0, "2026-11-15T15:27:34Z", 1)
        assertEquals(found.toString(), 1, found.size)
        val end = Instant.parse("2026-11-15T16:27:34Z")
        assertTrue("window ends ${found[0].end} after the search end", !found[0].end.isAfter(end))
        assertTrue("window ends ${found[0].end}, well before the search end", seconds(found[0].end, end) <= 5)
        assertTrue("best moment ${found[0].best.time} is after the search end", !found[0].best.time.isAfter(end))
        assertTrue(seconds(found[0].start, Instant.parse("2026-11-15T16:25:34Z")) <= 10)
    }

    @Test
    fun `a window of about ten seconds inside a longer overlap is found`() {
        val whole = windows(rs44, uk, kansasCity, 0.0, "2026-11-15T15:00:00Z", 2)
        assertEquals(whole.toString(), 1, whole.size)
        val peak = whole[0].best.lowerElevationDeg
        // just below the peak elevation, only a few seconds of the overlap are high enough
        val short = windows(rs44, uk, kansasCity, peak - 0.004, "2026-11-15T15:00:00Z", 2)
        assertEquals(short.toString(), 1, short.size)
        val length = Duration.between(short[0].start, short[0].end).seconds
        assertTrue("expected a few seconds, got $length", length in 0..20)
        val brute = bruteForce(rs44, uk, kansasCity, peak - 0.004, "2026-11-15T15:00:00Z", 2)
        assertTrue("one-second search: $brute", brute.any { (s, e) -> !e.isBefore(short[0].start) && !s.isAfter(short[0].end) })
    }

    @Test
    fun `a pass already in progress for more than a quarter orbit is recovered`() {
        // A Molniya-type orbit (period 12 hours, apogee over the northern hemisphere): the UK and Moscow both see it for
        // about 11 hours at a time. Four hours into such a pass is further back than the predictor's quarter-orbit rewind.
        val molniya = TLE.fromElements(
            OrbitalElements(
                name = "MOLNIYA TEST", catnum = 99999, epochYear = 2026, epochDay = 318.0, meanMotion = 2.00623,
                eccentricity = 0.72, inclination = 63.4, raan = 0.0, argPerigee = 270.0, meanAnomaly = 180.0,
                elementSetNo = 1, revAtEpoch = 100,
            )
        )
        val start = "2026-11-14T22:27:00Z" // four hours into a pass that began at about 18:27
        val firstPassFromPredictor = PassPredictor(molniya, GroundStationPosition(uk.latitudeDeg, uk.longitudeDeg, 0.0))
            .getPasses(Date.from(Instant.parse(start)), 12, true).filter { it.maxEl > 0.0 }
        assertTrue(
            "fixture: the predictor alone does not return the pass in progress ($firstPassFromPredictor)",
            firstPassFromPredictor.none { !it.startTime.toInstant().isAfter(Instant.parse(start)) && !it.endTime.toInstant().isBefore(Instant.parse(start)) },
        )
        val found = windows(molniya, uk, moscow, 0.0, start, 12)
        assertTrue("a window starts at the search start: ${found.map { it.start to it.end }}", found.isNotEmpty() && found[0].start == Instant.parse(start))
        assertTrue("the window runs for hours: ${found[0].end}", Duration.between(found[0].start, found[0].end).toHours() >= 5)
        // The first window agrees with the one-second search. The next pass, 2.6 hours after this one sets, is not
        // asserted: the predictor skips three quarters of an orbit after each pass, which on a 12-hour orbit jumps over
        // it. That is the second limit written in the design ("What the search guarantees"), which promises every
        // window for satellites whose passes last under a quarter orbit; this orbit's pass lasts about 11 hours.
        val brute = bruteForce(molniya, uk, moscow, 0.0, start, 12).first()
        assertTrue("start ${found[0].start} vs ${brute.first}", seconds(found[0].start, brute.first) <= 5)
        assertTrue("end ${found[0].end} vs ${brute.second}", seconds(found[0].end, brute.second) <= 5)
    }

    // ---- the minimum elevation --------------------------------------------------------------------------

    @Test
    fun `a higher minimum elevation shortens a window and one above its peak removes it`() {
        val start = "2026-11-15T15:00:00Z"
        val zero = windows(rs44, uk, kansasCity, 0.0, start, 2).single()
        val three = windows(rs44, uk, kansasCity, 3.0, start, 2).single()
        assertTrue("shorter at the start", three.start.isAfter(zero.start))
        assertTrue("shorter at the end", three.end.isBefore(zero.end))
        assertTrue("both ends still at 3 degrees or more", three.best.lowerElevationDeg >= 3.0)
        val brute = bruteForce(rs44, uk, kansasCity, 3.0, start, 2).single()
        assertTrue(seconds(three.start, brute.first) <= 5 && seconds(three.end, brute.second) <= 5)
        assertEquals(emptyList<MutualWindow>(), windows(rs44, uk, kansasCity, zero.best.lowerElevationDeg + 0.5, start, 2))
    }

    // ---- the cases without a window -----------------------------------------------------------------------

    @Test
    fun `stations too far apart have no windows`() {
        // the UK and Sydney are about 17,000 km apart; the footprint of RS-44 is under 4,000 km in radius
        assertEquals(emptyList<MutualWindow>(), windows(rs44, uk, sydney, 0.0, "2026-11-14T15:00:00Z", 48))
    }

    @Test
    fun `the same station twice gives that station's own passes`() {
        val start = "2026-11-14T15:00:00Z"
        val planner = windows(rs44, uk, uk, 0.0, start, 24)
        val passes = PassPredictor(rs44, GroundStationPosition(uk.latitudeDeg, uk.longitudeDeg, 0.0))
            .getPasses(Date.from(Instant.parse(start)), 24, true)
            .filter { it.maxEl > 0.0 && it.startTime.toInstant().isBefore(Instant.parse(start).plusSeconds(24 * 3600)) && it.endTime.toInstant().isAfter(Instant.parse(start)) }
        assertEquals(planner.map { it.start }.toString() + " vs " + passes.map { it.startTime }, passes.size, planner.size)
        for ((window, pass) in planner.zip(passes)) {
            assertTrue("start ${window.start} vs ${pass.startTime}", seconds(window.start, pass.startTime.toInstant()) <= 10)
            assertTrue("end ${window.end} vs ${pass.endTime}", seconds(window.end, pass.endTime.toInstant()) <= 10)
            // the predictor and the planner sample the peak at different times, so they differ a little
            assertEquals(pass.maxEl, window.best.lowerElevationDeg, 2.0)
        }
    }

    @Test
    fun `a geostationary satellite is usable now from both stations or not at all`() {
        val start = Instant.parse("2026-11-04T12:00:00Z")
        val london = PlannerStation(51.5, -0.1)
        val both = ContactPlanner.windows(qo100, london, paris, 0.0, start, 24)
        assertTrue("both see QO-100: $both", both is PlannerItem.UsableNow)
        both as PlannerItem.UsableNow
        assertTrue(both.home.elevationDeg > 10.0 && both.other.elevationDeg > 10.0)
        val hawaii = PlannerStation(21.3, -157.8)
        assertEquals(PlannerItem.Windows(43700, emptyList()), ContactPlanner.windows(qo100, london, hawaii, 0.0, start, 24))
        // a minimum elevation above what one station sees also means not usable
        assertEquals(PlannerItem.Windows(43700, emptyList()), ContactPlanner.windows(qo100, london, paris, 89.0, start, 24))
    }

    // ---- the best moment ---------------------------------------------------------------------------------

    private fun moment(second: Long, homeEl: Double, otherEl: Double, homeRange: Double, otherRange: Double) = MutualMoment(
        Instant.ofEpochSecond(second),
        LookAngle(10.0, homeEl, homeRange, 0.0),
        LookAngle(20.0, otherEl, otherRange, 0.0),
    )

    @Test
    fun `the best moment has the highest lower elevation`() {
        val a = moment(0, 5.0, 9.0, 4000.0, 4000.0) // lower 5.0
        val b = moment(5, 6.0, 4.0, 3000.0, 3000.0) // lower 4.0 although a station is higher and the path shorter
        assertEquals(a, ContactPlanner.bestOf(listOf(b, a)))
        assertEquals(5.0, a.lowerElevationDeg, 0.0)
        assertEquals(8000.0, a.totalPathKm, 0.0)
    }

    @Test
    fun `moments within 0·1 degree share the best and the shorter path decides`() {
        val a = moment(0, 5.00, 9.0, 4100.0, 4100.0)
        val b = moment(5, 4.95, 9.0, 3900.0, 3900.0)
        val c = moment(10, 4.70, 9.0, 3000.0, 3000.0) // too far below the best to count
        assertEquals(b, ContactPlanner.bestOf(listOf(a, b, c)))
    }

    @Test
    fun `the best moment is inside its window and its angles equal the satellite's position`() {
        val w = windows(rs44, uk, kansasCity, 0.0, "2026-11-15T15:00:00Z", 2).single()
        assertTrue(!w.best.time.isBefore(w.start) && !w.best.time.isAfter(w.end))
        val satellite = SatelliteFactory.createSatellite(rs44)
        val h = satellite.getPosition(GroundStationPosition(uk.latitudeDeg, uk.longitudeDeg, 0.0), Date.from(w.best.time))
        val o = satellite.getPosition(GroundStationPosition(kansasCity.latitudeDeg, kansasCity.longitudeDeg, 0.0), Date.from(w.best.time))
        assertEquals(Math.toDegrees(h.azimuth), w.best.home.azimuthDeg, 1e-9)
        assertEquals(Math.toDegrees(h.elevation), w.best.home.elevationDeg, 1e-9)
        assertEquals(h.range, w.best.home.rangeKm, 1e-9)
        assertEquals(h.rangeRate, w.best.home.rangeRateKmS, 1e-12)
        assertEquals(Math.toDegrees(o.azimuth), w.best.other.azimuthDeg, 1e-9)
        assertEquals(Math.toDegrees(o.elevation), w.best.other.elevationDeg, 1e-9)
        assertEquals(o.range, w.best.other.rangeKm, 1e-9)
    }

    // ---- the timeline and the distance ---------------------------------------------------------------------

    @Test
    fun `the timeline has the start, every whole minute, the best moment and the end`() {
        val w = windows(rs44, uk, kansasCity, 0.0, "2026-11-15T15:00:00Z", 2).single() // about 16:25:34 to 16:30:24
        val rows = ContactPlanner.timeline(rs44, uk, kansasCity, w)
        assertEquals(w.start, rows.first().time)
        assertEquals(w.end, rows.last().time)
        assertTrue("best moment ${w.best.time} in the timeline", rows.any { it.time == w.best.time })
        assertEquals("sorted and without repeats", rows.map { it.time }.distinct().sorted(), rows.map { it.time })
        // the whole minutes 16:26, 16:27, 16:28, 16:29 and 16:30 are inside this window
        for (m in listOf("16:26", "16:27", "16:28", "16:29", "16:30")) {
            assertTrue("minute $m", rows.any { it.time == Instant.parse("2026-11-15T${m}:00Z") })
        }
        // no row more than a minute after the one before, apart from the start and end rows next to a whole minute
        for ((a, b) in rows.zipWithNext()) assertTrue("gap ${a.time} to ${b.time}", Duration.between(a.time, b.time).seconds <= 60)
        // the best moment's row equals the window's own best moment
        assertEquals(w.best, rows.first { it.time == w.best.time })
        // both stations above the horizon throughout
        assertTrue(rows.all { it.home.elevationDeg >= 0.0 && it.other.elevationDeg >= 0.0 })
    }

    @Test
    fun `the timeline of a window shorter than a minute has just its own moments`() {
        val peak = windows(rs44, uk, kansasCity, 0.0, "2026-11-15T15:00:00Z", 2).single().best.lowerElevationDeg
        val w = windows(rs44, uk, kansasCity, peak - 0.004, "2026-11-15T15:00:00Z", 2).single()
        val rows = ContactPlanner.timeline(rs44, uk, kansasCity, w)
        assertEquals(w.start, rows.first().time)
        assertEquals(w.end, rows.last().time)
        assertTrue("rows ${rows.map { it.time }}", rows.size in 1..4)
    }

    @Test
    fun `the great-circle distance between stations`() {
        assertEquals(6818.0, ContactPlanner.greatCircleKm(uk, kansasCity), 5.0) // the figure in the requirements
        assertEquals(343.0, ContactPlanner.greatCircleKm(PlannerStation(51.5, -0.12), PlannerStation(48.86, 2.35)), 5.0) // London to Paris
        assertEquals(0.0, ContactPlanner.greatCircleKm(uk, uk), 1e-9)
        assertEquals(Math.PI * 6371.0, ContactPlanner.greatCircleKm(PlannerStation(10.0, 20.0), PlannerStation(-10.0, -160.0)), 1e-3) // opposite points
        assertEquals(ContactPlanner.greatCircleKm(uk, kansasCity), ContactPlanner.greatCircleKm(kansasCity, uk), 1e-9)
        assertEquals(ContactPlanner.greatCircleKm(PlannerStation(0.0, 179.0), PlannerStation(0.0, -179.0)), 222.4, 0.5) // across the date line
    }

    // ---- stopping the work -------------------------------------------------------------------------------

    @Test
    fun `the checkpoint is called during the search`() {
        var calls = 0
        windows(rs44, uk, kansasCity, 0.0, "2026-11-14T14:55:24Z", 48) { calls++ }
        assertTrue("checkpoint called $calls times", calls >= 5)
    }

    @Test
    fun `an exception from the checkpoint stops the search and is not turned into Skipped`() {
        var calls = 0
        assertThrows(CancellationException::class.java) {
            ContactPlanner.windows(rs44, uk, kansasCity, 0.0, Instant.parse("2026-11-14T14:55:24Z"), 48) {
                if (++calls == 3) throw CancellationException("stop")
            }
        }
        assertEquals(3, calls)
    }

    @Test
    fun `a search of no hours is a mistake`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContactPlanner.windows(rs44, uk, kansasCity, 0.0, Instant.parse("2026-11-14T14:55:24Z"), 0)
        }
        assertNotNull(rs44)
    }
}
