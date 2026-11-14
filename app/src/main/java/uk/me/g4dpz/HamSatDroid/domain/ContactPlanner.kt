// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.InvalidTleException
import uk.me.g4dpz.satellite.PassPredictor
import uk.me.g4dpz.satellite.SatNotFoundException
import uk.me.g4dpz.satellite.Satellite
import uk.me.g4dpz.satellite.SatelliteFactory
import uk.me.g4dpz.satellite.TLE
import java.time.Instant
import java.util.Date
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** A ground station for the planner: position in degrees, east and north positive. */
data class PlannerStation(val latitudeDeg: Double, val longitudeDeg: Double)

/** Where one station sees the satellite at one moment. [rangeRateKmS] is positive while the satellite moves away. */
data class LookAngle(
    val azimuthDeg: Double,
    val elevationDeg: Double,
    val rangeKm: Double,
    val rangeRateKmS: Double,
)

/** One moment as seen from both stations. */
data class MutualMoment(val time: Instant, val home: LookAngle, val other: LookAngle) {
    /** The smaller of the two elevations: how well the worse-placed station sees the satellite. */
    val lowerElevationDeg: Double get() = min(home.elevationDeg, other.elevationDeg)

    /** The sum of the two ranges, in kilometres. */
    val totalPathKm: Double get() = home.rangeKm + other.rangeKm
}

/** A continuous time when both stations see the satellite at or above the minimum elevation. */
data class MutualWindow(
    val catnum: Int,
    val name: String,
    val start: Instant,
    val end: Instant,
    val best: MutualMoment,
)

/** The planner's answer for one satellite. */
sealed interface PlannerItem {
    val catnum: Int

    /** The mutual windows of a satellite that moves across the sky; empty when there are none. */
    data class Windows(override val catnum: Int, val windows: List<MutualWindow>) : PlannerItem

    /** A satellite that hardly moves in the sky, at or above the minimum elevation at both stations at the start. */
    data class UsableNow(override val catnum: Int, val name: String, val home: LookAngle, val other: LookAngle) : PlannerItem

    /** The satellite could not be worked with (unusable elements). */
    data class Skipped(override val catnum: Int) : PlannerItem
}

/**
 * Finds the times two ground stations can both use a satellite (the contact planner).
 *
 * The pass predictor narrows each station's time down to its passes; the overlaps of the two stations' passes are the
 * only times a mutual window can exist, and only they are stepped through. A window is a run of steps with both
 * elevations at or above the minimum, so every window of [STEP_SECONDS] or more inside an overlap is found.
 *
 * The pass predictor has two limits that the passes screen shares: it looks for AOS in 60-second steps, so a pass
 * shorter than about a minute can be missed, and after each LOS it skips three quarters of an orbit, so on an orbit
 * where passes last longer than a quarter orbit a later pass can be missed. The planner therefore finds every window of
 * 60 seconds or more for satellites whose passes last less than a quarter orbit, which is every amateur satellite in a
 * low orbit. A pass that is already in progress at the start is recovered here, because the predictor's quarter-orbit
 * rewind does not reach back far enough on a slow orbit.
 *
 * Position calculations write into the ground station they are given, so each call makes its own stations and
 * satellite and is safe to run on several threads at once, one satellite on each.
 */
object ContactPlanner {
    const val STEP_SECONDS = 5

    private const val STEP_MS = STEP_SECONDS * 1000L
    private const val MINUTE_MS = 60_000L
    private const val EARTH_RADIUS_KM = 6371.0

    /** The predictor finds AOS and LOS only to 5 seconds; the padding keeps the stepping from starting late or ending early. */
    private const val PASS_PAD_MS = 10_000L

    private const val RECOVERY_STEP_MS = 30_000L
    private const val RECOVERY_CHECKPOINT_EVERY = 20
    private const val STEP_CHECKPOINT_EVERY = 100

    /** Moments within this many degrees of the best lower elevation count as equally good (the path then decides). */
    private const val TIE_ELEVATION_DEG = 0.1

