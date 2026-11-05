// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import uk.me.g4dpz.HamSatDroid.night.NightPalette

/**
 * The colours of the sky chart and the map. They keep their meaning (red for north and the satellite, yellow-ish
 * for the track, and so on) but are darker on a light background so they stay readable.
 *
 * In red night every colour is a shade of red, so the track, the footprint and the frame are told apart by
 * brightness, line width ([trackWidthScale]) and line style ([footprintDashed]) instead, and the map photograph and
 * icons are filtered to red ([mapImageFilter]).
 */
data class ChartColors(
    val frame: Color,
    val track: Color,
    val satellite: Color,
    val footprint: Color,
    val north: Color,
    val east: Color,
    val south: Color,
    val west: Color,
    val elevationLabel: Color,
    val mapText: Color,
    /** The ground track drawn over the map photograph. */
    val mapTrack: Color = Color(0xFFFFEB3B),
    /** The visibility circle drawn over the map photograph. */
    val mapFootprint: Color = Color(0xFFFF1744),
    /** Applied to the map photograph; null draws it as it is. */
    val mapImageFilter: ColorFilter? = null,
    /** Applied to the satellite and home icons on the map; null draws them as they are. */
    val mapIconFilter: ColorFilter? = null,
    /** How much thicker than the frame the tracks are drawn. */
    val trackWidthScale: Float = 1f,
    /** Whether the map footprint is dashed (so it differs from the track by more than colour). */
    val footprintDashed: Boolean = false,
) {
    companion object {
        val Dark = ChartColors(
            frame = Color(0xFFCCCCCC), track = Color(0xFFFFEB3B), satellite = Color(0xFFFF5252),
            footprint = Color(0xFFFF5252), north = Color(0xFFFF6659), east = Color(0xFF4DD0E1),
            south = Color(0xFF8AB4F8), west = Color(0xFF4DD0E1), elevationLabel = Color(0xFFCCCCCC),
            mapText = Color.Black,
        )
        val Light = ChartColors(
            frame = Color(0xFF79747E), track = Color(0xFF9A7B00), satellite = Color(0xFFB3261E),
            footprint = Color(0xFFB3261E), north = Color(0xFFB3261E), east = Color(0xFF006874),
            south = Color(0xFF1F4FA3), west = Color(0xFF006874), elevationLabel = Color(0xFF79747E),
            mapText = Color.Black,
        )

        /** Dim frame, mid labels, brightest (and thicker) track and satellite. */
        val RedNight = ChartColors(
            frame = NightPalette.red(0x70), track = NightPalette.red(0xFF), satellite = NightPalette.red(0xFF),
            footprint = NightPalette.red(0xFF), north = NightPalette.red(0xFF), east = NightPalette.red(0xB0),
            south = NightPalette.red(0xB0), west = NightPalette.red(0xB0), elevationLabel = NightPalette.red(0x90),
            mapText = NightPalette.red(0),
            mapTrack = NightPalette.red(0xFF),
            mapFootprint = NightPalette.red(0xFF),
            mapImageFilter = NightPalette.redLuminanceFilter(NightPalette.MAP_BRIGHTNESS),
            // the icons stay at full brightness so they stand out from the dimmed photograph
            mapIconFilter = NightPalette.redLuminanceFilter(NightPalette.ICON_BRIGHTNESS),
            trackWidthScale = 1.75f,
            footprintDashed = true,
        )
    }
}

/** True while the red night theme is active; set by `HamSatDroidTheme`, so the charts use [ChartColors.RedNight]. */
val LocalRedNight = staticCompositionLocalOf { false }

@Composable
fun chartColors(): ChartColors = when {
    LocalRedNight.current -> ChartColors.RedNight
    MaterialTheme.colorScheme.background.luminance() < 0.5f -> ChartColors.Dark
    else -> ChartColors.Light
}
