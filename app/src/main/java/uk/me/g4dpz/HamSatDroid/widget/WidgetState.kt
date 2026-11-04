package uk.me.g4dpz.HamSatDroid.widget

import uk.me.g4dpz.HamSatDroid.domain.PassSummary

/** What the next-pass widget shows. The content is worked out off the widget and passed in. */
sealed interface WidgetState {
    /** No home location set; the widget tells the user to set one. */
    data object NoLocation : WidgetState

    /** A location is set but there is no satellite selected or it is not in the keps. */
    data object NoSatellite : WidgetState

    /** A next pass was found. [satName] may differ from the pass if the keps name is preferred. */
    data class Ready(val satName: String, val pass: PassSummary) : WidgetState

    /** The elements could not be used, or the prediction failed. */
    data object Error : WidgetState
}
