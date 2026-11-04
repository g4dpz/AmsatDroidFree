package uk.me.g4dpz.HamSatDroid.night

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the last light reading is fed to the controller again, because the sensor reports only changes. */
private const val TICK_MILLIS = 500L

/**
 * Whether the light sensor says it is dark enough for red night. Always false unless [enabled] and the device has a
 * light sensor. The sensor runs only while the screen is started (visible); when the app comes back the previous
 * answer is kept, so returning to the app in the dark does not flash a bright theme.
 */
@Composable
fun rememberAmbientNight(provider: AmbientLightProvider, enabled: Boolean): State<Boolean> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val controller = remember { NightModeController() }
    return produceState(initialValue = enabled && controller.isNight, provider, enabled) {
        if (!enabled || !provider.available) {
            value = false
            return@produceState
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            controller.resetTimers()
            var last: Float? = null
            coroutineScope {
                launch {
                    provider.lux().collect { lux ->
                        last = lux
                        value = controller.onSample(lux, SystemClock.elapsedRealtime())
                    }
                }
                while (true) {
                    delay(TICK_MILLIS)
                    last?.let { value = controller.onSample(it, SystemClock.elapsedRealtime()) }
                }
            }
        }
    }
}

/** The theme to show: red night while the light sensor says so (automatic mode), otherwise the chosen theme. */
fun effectiveThemeMode(chosen: ThemeMode, ambientNight: Boolean): ThemeMode =
    if (ambientNight) ThemeMode.RED_NIGHT else chosen
