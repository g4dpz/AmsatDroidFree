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
 * One piece of the world picture and where it goes on the map, in whole pixels: [srcWidth] columns of the picture from
 * [srcX] are drawn into [dstWidth] columns of the map from [dstX].
 */
data class PicturePiece(val srcX: Int, val srcWidth: Int, val dstX: Int, val dstWidth: Int)

/**
 * Maps latitude and longitude onto the flat world map, a plate carree picture with the date line at the left and right
 * edges, longitude 0 in the middle and north at the top. These are the formulas of the original map view.
 */
object MapProjection {

    private fun normalise(longitudeDeg: Double): Double = ((longitudeDeg % 360.0) + 360.0) % 360.0

    /**
     * Horizontal position in a map [mapWidth] units wide, with [centreDeg] (a longitude, default 0) in the middle. The
     * map wraps round: the longitude [centreDeg] + 180 is at the right edge, and the picture is turned about so that
     * what falls off one edge appears at the other. With the default centre this is the original projection.
     */
    fun x(longitudeDeg: Double, mapWidth: Double, centreDeg: Double = 0.0): Double {
        val lon = normalise(longitudeDeg - centreDeg)
        // 0..180 is the eastern half (right of the middle), the rest is the western half (left of the middle)
        val shifted = if (lon <= 180.0) lon + 180.0 else lon - 180.0
        return shifted * (mapWidth / 360.0)
    }

    /**
     * How to draw the world picture, [pictureWidth] pixels wide with longitude -180 at its left edge, onto a map
     * [mapWidth] pixels wide with [centreDeg] in the middle. The map's left edge is the longitude [centreDeg] - 180,
     * which is some way along the picture, so the picture is drawn in two pieces: from that point to its right edge,
     * then from its left edge up to that point. With a centre of 0 (or 360, -360) that point is the left edge and the
     * result is the one piece the original map drew.
     *
     * The split on the map comes from the exact longitude (so the picture lines up with [x] to within half a pixel);
     * the split in the picture is rounded to a picture pixel. The pieces share the split, so there is no gap or overlap.
     * A piece that would be less than one map pixel wide is left out; the other piece then fills the map.
     */
    fun picturePieces(centreDeg: Double, pictureWidth: Int, mapWidth: Int): List<PicturePiece> {
        require(pictureWidth > 0 && mapWidth > 0) { "sizes must be positive" }
        val fromLeft = normalise(centreDeg) / 360.0 // how far along the picture the map's left edge is
        val srcSplit = (fromLeft * pictureWidth).roundToInt() % pictureWidth
        if (srcSplit == 0) return listOf(PicturePiece(0, pictureWidth, 0, mapWidth))
        val dstSplit = ((1.0 - fromLeft) * mapWidth).roundToInt().coerceIn(0, mapWidth)
        return listOf(
            PicturePiece(srcSplit, pictureWidth - srcSplit, 0, dstSplit),
            PicturePiece(0, srcSplit, dstSplit, mapWidth - dstSplit),
        ).filter { it.srcWidth > 0 && it.dstWidth > 0 }
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
    fun polylines(
        points: List<GeoPoint>,
        mapWidth: Double,
        mapHeight: Double,
        centreDeg: Double = 0.0,
    ): List<FloatArray> {
        val lines = ArrayList<FloatArray>()
        var current = ArrayList<Float>()
        var oldX = 0f
        for ((i, p) in points.withIndex()) {
            val px = x(p.longitudeDeg, mapWidth, centreDeg).toFloat()
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
