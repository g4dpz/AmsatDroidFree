// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

import java.time.Duration
import java.time.Instant

/** How a pass card looks at a given moment (David, 24 Nov 2026, after Peter's OscarWatch cards). */
enum class PassCardState {
    /** More than [PassCardStates.SOON_BEFORE] before AOS: grey, information only. */
    UPCOMING,

    /** From [PassCardStates.SOON_BEFORE] before AOS until AOS: orange, with a countdown to AOS. */
    SOON,

    /** AOS to LOS: green, "Passing". */
    PASSING,

    /** After LOS (a restored list can still hold it): grey. */
    OVER,
}

object PassCardStates {
    /** How long before AOS a card turns orange. */
    val SOON_BEFORE: Duration = Duration.ofMinutes(5)

    fun of(pass: PassSummary, now: Instant): PassCardState = when {
        pass.isOver(now) -> PassCardState.OVER
        pass.isInProgress(now) -> PassCardState.PASSING
        !now.isBefore(pass.start.minus(SOON_BEFORE)) -> PassCardState.SOON
        else -> PassCardState.UPCOMING
    }
}
