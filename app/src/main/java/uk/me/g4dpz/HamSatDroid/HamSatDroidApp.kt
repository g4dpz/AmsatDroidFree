package uk.me.g4dpz.HamSatDroid

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uk.me.g4dpz.HamSatDroid.alert.AlertNotifier
import uk.me.g4dpz.HamSatDroid.alert.PassAlertStore
import uk.me.g4dpz.HamSatDroid.data.FileStore
import uk.me.g4dpz.HamSatDroid.data.FrequencyStore
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.data.SharedPreferencesStore
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

    /** The user's per-satellite frequency entries, for Doppler. */
    val frequencyStore: FrequencyStore by lazy { FrequencyStore(FileStore(filesDir)) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AlertNotifier(this).ensureChannel()
        val scheduler = KepsScheduler(this)
        // A StateFlow delivers the current value first (applied at start-up) and then every change.
        scope.launch {
            settings.autoKepsRefresh.state.collect { enabled -> scheduler.apply(enabled) }
        }
    }
}