    /**
     * Every mutual window of [tle] between [start] and [start] + [hours], clipped to that interval, with the satellite
     * at or above [minElevationDeg] at both stations. [checkpoint] is called regularly, so a caller can stop the work by
     * throwing from it (see the cancellation notes in the design); the exception is not caught here.
     */
    fun windows(
        tle: TLE,
        home: PlannerStation,
        other: PlannerStation,
        minElevationDeg: Double,
        start: Instant,
        hours: Int,
        checkpoint: () -> Unit = {},
    ): PlannerItem {
        require(hours > 0) { "hours must be positive" }
        return try {
            val homeStation = GroundStationPosition(home.latitudeDeg, home.longitudeDeg, 0.0)
            val otherStation = GroundStationPosition(other.latitudeDeg, other.longitudeDeg, 0.0)
            val satellite = SatelliteFactory.createSatellite(tle)
            val startMs = start.toEpochMilli()
            val endMs = start.plusSeconds(hours * 3600L).toEpochMilli()
            if (SatelliteMotion.isStationary(tle)) {
                // No passes to search for; whether it is usable now is all there is to say.
                val h = look(satellite, homeStation, startMs)
                val o = look(satellite, otherStation, startMs)
                if (h.elevationDeg >= minElevationDeg && o.elevationDeg >= minElevationDeg) {
                    PlannerItem.UsableNow(tle.catnum, tle.name, h, o)
                } else {
                    PlannerItem.Windows(tle.catnum, emptyList())
                }
            } else {
                val homeSpans = spansOf(tle, satellite, home, homeStation, startMs, endMs, hours, checkpoint)
                checkpoint()
                val otherSpans = spansOf(tle, satellite, other, otherStation, startMs, endMs, hours, checkpoint)
                checkpoint()
                val found = ArrayList<MutualWindow>()
                for (candidate in intersect(homeSpans, otherSpans)) {
                    checkpoint()
                    found += stepThrough(tle, satellite, homeStation, otherStation, candidate, minElevationDeg, checkpoint)
                }
                PlannerItem.Windows(tle.catnum, found)
            }
        } catch (e: SatNotFoundException) {
            PlannerItem.Skipped(tle.catnum)
        } catch (e: InvalidTleException) {
            PlannerItem.Skipped(tle.catnum)
        } catch (e: IllegalArgumentException) {
            PlannerItem.Skipped(tle.catnum)
        }
    }

    /**
     * The moments to list for [window], earliest first: its start, every whole UTC minute inside it, its best moment
     * and its end (requirement 6.3). [tle] must be the satellite the window was found for.
     */
    fun timeline(tle: TLE, home: PlannerStation, other: PlannerStation, window: MutualWindow): List<MutualMoment> {
        val satellite = SatelliteFactory.createSatellite(tle)
        val homeStation = GroundStationPosition(home.latitudeDeg, home.longitudeDeg, 0.0)
        val otherStation = GroundStationPosition(other.latitudeDeg, other.longitudeDeg, 0.0)
        val startMs = window.start.toEpochMilli()
        val endMs = window.end.toEpochMilli()
        val times = sortedSetOf(startMs, endMs, window.best.time.toEpochMilli())
        var minute = (startMs / MINUTE_MS + 1) * MINUTE_MS
        while (minute < endMs) {
            times += minute
            minute += MINUTE_MS
        }
        return times.filter { it in startMs..endMs }.map {
            MutualMoment(Instant.ofEpochMilli(it), look(satellite, homeStation, it), look(satellite, otherStation, it))
        }
    }

