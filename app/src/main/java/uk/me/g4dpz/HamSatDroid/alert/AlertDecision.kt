// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.alert

import kotlin.math.roundToInt

/** What the spoken alert should say, as data; the receiver turns it into text in the user's language. */
sealed interface Announcement {
    /** "Rises in [minutesToAos] minutes" (the lead stage). */
    data class Soon(val minutesToAos: Int) : Announcement

    /** "Is now rising" (AOS). */
    data object Start : Announcement

    /** "Pass complete" (LOS). */
    data object End : Announcement
}

/**
 * What [PassAlertReceiver] should do when an alarm fires. Pure, so it is unit tested; the receiver only carries it out.
 *
 * The notification follows the stage the pass is actually in at [nowMillis] ([AlertTiming.stageNow]), not the stage
 * the alarm was set for, so a late alarm (Doze, or after a reboot) shows the right thing. Once the pass has ended the
 * notification is cleared and the alert is dropped.
 *
 * The announcement is spoken for the stage shown, with one exception: once the pass has ended, "pass complete" is
 * spoken only when the alarm that fired was the LOS alarm ([firedStage]). A LEAD or AOS alarm arriving after LOS
 * (a long Doze) clears quietly, because announcing the end of a pass the user never heard about would be noise.
 *
 * @param firedStage the stage the alarm was scheduled for, or null if the intent did not say
 */
data class AlertDecision(
    /** The stage to show in the notification, or null to clear it. */
    val notificationStage: AlertStage?,
    /** Whether the alert is over: drop it from the store and cancel its remaining alarms. */
    val removeAlert: Boolean,
    /** What to say if spoken alerts are on, or null for nothing. */
    val announcement: Announcement?,
) {
    companion object {
        fun decide(alert: PassAlert, leadMinutes: Int, firedStage: AlertStage?, nowMillis: Long): AlertDecision {
            val stage = AlertTiming(alert, leadMinutes, nowMillis).stageNow()
            return when (stage) {
                null -> AlertDecision(
                    notificationStage = null,
                    removeAlert = true,
                    announcement = if (firedStage == AlertStage.LOS) Announcement.End else null,
                )
                AlertStage.LEAD -> AlertDecision(
                    notificationStage = AlertStage.LEAD,
                    removeAlert = false,
                    announcement = Announcement.Soon(((alert.aosMillis - nowMillis) / 60_000.0).roundToInt().coerceAtLeast(0)),
                )
                AlertStage.AOS -> AlertDecision(
                    notificationStage = AlertStage.AOS,
                    removeAlert = false,
                    announcement = Announcement.Start,
                )
                // stageNow() reports the pass as ended (null) at LOS rather than a LOS stage.
                AlertStage.LOS -> AlertDecision(null, removeAlert = true, announcement = Announcement.End)
            }
        }
    }
}
