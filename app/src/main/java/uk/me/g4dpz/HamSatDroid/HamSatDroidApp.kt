// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uk.me.g4dpz.HamSatDroid.alert.AlertNotifier
import uk.me.g4dpz.HamSatDroid.alert.PassAlertStore
import uk.me.g4dpz.HamSatDroid.data.FileStore
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.data.SharedPreferencesStore
import uk.me.g4dpz.HamSatDroid.data.TransponderDatabase
import uk.me.g4dpz.HamSatDroid.data.createTransponderDatabaseRepository
import uk.me.g4dpz.HamSatDroid.keps.KepsScheduler

/**
 * Application object: owns the one shared [SettingsRepository] (so every screen sees the same live values) and keeps
 * the background keps refresh in step with the "automatic refresh" setting.
 */
class HamSatDroidApp : Application() {

    /** The app's settings. Use this instance everywhere so changes reach all observers. */
    val settings: SettingsRepository by lazy { SettingsRepository(SharedPreferencesStore(this)) }

    /** The pass alerts the user has set. Shared so the UI and the broadcast receivers see the same list. */
    val passAlertStore: PassAlertStore by lazy { PassAlertStore(FileStore(filesDir)) }

    /**
     * The satellite transponder database (OscarWatch's `satellite_database.json`), read-only, for Doppler. Loaded
     * once (synced copy if present, otherwise bundled). It is replaced after a successful background sync; read it
     * through [transponderDatabase] so the latest load is seen.
     */
    @Volatile
    var transponderDatabase: TransponderDatabase = TransponderDatabase.EMPTY
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AlertNotifier(this).ensureChannel()
        val scheduler = KepsScheduler(this)
        // A StateFlow delivers the current value first (applied at start-up) and then every change.
        scope.launch {
            settings.autoKepsRefresh.state.collect { enabled -> scheduler.apply(enabled) }
        }
        loadTransponderDatabase()
    }

    /**
     * Loads the transponder database off the main thread, and, when automatic refresh is on, syncs the published copy
     * (respecting the minimum-interval rule) and reloads if it changed. Safe to call again after a background sync.
     */
    fun loadTransponderDatabase() {
        scope.launch {
            val repository = createTransponderDatabaseRepository(this@HamSatDroidApp)
            transponderDatabase = repository.load().database
            if (settings.autoKepsRefresh.value) {
                repository.sync()
                transponderDatabase = repository.load().database
            }
        }
    }
}
