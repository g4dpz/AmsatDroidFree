// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.widget

import android.content.Context
import uk.me.g4dpz.HamSatDroid.HamSatDroidApp
import uk.me.g4dpz.HamSatDroid.data.LocationText
import uk.me.g4dpz.HamSatDroid.data.createKepsRepository
import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.InvalidTleException
import uk.me.g4dpz.satellite.PassPredictor
import uk.me.g4dpz.satellite.SatNotFoundException
import uk.me.g4dpz.satellite.TLE
import java.util.Date

/**
 * Works out what the next-pass widget should show, using the same prediction code as the app. The decision of which
 * state to show is a pure function ([decide]) so it can be unit tested; [compute] wires in the stored keps, the last
 * selected satellite and the home location.
 */
object WidgetUpdater {

    /**
     * The widget state for the given inputs, without any prediction: whether there is a location, a selected
     * satellite in the keps, and (when a pass was found) the pass. [nextPass] is the result of running the predictor,
     * or null if none/failed; it is only consulted when a location and satellite are present.
     */
    fun decide(
        latitude: Double?,
        longitude: Double?,
        tle: TLE?,
        nextPass: PassSummary?,
        predictionFailed: Boolean,
    ): WidgetState = when {
        latitude == null || longitude == null -> WidgetState.NoLocation
        tle == null -> WidgetState.NoSatellite
        predictionFailed -> WidgetState.Error
        nextPass == null -> WidgetState.Error
        else -> WidgetState.Ready(tle.name, nextPass)
    }

    /** Loads the current data and computes the state. Safe to call off the main thread (it does I/O and prediction). */
    fun compute(context: Context, nowMillis: Long = System.currentTimeMillis()): WidgetState {
        val settings = (context.applicationContext as HamSatDroidApp).settings
        val latitude = LocationText.toDegrees(settings.homeLat.value)
        val longitude = LocationText.toDegrees(settings.homeLon.value)
        if (latitude == null || longitude == null) return WidgetState.NoLocation

        val catnum = settings.selectedCatnum.value.takeIf { it > 0 }
        val tle = catnum?.let { c -> createKepsRepository(context).load().tles.firstOrNull { it.catnum == c } }
        if (tle == null) return WidgetState.NoSatellite

        return try {
            val predictor = PassPredictor(tle, GroundStationPosition(latitude, longitude, 0.0))
            val pass = predictor.nextSatPass(Date(nowMillis))
            // The library reports "nothing" as a pass with zero max elevation.
            if (pass.maxEl == 0.0) {
                WidgetState.Error
            } else {
                WidgetState.Ready(tle.name, PassSummary.from(pass, tle.catnum, tle.name))
            }
        } catch (e: InvalidTleException) {
            WidgetState.Error
        } catch (e: SatNotFoundException) {
            WidgetState.Error
        } catch (e: RuntimeException) {
            WidgetState.Error
        }
    }
}
