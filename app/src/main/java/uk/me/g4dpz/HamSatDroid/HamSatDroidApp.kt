// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uk.me.g4dpz.HamSatDroid.alert.AlertNotifier
import uk.me.g4dpz.HamSatDroid.alert.PassAlertStore
import uk.me.g4dpz.HamSatDroid.data.FileStore
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.data.SharedPreferencesStore
import uk.me.g4dpz.HamSatDroid.data.TransponderDatabase
import uk.me.g4dpz.HamSatDroid.data.TransponderSyncResult
import uk.me.g4dpz.HamSatDroid.data.createTransponderDatabaseRepository
import uk.me.g4dpz.HamSatDroid.keps.KepsScheduler

/**
 * Application object: owns the one shared [SettingsRepository] (so every screen sees the same live values) and keeps
 * the background keps refresh in step with the "automatic refresh" setting.
 */
open class HamSatDroidApp : Application() {

    /** The app's settings. Use this instance everywhere so changes reach all observers. */
    val settings: SettingsRepository by lazy { SettingsRepository(SharedPreferencesStore(this)) }

    /** The pass alerts the user has set. Shared so the UI and the broadcast receivers see the same list. */
    val passAlertStore: PassAlertStore by lazy { PassAlertStore(FileStore(filesDir)) }

    private val _transponderDatabase = MutableStateFlow(TransponderDatabase.EMPTY)

    /**
     * The satellite transponder database (OscarWatch's `satellite_database.json`), read-only, for Doppler. It is
     * [TransponderDatabase.EMPTY] until the first load finishes and is replaced after every successful sync
     * (start-up and the background worker), so observe it rather than reading it once.
     */
    val transponderDatabase: StateFlow<TransponderDatabase> = _transponderDatabase.asStateFlow()

    // IO: the work here is file reads and, for the sync, a network download.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AlertNotifier(this).ensureChannel()
        val scheduler = KepsScheduler(this)
        // A StateFlow delivers the current value first (applied at start-up) and then every change.
        scope.launch {
            settings.autoKepsRefresh.state.collect { enabled -> scheduler.apply(enabled) }
        }
        scope.launch {
            reloadTransponderDatabase()
            // When automatic refresh is on, also sync the published copy (the minimum-interval rule makes this cheap
            // when it ran recently) and pick up the result.
            if (settings.autoKepsRefresh.value &&
                createTransponderDatabaseRepository(this@HamSatDroidApp).sync() is TransponderSyncResult.Updated
            ) {
                reloadTransponderDatabase()
            }
        }
    }

    /**
     * Reads the stored (or bundled) transponder database into [transponderDatabase]. Blocking file I/O: call off the
     * main thread. The background worker calls this after a successful sync so the app sees the new file at once.
     */
    fun reloadTransponderDatabase() {
        _transponderDatabase.value = createTransponderDatabaseRepository(this).load().database
    }
}
