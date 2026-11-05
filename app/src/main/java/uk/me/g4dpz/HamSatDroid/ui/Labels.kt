// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.annotation.StringRes
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.data.KepsSource

/** The text shown for each keps source. */
@get:StringRes
val KepsSource.labelRes: Int
    get() = when (this) {
        KepsSource.AMATEUR_AMSAT -> R.string.DownloadAmateurAmsatElem
        KepsSource.AMATEUR_CELESTRAK -> R.string.DownloadAmateurCelestrakElem
        KepsSource.WEATHER_CELESTRAK -> R.string.DownloadWeatherCelestrakElem
        KepsSource.CUBESAT_CELESTRAK -> R.string.DownloadCubesatCelestrakElem
        KepsSource.RESOURCES_CELESTRAK -> R.string.DownloadResourcesCelestrakElem
        KepsSource.NEW_CELESTRAK -> R.string.DownloadNewCelestrakElem
    }
