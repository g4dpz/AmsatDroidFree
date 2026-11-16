// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.SatelliteFactory
import uk.me.g4dpz.satellite.TLE
import java.io.File
import java.time.Instant

class MapProjectionTest {

    /** The original GroundView.drawTrack, reduced to the sequence of moves and lines it made. */
    private sealed interface Op {
        data class Move(val x: Float, val y: Float) : Op
        data class Line(val x: Float, val y: Float) : Op
    }

    private fun legacyOps(lonLatPairs: List<Pair<Double, Double>>, mapWidth: Int, mapHeight: Int): List<Op> {
        val ops = ArrayList<Op>()
        var oldPathX = 181f
        for ((i, p) in lonLatPairs.withIndex()) {
            var pathX = p.first.toFloat() // longitude in degrees, 0..360
            if (pathX <= 180.0) pathX += 180 else pathX -= 180
            pathX *= (mapWidth / 360.0).toFloat()
            val pathY = Math.round((90.0 - p.second) * (mapHeight / 180.0)).toFloat()
            if (i == 0 || Math.abs(pathX - oldPathX) > 180) ops.add(Op.Move(pathX, pathY)) else ops.add(Op.Line(pathX, pathY))
            oldPathX = pathX
        }
        return ops
    }

    private fun opsOf(lines: List<FloatArray>): List<Op> = lines.flatMap { line ->
        (0 until line.size / 2).map { i -> if (i == 0) Op.Move(line[2 * i], line[2 * i + 1]) else Op.Line(line[2 * i], line[2 * i + 1]) }
    }

    /** Same sequence of moves and lines; x within a hundredth of a pixel (float versus double arithmetic), y exactly. */
    private fun assertSameOps(message: String, expected: List<Op>, actual: List<Op>) {
        assertEquals("$message: number of operations", expected.size, actual.size)
        var worst = 0f
        for ((i, pair) in expected.zip(actual).withIndex()) {
            val (e, a) = pair
            assertEquals("$message: kind of operation $i", e::class, a::class)
            val (ex, ey) = when (e) { is Op.Move -> e.x to e.y; is Op.Line -> e.x to e.y; }
            val (ax, ay) = when (a) { is Op.Move -> a.x to a.y; is Op.Line -> a.x to a.y; }
            assertEquals("$message: y of operation $i", ey, ay, 0f)
            worst = maxOf(worst, Math.abs(ex - ax))
        }
        assertTrue("$message: x differs by up to $worst", worst < 0.01f)
    }

    // ---- single points ----

    @Test
    fun theMiddleOfTheMapIsLongitudeZero() {
        assertEquals(500.0, MapProjection.x(0.0, 1000.0), 1e-9)
        assertEquals(500.0, MapProjection.x(360.0, 1000.0), 1e-9)
    }

    @Test
    fun eastIsRightAndWestIsLeft() {
        assertEquals(750.0, MapProjection.x(90.0, 1000.0), 1e-9)
        assertEquals(250.0, MapProjection.x(-90.0, 1000.0), 1e-9)
        assertEquals(250.0, MapProjection.x(270.0, 1000.0), 1e-9) // the same place, written 0..360
    }

    @Test
    fun theDateLineIsAtAnEdge() {
        assertEquals(1000.0, MapProjection.x(180.0, 1000.0), 1e-9)
        assertEquals(0.0, MapProjection.x(-180.0 + 1e-9, 1000.0) - 0.0, 1e-5)
        assertEquals(0.0, MapProjection.x(180.0000001, 1000.0), 1e-3)
    }

    // ---- centred on another longitude ----

    private val sydney = 151.2

    @Test
    fun theCentreLongitudeIsInTheMiddle() {
        for (centre in listOf(0.0, sydney, -122.4, 180.0, -180.0, 359.9, 45.0)) {
            assertEquals("centre $centre", 500.0, MapProjection.x(centre, 1000.0, centre), 1e-9)
        }
    }

