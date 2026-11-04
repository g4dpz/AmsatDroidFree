package uk.me.g4dpz.HamSatDroid.night

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.ui.ChartColors

class NightPaletteTest {

    /** Every colour role of the scheme. Colours are packed in a `long`, so each role is a no-argument `long` getter. */
    private fun schemeColours(scheme: ColorScheme): Map<String, Color> =
        ColorScheme::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE && it.name.startsWith("get") }
            .associate { it.name.substringBefore('-') to Color((it.invoke(scheme) as Long).toULong()) }

    private fun assertRedOnly(name: String, c: Color) {
        assertEquals("$name has green: $c", 0f, c.green, 0f)
        assertEquals("$name has blue: $c", 0f, c.blue, 0f)
    }

    @Test
    fun `every role of the red night scheme is a shade of red`() {
        val colours = schemeColours(NightPalette.colorScheme)
        // Material 3 1.4 has more than 40 roles; a much smaller number means the reflection is not finding them
        assertTrue("only found ${colours.size} roles", colours.size >= 40)
        for ((name, c) in colours) assertRedOnly(name, c)
    }

    @Test
    fun `text is bright enough against the background`() {
        val s = NightPalette.colorScheme
        assertTrue(s.onBackground.red - s.background.red > 0.7f)
        assertTrue(s.onSurface.red - s.surface.red > 0.7f)
        assertTrue(s.primary.red - s.onPrimary.red > 0.7f)
    }

    @Test
    fun `red night chart colours are shades of red`() {
        val c = ChartColors.RedNight
        for ((name, colour) in listOf(
            "frame" to c.frame, "track" to c.track, "satellite" to c.satellite, "footprint" to c.footprint,
            "north" to c.north, "east" to c.east, "south" to c.south, "west" to c.west,
            "elevationLabel" to c.elevationLabel, "mapText" to c.mapText, "mapTrack" to c.mapTrack,
            "mapFootprint" to c.mapFootprint,
        )) {
            assertRedOnly(name, colour)
        }
    }

    @Test
    fun `red night chart elements differ by more than colour`() {
        val c = ChartColors.RedNight
        assertTrue(c.footprintDashed)
        assertTrue(c.trackWidthScale > 1f)
        assertTrue("track should be brighter than the frame", c.track.red > c.frame.red)
        assertTrue(c.mapImageFilter != null)
        assertTrue(c.mapIconFilter != null)
        assertTrue("icons brighter than the photo", NightPalette.ICON_BRIGHTNESS > NightPalette.MAP_BRIGHTNESS)
        // the other themes are unchanged
        assertEquals(1f, ChartColors.Dark.trackWidthScale, 0f)
        assertEquals(null, ChartColors.Light.mapImageFilter)
    }

    @Test
    fun `the map filter keeps only red, scaled by brightness`() {
        val m = NightPalette.redLuminanceMatrix(0.5f)
        // red row: luminance weights times the scale
        assertEquals(0.5f, m[0] + m[1] + m[2], 1e-6f)
        // green and blue rows are all zero
        for (i in 5 until 15) assertEquals("matrix[$i]", 0f, m[i], 0f)
        // alpha passes through
        assertEquals(1f, m[18], 0f)
        // white becomes red at the chosen brightness
        val white = floatArrayOf(1f, 1f, 1f, 1f)
        val red = (0 until 4).sumOf { (m[it] * white[it]).toDouble() }
        assertEquals(0.5, red, 1e-6)
    }
}
