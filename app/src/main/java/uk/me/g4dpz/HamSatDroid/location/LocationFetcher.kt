// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import java.util.function.Consumer

/**
 * Gets the device's position once. On Android 11+ it uses the platform [LocationManager.getCurrentLocation]; on
 * Android 10 (API 29), which the app also supports, that call does not exist, so a single update is requested
 * ([LocationManager.requestSingleUpdate]) with a timeout. Either way nothing keeps running after the answer, a cancel
 * or the timeout.
 */
class LocationFetcher(private val context: Context) {

    sealed interface Outcome {
        /** [accuracyMeters] is null when the device did not report one. */
        data class Found(val latitude: Double, val longitude: Double, val accuracyMeters: Float?) : Outcome

        /** The provider is switched off in the system settings. */
        data object ProviderDisabled : Outcome

        /** The provider answered without a position (no fix, no network, timed out). */
        data object NoFix : Outcome

        /** The location permission has not been granted. */
        data object NoPermission : Outcome
    }

    /** True if the device has a GPS receiver. Some tablets (for example Wi-Fi-only models) may not. */
    val hasGps: Boolean get() = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    /** The permissions the user must grant before [provider] can be used. */
    fun missingPermissions(provider: String): List<String> {
        val needed = if (provider == LocationManager.GPS_PROVIDER) {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // The network provider works with approximate (coarse) location; precise is also fine.
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                emptyList()
            } else {
                listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        return needed.filterNot(::granted)
    }

    /**
     * Asks [provider] for one position. [onResult] is called once, on the main thread, unless [cancel] is triggered
     * first (then it may not be called at all).
     */
    fun request(provider: String, cancel: CancellationSignal, onResult: (Outcome) -> Unit) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (missingPermissions(provider).isNotEmpty()) {
            onResult(Outcome.NoPermission)
            return
        }
        if (!manager.isProviderEnabled(provider)) {
            onResult(Outcome.ProviderDisabled)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                @Suppress("MissingPermission") // checked just above
                manager.getCurrentLocation(provider, cancel, context.mainExecutor, Consumer<Location?> { location ->
                    onResult(outcomeOf(location))
                })
            } else {
                requestSingleUpdate(manager, provider, cancel, onResult)
            }
        } catch (e: SecurityException) {
            onResult(Outcome.NoPermission)
        } catch (e: IllegalArgumentException) {
            onResult(Outcome.NoFix) // provider unknown to this device
        }
    }

    /** Android 10: one update from [provider], answered once, with NoFix after [TIMEOUT_MILLIS] without an answer. */
    @Suppress("DEPRECATION", "MissingPermission") // requestSingleUpdate is deprecated from API 30; permission checked by the caller
    private fun requestSingleUpdate(
        manager: LocationManager,
        provider: String,
        cancel: CancellationSignal,
        onResult: (Outcome) -> Unit,
    ) {
        val handler = Handler(Looper.getMainLooper())
        var answered = false
        lateinit var listener: LocationListener
        // Stops the timeout and removes the listener from the LocationManager, which keeps its side of the registration
        // until removeUpdates is called, even after a single update has been delivered. Safe to call more than once.
        fun finish() {
            handler.removeCallbacksAndMessages(null)
            manager.removeUpdates(listener)
        }
        val timeout = Runnable {
            if (!answered) {
                answered = true
                finish()
                onResult(Outcome.NoFix)
            }
        }
        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (answered) return
                answered = true
                finish()
                onResult(outcomeOf(location))
            }

            // Android 10 still calls these abstract methods; there is nothing to do for them.
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        cancel.setOnCancelListener {
            answered = true
            finish()
        }
        manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        handler.postDelayed(timeout, TIMEOUT_MILLIS)
        // A cancel that came before the request existed ran finish() too early (there was nothing to remove yet), so
        // look again now that the request and the timeout are in place.
        if (cancel.isCanceled) {
            answered = true
            finish()
        }
    }

    private fun outcomeOf(location: Location?): Outcome =
        if (location == null) {
            Outcome.NoFix
        } else {
            Outcome.Found(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else null)
        }

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        /** How long to wait for a fix on Android 10; the platform call used on Android 11 and later has its own limit. */
        const val TIMEOUT_MILLIS = 30_000L
    }
}