    /** The great-circle distance between two stations in kilometres, on a sphere of radius 6,371 km. */
    fun greatCircleKm(a: PlannerStation, b: PlannerStation): Double {
        val lat1 = Math.toRadians(a.latitudeDeg)
        val lat2 = Math.toRadians(b.latitudeDeg)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitudeDeg - a.longitudeDeg)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(h)))
    }

    /** The moment of a window that gives the contact its best chance (requirement 7). [moments] must not be empty. */
    internal fun bestOf(moments: List<MutualMoment>): MutualMoment {
        val top = moments.maxOf { it.lowerElevationDeg }
        return moments.filter { it.lowerElevationDeg >= top - TIE_ELEVATION_DEG }.minBy { it.totalPathKm }
    }

    /** A time interval in milliseconds since the epoch, both ends included. */
    private data class Span(val startMs: Long, val endMs: Long)

    /**
     * The times [station] has the satellite above the horizon between [startMs] and [endMs], a little wider than the
     * truth (see [PASS_PAD_MS]). A pass already in progress at the start is added when the predictor missed it.
     * A satellite that never rises at this station has no spans.
     */
    private fun spansOf(
        tle: TLE,
        satellite: Satellite,
        station: PlannerStation,
        position: GroundStationPosition,
        startMs: Long,
        endMs: Long,
        hours: Int,
        checkpoint: () -> Unit,
    ): List<Span> {
        val spans = ArrayList<Span>()
        try {
            val predictor = PassPredictor(tle, GroundStationPosition(station.latitudeDeg, station.longitudeDeg, 0.0))
            // The predictor returns whole passes, including the first one that starts after the window and, as maxEl 0, "none".
            for (pass in predictor.getPasses(Date(startMs), hours, true)) {
                if (pass.maxEl <= 0.0) continue
                val s = pass.startTime.time - PASS_PAD_MS
                val e = pass.endTime.time + PASS_PAD_MS
                if (e >= startMs && s <= endMs) spans += Span(max(s, startMs), min(e, endMs))
            }
        } catch (e: SatNotFoundException) {
            // never rises at this station: nothing here, and the other station's spans then meet nothing
        }
        checkpoint()
        if (look(satellite, position, startMs).elevationDeg > 0.0 && spans.none { it.startMs <= startMs && startMs <= it.endMs }) {
            spans += Span(startMs, losAfter(satellite, position, startMs, endMs, checkpoint))
        }
        return merge(spans)
    }

    /** When the satellite, above the horizon at [startMs], next goes below it, stepping 30 s; [endMs] at the latest. */
    private fun losAfter(satellite: Satellite, position: GroundStationPosition, startMs: Long, endMs: Long, checkpoint: () -> Unit): Long {
        var t = startMs
        var steps = 0
        while (t < endMs) {
            if (steps++ % RECOVERY_CHECKPOINT_EVERY == 0) checkpoint()
            t = min(t + RECOVERY_STEP_MS, endMs)
            if (look(satellite, position, t).elevationDeg <= 0.0) return t
        }
        return endMs
    }

    private fun merge(spans: List<Span>): List<Span> {
        val merged = ArrayList<Span>()
        for (span in spans.sortedBy { it.startMs }) {
            val last = merged.lastOrNull()
            if (last != null && span.startMs <= last.endMs) merged[merged.lastIndex] = Span(last.startMs, max(last.endMs, span.endMs))
            else merged += span
        }
        return merged
    }

    /** The overlaps of two sorted, non-overlapping lists of spans. */
    private fun intersect(a: List<Span>, b: List<Span>): List<Span> {
        val out = ArrayList<Span>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            val s = max(a[i].startMs, b[j].startMs)
            val e = min(a[i].endMs, b[j].endMs)
            if (s <= e) out += Span(s, e)
            if (a[i].endMs < b[j].endMs) i++ else j++
        }
        return out
    }

    /** Steps through [span] every [STEP_SECONDS] and returns the windows (runs with both elevations high enough). */
    private fun stepThrough(
        tle: TLE,
        satellite: Satellite,
        home: GroundStationPosition,
        other: GroundStationPosition,
        span: Span,
        minElevationDeg: Double,
        checkpoint: () -> Unit,
    ): List<MutualWindow> {
        val windows = ArrayList<MutualWindow>()
        var run = ArrayList<MutualMoment>()
        fun close() {
            if (run.isNotEmpty()) windows += MutualWindow(tle.catnum, tle.name, run.first().time, run.last().time, bestOf(run))
            run = ArrayList()
        }
        var t = span.startMs
        var steps = 0
        while (t <= span.endMs) {
            if (steps++ % STEP_CHECKPOINT_EVERY == 0) checkpoint()
            val h = look(satellite, home, t)
            val o = look(satellite, other, t)
            if (h.elevationDeg >= minElevationDeg && o.elevationDeg >= minElevationDeg) {
                run += MutualMoment(Instant.ofEpochMilli(t), h, o)
            } else {
                close()
            }
            t += STEP_MS
        }
        close()
        return windows
    }

    private fun look(satellite: Satellite, station: GroundStationPosition, timeMs: Long): LookAngle {
        val p = satellite.getPosition(station, Date(timeMs))
        return LookAngle(Math.toDegrees(p.azimuth), Math.toDegrees(p.elevation), p.range, p.rangeRate)
    }
}