    @Test
    fun aCentreOfZeroIsTheOriginalProjection() {
        for (lon in listOf(-180.0, -179.9, -90.0, 0.0, 90.0, 151.2, 180.0, 270.0, 359.9)) {
            assertEquals("lon $lon", MapProjection.x(lon, 1000.0), MapProjection.x(lon, 1000.0, 0.0), 0.0)
        }
        assertEquals(1000.0, MapProjection.x(180.0, 1000.0, 0.0), 1e-9) // the date line stays on the right edge
    }

    @Test
    fun theMapWrapsRoundWhenCentredOnSydney() {
        // 10 degrees east of Sydney is 10/360 of the width right of the middle, 10 west the same to the left
        assertEquals(500.0 + 1000.0 * 10 / 360, MapProjection.x(sydney + 10, 1000.0, sydney), 1e-9)
        assertEquals(500.0 - 1000.0 * 10 / 360, MapProjection.x(sydney - 10, 1000.0, sydney), 1e-9)
        // New Zealand (175 east) is right of Sydney, Hawaii (-155) is further east still and wraps past the date line
        assertTrue(MapProjection.x(175.0, 1000.0, sydney) > 500.0)
        assertTrue(MapProjection.x(-155.0, 1000.0, sydney) > MapProjection.x(175.0, 1000.0, sydney))
        // the far side of the Earth from Sydney is at the edges: just east of it at the left, just west of it at the right
        val antipode = sydney + 180.0
        assertEquals(0.0, MapProjection.x(antipode + 1, 1000.0, sydney), 3.0)
        assertEquals(1000.0, MapProjection.x(antipode - 1, 1000.0, sydney), 3.0)
    }

    @Test
    fun aCentreWrittenInEitherNotationGivesTheSameMap() {
        assertEquals(MapProjection.x(10.0, 1000.0, -122.4), MapProjection.x(10.0, 1000.0, 237.6), 1e-9)
        assertEquals(MapProjection.x(-170.0, 1000.0, 179.0), MapProjection.x(190.0, 1000.0, -181.0), 1e-9)
    }

    @Test
    fun aLineCrossingTheNewSeamIsBrokenThere() {
        // centred on Sydney the seam is at 331.2 east (28.8 west): a track going east across it must be split
        val points = listOf(GeoPoint(0.0, -40.0), GeoPoint(0.0, -30.0), GeoPoint(0.0, -20.0), GeoPoint(0.0, -10.0))
        assertEquals(2, MapProjection.polylines(points, 1000.0, 500.0, sydney).size)
        // the same track on the original map has no break
        assertEquals(1, MapProjection.polylines(points, 1000.0, 500.0).size)
    }

    @Test
    fun aLineNotCrossingTheSeamIsNotBroken() {
        // the original seam (the date line) is now in the middle of the Pacific side, but this track is nowhere near it
        val points = listOf(GeoPoint(0.0, 120.0), GeoPoint(0.0, 140.0), GeoPoint(0.0, 160.0), GeoPoint(0.0, 175.0))
        assertEquals(1, MapProjection.polylines(points, 1000.0, 500.0, sydney).size)
        // a track across the date line, which broke on the original map, does not break now (it is far from the seam)
        val dateLine = listOf(GeoPoint(0.0, 170.0), GeoPoint(0.0, 179.0), GeoPoint(0.0, -179.0), GeoPoint(0.0, -170.0))
        assertEquals(2, MapProjection.polylines(dateLine, 1000.0, 500.0).size)
        assertEquals(1, MapProjection.polylines(dateLine, 1000.0, 500.0, sydney).size)
    }

    // ---- drawing the picture in pieces ----

    @Test
    fun aCentreOfZeroDrawsTheWholePictureInOnePiece() {
        for (centre in listOf(0.0, 360.0, -360.0)) {
            assertEquals(listOf(PicturePiece(0, 2000, 0, 1000)), MapProjection.picturePieces(centre, 2000, 1000))
        }
    }

