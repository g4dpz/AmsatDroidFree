package uk.me.g4dpz.HamSatDroid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * A clock that ticks every [periodMillis] milliseconds, but only while the screen is visible (the lifecycle is at least
 * started). It stops completely when the app goes to the background, and starts again with a fresh value when it
 * returns, so nothing keeps running unseen.
 */
@Composable
fun rememberTicker(periodMillis: Long): State<Long> {
    val lifecycleOwner = LocalLifecycleOwner.current
    val ticks = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner, periodMillis) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                ticks.longValue = System.currentTimeMillis()
                delay(periodMillis)
            }
        }
    }
    return ticks
}

/**
 * Keeps the screen on while this is in the composition and [enabled] is true. The flag is cleared when the screen is
 * left or the app goes to the background, so the normal screen timeout returns.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
