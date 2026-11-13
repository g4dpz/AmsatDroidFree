// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.domain

/** When the first-run setup guide opens by itself. Pure logic, no Android. */
object SetupPolicy {
    /**
     * The guide opens at start-up only for a user who has not set a home location and has not finished or skipped
     * the guide. Existing users, whose location is set, never see it; nor does anyone who skipped it (the banner on
     * the passes screen reminds them instead).
     *
     * @param locationSet whether the stored home location is set ([HomeLocation.isSet])
     * @param finished whether the guide has been finished or skipped (the `setupFinished` setting)
     */
    fun shouldShowAtStart(locationSet: Boolean, finished: Boolean): Boolean = !locationSet && !finished
}