    @Test
    fun theTwoPiecesMeetExactlyWithNoGapOrOverlap() {
        for (pictureWidth in listOf(1024, 1999, 2000, 4321)) {
            for (mapWidth in listOf(300, 800, 1000, 1340, 2361)) {
                for (centre in listOf(sydney, -122.4, 180.0, -180.0, 0.1, 359.9, 45.0, -0.1)) {
                    val pieces = MapProjection.picturePieces(centre, pictureWidth, mapWidth)
                    val label = "picture $pictureWidth map $mapWidth centre $centre"
                    // the destinations cover the map exactly, in order
                    assertEquals(label, 0, pieces.first().dstX)
                    pieces.zipWithNext { a, b -> assertEquals(label, a.dstX + a.dstWidth, b.dstX) }
                    assertEquals(label, mapWidth, pieces.last().dstX + pieces.last().dstWidth)
                    // the sources cover the picture once; a sliver narrower than one map pixel may be left out
                    val sliver = Math.ceil(pictureWidth.toDouble() / mapWidth).toInt()
                    val covered = pieces.sumOf { it.srcWidth }
                    assertTrue("$label covered $covered", covered <= pictureWidth && pictureWidth - covered <= sliver)
                    assertTrue(label, pieces.all { it.srcX >= 0 && it.srcX + it.srcWidth <= pictureWidth && it.srcWidth > 0 && it.dstWidth > 0 })
                }
            }
        }
    }

    @Test
    fun thePictureLinesUpWithTheProjectionToHalfAPixel() {
        // a feature at longitude L is at picture column (L + 180) / 360 * pictureWidth; it must land where x() puts it
        val pictureWidth = 2000
        val mapWidth = 1340
        for (centre in listOf(sydney, -122.4, 179.9, -179.9, 45.0)) {
            val pieces = MapProjection.picturePieces(centre, pictureWidth, mapWidth)
            for (lon in listOf(-170.0, -90.0, 0.5, 90.0, 151.2, 170.0)) {
                val column = (lon + 180.0) / 360.0 * pictureWidth
                val piece = pieces.first { column >= it.srcX && column < it.srcX + it.srcWidth }
                val drawnAt = piece.dstX + (column - piece.srcX) * piece.dstWidth / piece.srcWidth
                val projectedAt = MapProjection.x(lon, mapWidth.toDouble(), centre)
                // the picture split is rounded to a picture pixel, so allow a pixel and a half (the stretch is under 1 percent)
                assertEquals("lon $lon centre $centre", projectedAt, drawnAt, 1.5)
            }
        }
    }

    @Test
    fun northIsAtTheTop() {
        assertEquals(0.0, MapProjection.y(90.0, 500.0), 1e-9)
        assertEquals(250.0, MapProjection.y(0.0, 500.0), 1e-9)
        assertEquals(500.0, MapProjection.y(-90.0, 500.0), 1e-9)
    }

    @Test
    fun matchesTheOriginalForLongitudeAsReportedByTheLibrary() {
        val rnd = java.util.Random(7)
        repeat(300) {
            val lon = rnd.nextDouble() * 360.0 // the library reports 0..360
            val lat = (rnd.nextDouble() - 0.5) * 180.0
            val w = 300 + rnd.nextInt(1000)
            var legacyX = lon.toFloat()
            if (legacyX <= 180.0) legacyX += 180 else legacyX -= 180
            legacyX *= (w / 360.0).toFloat()
            assertEquals(legacyX.toDouble(), MapProjection.x(lon, w.toDouble()), 1e-3)
            assertEquals(
                Math.round((90.0 - lat) * (500 / 180.0)).toDouble(),
                Math.round(MapProjection.y(lat, 500.0)).toDouble(), 0.0,
            )
        }
    }

