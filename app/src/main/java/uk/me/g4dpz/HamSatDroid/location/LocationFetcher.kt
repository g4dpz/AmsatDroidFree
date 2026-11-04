package uk.me.g4dpz.HamSatDroid.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import java.util.function.Consumer

/**
 * Gets the device's position once, using the platform [LocationManager.getCurrentLocation] (Android 11+), instead of
 * the old request-updates-then-cancel approach. Nothing keeps running after the answer or a cancel.
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
            @Suppress("MissingPermission") // checked just above
            manager.getCurrentLocation(provider, cancel, context.mainExecutor, Consumer<Location?> { location ->
                onResult(
                    if (location == null) Outcome.NoFix
                    else Outcome.Found(
                        location.latitude, location.longitude,
                        if (location.hasAccuracy()) location.accuracy else null
                    )
                )
            })
        } catch (e: SecurityException) {
            onResult(Outcome.NoPermission)
        } catch (e: IllegalArgumentException) {
            onResult(Outcome.NoFix) // provider unknown to this device
        }
    }

    private fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
