package uk.me.g4dpz.HamSatDroid.data

import android.content.Context
import android.os.Build
import uk.me.g4dpz.HamSatDroid.R

/**
 * Builds the app's [TransponderDatabaseRepository]: files in the app's private directory, https downloads with a
 * User-Agent naming the app and version, and the transponder database shipped in `res/raw/satellite_database.json`
 * as the fallback. Used by the app and the background worker so they behave identically.
 */
fun createTransponderDatabaseRepository(context: Context): TransponderDatabaseRepository {
    val app = context.applicationContext
    val version = try {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "unknown"
    } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
        "unknown"
    }
    return TransponderDatabaseRepository(
        files = FileStore(app.filesDir),
        downloader = HttpsKepsDownloader(HttpsKepsDownloader.userAgent(version, Build.VERSION.RELEASE)),
        bundledText = { app.resources.openRawResource(R.raw.satellite_database).bufferedReader().use { it.readText() } },
    )
}
