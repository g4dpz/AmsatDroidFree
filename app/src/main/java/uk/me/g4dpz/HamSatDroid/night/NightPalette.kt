package uk.me.g4dpz.HamSatDroid.night

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * The red night palette: every colour is a shade of pure red (green and blue are zero), so the display does not
 * spoil dark-adapted eyes. Things are told apart by brightness instead of hue.
 *
 * Every role of the Material 3 scheme is set here, including the ones the app does not use directly today, so no
 * component can fall back to a default blue, grey or white. `NightPaletteTest` checks that.
 */
object NightPalette {

    /** A shade of pure red, from 0 (black) to 255 (brightest red). */
    fun red(level: Int): Color = Color(red = level / 255f, green = 0f, blue = 0f)

    private val black = red(0)

    val colorScheme: ColorScheme = darkColorScheme(
        primary = red(0xD0),
        onPrimary = black,
        primaryContainer = red(0x50),
        onPrimaryContainer = red(0xFF),
        inversePrimary = red(0x60),
        secondary = red(0xB0),
        onSecondary = black,
        secondaryContainer = red(0x40),
        onSecondaryContainer = red(0xE0),
        tertiary = red(0xB0),
        onTertiary = black,
        tertiaryContainer = red(0x40),
        onTertiaryContainer = red(0xE0),
        background = black,
        onBackground = red(0xE0),
        surface = black,
        onSurface = red(0xE0),
        surfaceVariant = red(0x2A),
        onSurfaceVariant = red(0xB0),
        surfaceTint = red(0xD0),
        inverseSurface = red(0xB0),
        inverseOnSurface = black,
        error = red(0xFF),
        onError = black,
        errorContainer = red(0x60),
        onErrorContainer = red(0xFF),
        outline = red(0x90),
        outlineVariant = red(0x40),
        scrim = black,
        surfaceBright = red(0x30),
        surfaceContainer = red(0x1A),
        surfaceContainerHigh = red(0x22),
        surfaceContainerHighest = red(0x2A),
        surfaceContainerLow = red(0x12),
        surfaceContainerLowest = black,
        surfaceDim = black,
        primaryFixed = red(0xD0),
        primaryFixedDim = red(0xA0),
        onPrimaryFixed = black,
        onPrimaryFixedVariant = red(0x30),
        secondaryFixed = red(0xB0),
        secondaryFixedDim = red(0x90),
        onSecondaryFixed = black,
        onSecondaryFixedVariant = red(0x30),
        tertiaryFixed = red(0xB0),
        tertiaryFixedDim = red(0x90),
        onTertiaryFixed = black,
        onTertiaryFixedVariant = red(0x30),
    )

    /** How bright the map photograph is kept, so the track and footprint drawn over it stand out. */
    const val MAP_BRIGHTNESS = 0.55f

    /**
     * The scale for the map icons. Above 1 because the icons are dark-ish pictures: the luminance is boosted (and
     * clamped at full red) so they stand out against the dimmed photograph.
     */
    const val ICON_BRIGHTNESS = 1.6f

    /**
     * Turns a picture (the world map, the icons) into shades of red by its brightness: red = scale x luminance,
     * green = blue = 0, alpha unchanged.
     */
    fun redLuminanceMatrix(scale: Float): FloatArray = floatArrayOf(
        0.299f * scale, 0.587f * scale, 0.114f * scale, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    fun redLuminanceFilter(scale: Float): ColorFilter = ColorFilter.colorMatrix(ColorMatrix(redLuminanceMatrix(scale)))
}
