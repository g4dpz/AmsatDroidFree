package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.Satellite
import java.time.Instant
import java.util.Date
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A direction in the sky: azimuth and elevation, both in radians as the prediction code reports them. */
data class AzEl(val azimuth: Double, val elevation: Double)

/**
 * Maths for the sky chart: a plan view of the sky with the zenith at the centre, the horizon on the outer circle,
 * north at the top and east to the right. These are the formulas of the original sky view.
 */
object SkyGeometry {

    /** The horizon circle takes this fraction of the shorter side of the chart. */
    const val RADIUS_FRACTION = 0.35f

    private const val HALF_PI = PI / 2.0

    fun chartRadius(width: Float, height: Float): Float = min(width, height) * RADIUS_FRACTION

    /** Distance from the chart centre: 0 at the zenith, [radius] on the horizon. */
    fun distanceFromCentre(elevation: Double, radius: Double): Double = radius * (HALF_PI - elevation) / HALF_PI

    /** Offset to the right of the centre (east is positive). */
    fun azElToX(azimuth: Double, elevation: Double, radius: Double): Float =
        (distanceFromCentre(elevation, radius) * cos(HALF_PI - azimuth)).toFloat()

    /** Offset above the centre (north is positive). Screen y runs downwards, so negate it when drawing. */
    fun azElToY(azimuth: Double, elevation: Double, radius: Double): Float =
        (distanceFromCentre(elevation, radius) * sin(HALF_PI - azimuth)).toFloat()

    /**
     * The track as x,y pairs relative to the chart centre, in screen orientation (y downwards), ready to draw.
     */
    fun toScreenOffsets(track: List<AzEl>, radius: Double): FloatArray {
        val out = FloatArray(track.size * 2)
        for ((i, p) in track.withIndex()) {
            out[2 * i] = azElToX(p.azimuth, p.elevation, radius)
            out[2 * i + 1] = -azElToY(p.azimuth, p.elevation, radius)
        }
        return out
    }

    /**
     * Positions of [satellite] seen from [station] at [stepSeconds] intervals from [start] up to (not including) [end].
     * Like the original this includes the first instant and stops before the last one.
     *
     * Use a ground station of your own for this call: the prediction code writes into it.
     */
    fun computeTrack(
        satellite: Satellite,
        station: GroundStationPosition,
        start: Instant,
        end: Instant,
        stepSeconds: Int = 5,
    ): List<AzEl> {
        val out = ArrayList<AzEl>()
        var t = start.toEpochMilli()
        val endMillis = end.toEpochMilli()
        while (t < endMillis) {
            val pos = satellite.getPosition(station, Date(t))
            out.add(AzEl(pos.azimuth, pos.elevation))
            t += stepSeconds * 1000L
        }
        return out
    }
}
