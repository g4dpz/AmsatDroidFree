// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import android.content.Context
import android.os.Build
import uk.me.g4dpz.HamSatDroid.R

/**
 * Builds the app's [KepsRepository]: files in the app's private directory, https downloads with a User-Agent naming
 * the app and its version, and the keps shipped in `res/raw/nasabare.txt` as the fallback. Used by both the activity
 * and the background worker so they behave identically.
 */
fun createKepsRepository(context: Context): KepsRepository {
    val app = context.applicationContext
    val version = try {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        "unknown"
    }
    return KepsRepository(
        files = FileStore(app.filesDir),
        downloader = HttpsKepsDownloader(HttpsKepsDownloader.userAgent(version, Build.VERSION.RELEASE)),
        bundledText = { app.resources.openRawResource(R.raw.nasabare).bufferedReader().use { it.readText() } },
    )
}
