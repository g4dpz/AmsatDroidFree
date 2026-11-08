// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.widget

import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import uk.me.g4dpz.HamSatDroid.domain.SatelliteMotion

/** What the next-pass widget shows. The content is worked out off the widget and passed in. */
sealed interface WidgetState {
    /** No home location set; the widget tells the user to set one. */
    data object NoLocation : WidgetState

    /** A location is set but there is no satellite selected or it is not in the keps. */
    data object NoSatellite : WidgetState

    /** A next pass was found. [satName] may differ from the pass if the keps name is preferred. */
    data class Ready(val satName: String, val pass: PassSummary) : WidgetState

    /**
     * The satellite is stationary in the sky (geostationary), so there is no next pass: it is always up at
     * [position] or never visible from the home location.
     */
    data class Stationary(val satName: String, val position: SatelliteMotion.FixedPosition) : WidgetState

    /** The elements could not be used, or the prediction failed. */
    data object Error : WidgetState
}
