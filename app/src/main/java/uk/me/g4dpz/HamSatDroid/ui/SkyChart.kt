package uk.me.g4dpz.HamSatDroid.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.domain.AzEl
import uk.me.g4dpz.HamSatDroid.domain.SkyGeometry

/**
 * The sky chart: the horizon circle with N/E/S/W, a 45 degree ring, the satellite's track (with AOS and LOS) and its
 * current position.
 *
 * The track path and the paints are built once (when the track, the size or the colours change), not every frame.
 * [rotationDegrees] is read only when drawing, so a changing value (the compass) redraws without rebuilding anything.
 *
 * @param track the pass as positions in the sky, or null for none
 * @param current where the satellite is now, or null if unknown
 * @param rotationDegrees how far to turn the chart; positive turns the chart clockwise
 */
@Composable
fun SkyChart(
    track: List<AzEl>?,
    current: AzEl?,
    modifier: Modifier = Modifier,
    rotationDegrees: () -> Float = { 0f },
) {
    val colors = chartColors()
    val density = LocalDensity.current.density
    val north = stringResource(R.string.compass_north)
    val east = stringResource(R.string.compass_east)
    val south = stringResource(R.string.compass_south)
    val west = stringResource(R.string.compass_west)
    val aos = stringResource(R.string.sky_label_aos)
    val los = stringResource(R.string.sky_label_los)
    val elevationRing = stringResource(R.string.sky_elevation_ring)
    val description = stringResource(R.string.sky_chart_description)

    Box(
        modifier = modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = description }
            .drawWithCache {
                val radius = SkyGeometry.chartRadius(size.width, size.height)
                val lineWidth = (2 * density + 0.5f).toInt().toFloat()
                val fontSize = (18 * density + 0.5f).toInt().toFloat()

                val offsets = track?.let { SkyGeometry.toScreenOffsets(it, radius.toDouble()) }
                val trackPath = Path().apply {
                    if (offsets != null) {
                        for (i in 0 until offsets.size / 2) {
                            if (i == 0) moveTo(offsets[0], offsets[1]) else lineTo(offsets[2 * i], offsets[2 * i + 1])
                        }
                    }
                }
                val currentOffset = current?.takeIf { it.elevation > 0 }?.let {
                    Offset(
                        SkyGeometry.azElToX(it.azimuth, it.elevation, radius.toDouble()),
                        -SkyGeometry.azElToY(it.azimuth, it.elevation, radius.toDouble()),
                    )
                }

                val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    textAlign = Paint.Align.CENTER
                    textSize = fontSize
                }
                val stroke = Stroke(width = lineWidth)
                val trackStroke = Stroke(width = lineWidth * colors.trackWidthScale)

                onDrawBehind {
                    translate(size.width / 2f, size.height / 2f) {
                        rotate(-rotationDegrees(), pivot = Offset.Zero) {
                            // frame: horizon, cross, and the 45 degree ring
                            drawCircle(colors.frame, radius, Offset.Zero, style = stroke)
                            drawLine(colors.frame, Offset(-radius, 0f), Offset(radius, 0f), lineWidth)
                            drawLine(colors.frame, Offset(0f, -radius), Offset(0f, radius), lineWidth)
                            drawCircle(colors.frame, radius / 2, Offset.Zero, style = stroke)

                            val canvas = drawContext.canvas.nativeCanvas
                            text.color = colors.east.toArgb()
                            canvas.drawText(east, radius + fontSize / 2, fontSize / 2, text)
                            text.color = colors.west.toArgb()
                            canvas.drawText(west, -radius - fontSize / 2, fontSize / 2, text)
                            text.color = colors.elevationLabel.toArgb()
                            canvas.drawText(elevationRing, fontSize, -radius / 2 - fontSize / 2, text)
                            text.color = colors.south.toArgb()
                            canvas.drawText(south, 0f, radius + fontSize, text)
                            text.color = colors.north.toArgb()
                            canvas.drawText(north, 0f, -radius - fontSize / 2, text)

                            // the track, with AOS at its start and LOS at its end
                            if (offsets != null && offsets.size >= 2) {
                                drawPath(trackPath, colors.track, style = trackStroke)
                                text.color = colors.track.toArgb()
                                canvas.drawText(aos, offsets[0], offsets[1], text)
                                canvas.drawText(los, offsets[offsets.size - 2], offsets[offsets.size - 1], text)
                            }

                            // where the satellite is now (only when above the horizon)
                            if (currentOffset != null) drawCircle(colors.satellite, radius / 25f, currentOffset)
                        }
                    }
                }
            },
    )
}
