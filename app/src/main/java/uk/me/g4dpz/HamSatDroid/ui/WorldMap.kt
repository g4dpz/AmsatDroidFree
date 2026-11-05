// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.domain.GeoPoint
import uk.me.g4dpz.HamSatDroid.domain.MapProjection
import kotlin.math.max
import kotlin.math.roundToInt

/** The width of the picture in pixels, used to scale the satellite and home icons with the map. */
private const val WORLD_PICTURE_WIDTH = 1280f

/**
 * The world map with the satellite's ground track (yellow), the area it can see (red), the home location and the
 * satellite itself. The picture is 2:1, so the map keeps that shape at any width.
 *
 * The track and footprint are drawn over a photograph, so in the light and dark themes they keep fixed colours. In
 * red night the photograph and icons are filtered to dim red, the track is drawn bright and thick, and the footprint
 * bright and dashed (see [ChartColors.RedNight]).
 *
 * Paths are built when the data, the size or the colours change, not every frame.
 */
@Composable
fun WorldMap(
    track: List<GeoPoint>?,
    footprint: List<GeoPoint>?,
    satellite: GeoPoint?,
    home: GeoPoint?,
    modifier: Modifier = Modifier,
) {
    val world = ImageBitmap.imageResource(R.drawable.world)
    val satelliteIcon = ImageBitmap.imageResource(R.drawable.saticon)
    val homeIcon = ImageBitmap.imageResource(R.drawable.home)
    val density = LocalDensity.current.density
    val description = stringResource(R.string.map_description)
    val colors = chartColors()

    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(2f).semantics { contentDescription = description }
            .drawWithCache {
                val w = size.width
                val h = size.height
                val strokeWidth = 2f * density
                val iconScale = max(w / WORLD_PICTURE_WIDTH, density * 0.75f)

                fun pathOf(points: List<GeoPoint>?): Path = Path().apply {
                    if (points != null) {
                        for (line in MapProjection.polylines(points, w.toDouble(), h.toDouble())) {
                            moveTo(line[0], line[1])
                            for (i in 1 until line.size / 2) lineTo(line[2 * i], line[2 * i + 1])
                        }
                    }
                }

                val trackPath = pathOf(track)
                val footprintPath = pathOf(footprint)
                val homeAt = home?.let {
                    Pair(MapProjection.x(it.longitudeDeg, w.toDouble()), MapProjection.y(it.latitudeDeg, h.toDouble()))
                }
                val satelliteAt = satellite?.let {
                    Pair(MapProjection.x(it.longitudeDeg, w.toDouble()), MapProjection.y(it.latitudeDeg, h.toDouble()))
                }
                val trackStroke = Stroke(
                    width = strokeWidth * colors.trackWidthScale, cap = StrokeCap.Round, join = StrokeJoin.Round,
                )
                val footprintStroke = Stroke(
                    width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round,
                    pathEffect = if (colors.footprintDashed) {
                        PathEffect.dashPathEffect(floatArrayOf(6f * density, 4f * density))
                    } else {
                        null
                    },
                )
                val filter = colors.mapImageFilter

                fun DrawScope.icon(image: ImageBitmap, at: Pair<Double, Double>) {
                    val iw = max(1, (image.width * iconScale).roundToInt())
                    val ih = max(1, (image.height * iconScale).roundToInt())
                    drawImage(
                        image,
                        srcSize = IntSize(image.width, image.height),
                        dstOffset = IntOffset((at.first - iw / 2.0).roundToInt(), (at.second - ih / 2.0).roundToInt()),
                        dstSize = IntSize(iw, ih),
                        colorFilter = colors.mapIconFilter,
                        filterQuality = FilterQuality.Medium,
                    )
                }

                onDrawBehind {
                    drawImage(
                        world,
                        srcSize = IntSize(world.width, world.height),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
                        colorFilter = filter,
                        filterQuality = FilterQuality.Medium,
                    )
                    drawPath(trackPath, colors.mapTrack, style = trackStroke)
                    drawPath(footprintPath, colors.mapFootprint, style = footprintStroke)
                    homeAt?.let { icon(homeIcon, it) }
                    satelliteAt?.let { icon(satelliteIcon, it) }
                }
            },
    )
}
