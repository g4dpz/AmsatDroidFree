// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import uk.me.g4dpz.HamSatDroid.domain.SetupPolicy

/** Which screen the app opens on. Pure logic, no Android. */
object StartRoute {
    /**
     * A screen asked for by the launch (a notification or a widget tap) always wins: both only exist once a location is
     * set, and they must open what they point at (first-run setup, requirement 1.4). Otherwise the setup guide, if
     * [SetupPolicy] says so, then the passes screen.
     *
     * [requested] must be null for an ordinary launch from the launcher. Before the guide existed `MainActivity` turned a
     * missing request into [Routes.PASSES], and the widget asks for [Routes.PASSES] too, so the two could not be told
     * apart and a widget tap on a fresh install would have looked like an ordinary launch, or the reverse.
     */
    fun choose(requested: String?, locationSet: Boolean, finished: Boolean): String = when {
        requested != null -> requested
        SetupPolicy.shouldShowAtStart(locationSet, finished) -> Routes.SETUP
        else -> Routes.PASSES
    }
}
