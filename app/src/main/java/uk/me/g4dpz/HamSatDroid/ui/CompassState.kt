// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import uk.me.g4dpz.HamSatDroid.aim.CompassProvider
import uk.me.g4dpz.HamSatDroid.aim.Orientation

/**
 * Collects compass readings while the sky screen is visible and [enabled], for the observer at [latitude]/[longitude].
 * The sensor registers only while this is in the composition and the lifecycle is at least STARTED, so it stops when
 * the screen is left or the app goes to the background. Null while disabled, unavailable, or before the first reading.
 */
@Composable
fun rememberCompassOrientation(
    provider: CompassProvider,
    enabled: Boolean,
    latitude: Double?,
    longitude: Double?,
): State<Orientation?> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val view = LocalView.current
    return produceState<Orientation?>(null, provider, enabled, latitude, longitude) {
        value = null
        if (!enabled || !provider.isAvailable || latitude == null || longitude == null) return@produceState
        val displayRotation = view.display?.rotation ?: android.view.Surface.ROTATION_0
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            provider.orientations(latitude, longitude, displayRotation).collect { value = it }
        }
    }
}

/** A `CompassProvider` tied to the current context. */
@Composable
fun rememberCompassProvider(): CompassProvider {
    val context = LocalContext.current
    return remember { CompassProvider(context) }
}
