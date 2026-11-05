// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.night

import androidx.annotation.StringRes
import uk.me.g4dpz.HamSatDroid.R

/** The theme the user has chosen. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, RED_NIGHT }

/** The name shown in the theme picker. */
@get:StringRes
val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.RED_NIGHT -> R.string.theme_red_night
    }
