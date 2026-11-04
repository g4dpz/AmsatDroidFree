package uk.me.g4dpz.HamSatDroid.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import uk.me.g4dpz.HamSatDroid.night.NightPalette
import uk.me.g4dpz.HamSatDroid.night.ThemeMode
import uk.me.g4dpz.HamSatDroid.ui.LocalRedNight

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/** Whether [mode] draws light-on-dark (dark and red night do). */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.RED_NIGHT -> true
}

/**
 * The app theme (Material 3).
 *
 * [ThemeMode.SYSTEM] follows the device's light/dark setting. On Android 12 and later the colours come from the
 * user's wallpaper (dynamic colour); older versions use the standard Material 3 palettes.
 *
 * [ThemeMode.RED_NIGHT] uses [NightPalette] (shades of red only) and switches the charts to their red variant.
 * Changing the mode recomposes in place; the activity is not recreated.
 */
@Composable
fun HamSatDroidTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val redNight = mode == ThemeMode.RED_NIGHT
    val dark = mode.isDark()
    val colorScheme = when {
        redNight -> NightPalette.colorScheme
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> if (dark) DarkColors else LightColors
    }
    CompositionLocalProvider(LocalRedNight provides redNight) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
