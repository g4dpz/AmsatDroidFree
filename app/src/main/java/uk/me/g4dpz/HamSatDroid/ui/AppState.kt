// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import uk.me.g4dpz.HamSatDroid.data.KepsStatus
import uk.me.g4dpz.HamSatDroid.domain.PassHeader
import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import java.time.Instant

/** A satellite in the selection list. */
data class SatelliteItem(
    val catnum: Int,
    val name: String,
    /** The epoch of the satellite's elements, in milliseconds since 1970 UTC (see `ElementAge`). */
    val epochMillis: Long = 0L,
)

/** Where the current elements came from, for the About dialog. */
data class KepsInfo(
    /** A keps source key, or "BUNDLED". */
    val source: String,
    val fetchedAtMillis: Long?,
    val isBundled: Boolean,
    val satelliteCount: Int,
    val lastFailure: KepsStatus.Failure?,
)

/** Everything the screens need to draw, as one immutable value. */
data class AppUiState(
    /** True until the stored elements have been loaded the first time. */
    val loading: Boolean = true,
    val satellites: List<SatelliteItem> = emptyList(),
    val selectedCatnum: Int? = null,
    val keps: KepsInfo? = null,
    /** How far ahead to look for passes: 0 = next pass only, otherwise 6, 12 or 24 hours. */
    val hoursAhead: Int = 0,
    /** The start time chosen by the user, or null for "now". */
    val startTime: Instant? = null,
    val passes: List<PassSummary> = emptyList(),
    val header: PassHeader? = null,
    val selectedPassId: String? = null,
    val calculating: Boolean = false,
    val downloadingKeps: Boolean = false,
) {
    val selectedSatellite: SatelliteItem? get() = satellites.firstOrNull { it.catnum == selectedCatnum }
    val selectedPass: PassSummary? get() = passes.firstOrNull { it.id == selectedPassId }
}

/** One-off things the UI should tell the user about (shown once, not kept in the state). */
sealed interface UiEvent {
    data class KepsUpdated(val satelliteCount: Int) : UiEvent

    /** The same source was fetched less than two hours ago, so nothing was downloaded. */
    data class KepsTooSoon(val lastSuccessMillis: Long?, val nextAllowedAtMillis: Long) : UiEvent

    data class KepsFailed(val reason: String) : UiEvent
}
