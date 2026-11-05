// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.Satellite
import java.time.Instant
import java.util.Date
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A point on the Earth. [longitudeDeg] is east-positive and may be given as -180..180 or as 0..360 (the prediction
 * code reports 0..360).
 */
data class GeoPoint(val latitudeDeg: Double, val longitudeDeg: Double)

/**
 * Maps latitude and longitude onto the flat world map, a plate carree picture with the date line at the left and right
 * edges, longitude 0 in the middle and north at the top. These are the formulas of the original map view.
 */
object MapProjection {

    private fun normalise(longitudeDeg: Double): Double = ((longitudeDeg % 360.0) + 360.0) % 360.0

    /** Horizontal position in a map [mapWidth] units wide. */
    fun x(longitudeDeg: Double, mapWidth: Double): Double {
        val lon = normalise(longitudeDeg)
        // 0..180 is the eastern half (right of the middle), the rest is the western half (left of the middle)
        val shifted = if (lon <= 180.0) lon + 180.0 else lon - 180.0
        return shifted * (mapWidth / 360.0)
    }

    /** Vertical position (0 at the north pole) in a map [mapHeight] units high. */
    fun y(latitudeDeg: Double, mapHeight: Double): Double = (90.0 - latitudeDeg) * (mapHeight / 180.0)

    /**
     * The points as lines drawn on the map, as x,y pairs. A line is broken wherever it would jump across the map
     * (the track crossing the date line), so no line is drawn across the whole picture. As in the original, y is
     * rounded to a whole unit.
     *
     * The original decided "jump" as more than 180 units whatever the map's width, which only suited a map 360 units
     * wide; here a jump is more than half the map's width, the same thing at every size.
     */
    fun polylines(points: List<GeoPoint>, mapWidth: Double, mapHeight: Double): List<FloatArray> {
        val lines = ArrayList<FloatArray>()
        var current = ArrayList<Float>()
        var oldX = 0f
        for ((i, p) in points.withIndex()) {
            val px = x(p.longitudeDeg, mapWidth).toFloat()
            val py = y(p.latitudeDeg, mapHeight).roundToInt().toFloat()
            if (i == 0 || abs(px - oldX) > mapWidth / 2.0) {
                if (current.size >= 2) lines.add(current.toFloatArray())
                current = ArrayList()
            }
            current.add(px)
            current.add(py)
            oldX = px
        }
        if (current.size >= 2) lines.add(current.toFloatArray())
        return lines
    }

    /**
     * Where the ground track of [satellite] goes from [from] for [minutes] minutes, one point every [stepSeconds].
     * Use a ground station of your own for this call: the prediction code writes into it.
     */
    fun groundTrack(
        satellite: Satellite,
        station: GroundStationPosition,
        from: Instant,
        minutes: Int,
        stepSeconds: Int = 30,
    ): List<GeoPoint> {
        val out = ArrayList<GeoPoint>()
        var t = from.toEpochMilli()
        val end = t + minutes * 60_000L
        while (t < end) {
            val pos = satellite.getPosition(station, Date(t))
            out.add(GeoPoint(Math.toDegrees(pos.latitude), Math.toDegrees(pos.longitude)))
            t += stepSeconds * 1000L
        }
        return out
    }

    /** The edge of the area from which the satellite can be seen, from `SatPos.rangeCircle` (rows of lat, lon in degrees). */
    fun footprint(rangeCircle: Array<DoubleArray>): List<GeoPoint> = rangeCircle.map { GeoPoint(it[0], it[1]) }
}