    // ---- lines ----

    @Test
    fun aTrackAcrossTheDateLineIsBrokenInTwo() {
        val points = listOf(GeoPoint(0.0, 170.0), GeoPoint(0.0, 178.0), GeoPoint(0.0, 186.0), GeoPoint(0.0, 194.0))
        val lines = MapProjection.polylines(points, 720.0, 360.0)
        assertEquals(2, lines.size)
        assertEquals(4, lines[0].size) // two points before the line
        assertEquals(4, lines[1].size)
        assertTrue("first piece ends at the right edge region", lines[0][2] > 700f)
        assertTrue("second piece starts at the left edge region", lines[1][0] < 20f)
    }

    @Test
    fun anOrdinaryTrackIsOneLine() {
        val points = (0 until 20).map { GeoPoint(10.0 + it, 30.0 + it * 2.0) }
        val lines = MapProjection.polylines(points, 800.0, 400.0)
        assertEquals(1, lines.size)
        assertEquals(40, lines[0].size)
    }

    @Test
    fun emptyAndSinglePointInputsAreHandled() {
        assertTrue(MapProjection.polylines(emptyList(), 800.0, 400.0).isEmpty())
        val one = MapProjection.polylines(listOf(GeoPoint(1.0, 2.0)), 800.0, 400.0)
        assertEquals(1, one.size)
        assertEquals(2, one[0].size)
    }

    // ---- a real orbit compared with the original drawing code ----

    private val tle: TLE = File("src/main/res/raw/nasabare.txt").inputStream().use { TLE.importSat(it) }.first { it.name == "AO-07" }
    private val start = Instant.ofEpochMilli(1264723200000L)

    private fun realTrack(minutes: Int): List<GeoPoint> =
        MapProjection.groundTrack(SatelliteFactory.createSatellite(tle), GroundStationPosition(52.0, -1.0, 0.0), start, minutes)

    @Test
    fun theRealGroundTrackHasOnePointEveryThirtySeconds() {
        assertEquals(200, realTrack(100).size)
    }

    @Test
    fun drawsTheSameLinesAsTheOriginalCodeAtTheWidthItWasDesignedFor() {
        // The original's "jump" test was 180 units, which matches "half the width" for a map 360 units wide.
        val track = realTrack(300)
        val lines = MapProjection.polylines(track, 360.0, 180.0)
        val legacy = legacyOps(track.map { it.longitudeDeg to it.latitudeDeg }, 360, 180)
        assertSameOps("width 360", legacy, opsOf(lines))
    }

    @Test
    fun drawsTheSameLinesAsTheOriginalAtTypicalScreenWidths() {
        // At other widths the original's fixed 180 unit threshold still separates a date-line jump from an ordinary
        // 30 second step for this orbit, so the result is the same.
        val track = realTrack(300)
        for (w in listOf(640, 800, 1280)) {
            val h = w / 2
            val lines = MapProjection.polylines(track, w.toDouble(), h.toDouble())
            val legacy = legacyOps(track.map { it.longitudeDeg to it.latitudeDeg }, w, h)
            assertSameOps("width $w", legacy, opsOf(lines))
        }
    }

    @Test
    fun aThreeHourTrackCrossesTheDateLineAndIsBroken() {
        val lines = MapProjection.polylines(realTrack(300), 800.0, 400.0)
        assertTrue("a low orbit circles the Earth about three times in 300 minutes: ${lines.size}", lines.size >= 3)
    }

    @Test
    fun footprintRowsBecomePoints() {
        val circle = Array(360) { doubleArrayOf(it / 4.0 - 45.0, it.toDouble()) }
        val pts = MapProjection.footprint(circle)
        assertEquals(360, pts.size)
        assertEquals(GeoPoint(-45.0, 0.0), pts[0])
        assertEquals(GeoPoint(44.75, 359.0), pts[359])
    }
}
