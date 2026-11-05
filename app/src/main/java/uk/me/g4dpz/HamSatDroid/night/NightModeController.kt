// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.night

/**
 * Decides from ambient light readings whether the red night theme should be on. Pure logic, no Android.
 *
 * It switches to night when the light stays below [ENTER_LUX] for [ENTER_MILLIS], and back to day when it stays above
 * [EXIT_LUX] for [EXIT_MILLIS]. Between the two thresholds nothing changes, so a reading that hovers around one
 * threshold does not make the screen flicker. A reading on the other side of a threshold restarts the wait.
 *
 * Light sensors report only when the value changes, so the caller should also feed the last reading again from
 * time to time (a steady dark room would otherwise never switch).
 *
 * The thresholds are first guesses, to be tuned on the device.
 */
class NightModeController(initiallyNight: Boolean = false) {

    var isNight: Boolean = initiallyNight
        private set

    /** When the light first went below [ENTER_LUX] (or above [EXIT_LUX]) in the current run, or null. */
    private var darkSince: Long? = null
    private var brightSince: Long? = null

    /** Takes a reading of [lux] at [timeMillis] (a monotonic clock) and returns the new [isNight]. */
    fun onSample(lux: Float, timeMillis: Long): Boolean {
        if (lux < ENTER_LUX) {
            brightSince = null
            val since = darkSince ?: timeMillis.also { darkSince = it }
            if (!isNight && timeMillis - since >= ENTER_MILLIS) isNight = true
        } else if (lux > EXIT_LUX) {
            darkSince = null
            val since = brightSince ?: timeMillis.also { brightSince = it }
            if (isNight && timeMillis - since >= EXIT_MILLIS) isNight = false
        } else {
            darkSince = null
            brightSince = null
        }
        return isNight
    }

    /**
     * Forgets the waits in progress but keeps [isNight]. Called when the app comes back to the foreground, so the
     * screen stays as it was (no flash of a bright theme in the dark) and a wait from before is not counted.
     */
    fun resetTimers() {
        darkSince = null
        brightSince = null
    }

    companion object {
        const val ENTER_LUX = 5f
        const val EXIT_LUX = 20f
        const val ENTER_MILLIS = 5_000L
        const val EXIT_MILLIS = 3_000L
    }
}
