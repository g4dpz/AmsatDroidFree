// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import uk.me.g4dpz.HamSatDroid.HamSatDroidApp
import uk.me.g4dpz.HamSatDroid.R
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Fires at each stage of a pass. It loads the alert from the store by id (so a cleared alert does nothing), asks
 * [AlertDecision] what to do, shows or clears the notification, drops the alert once the pass has ended, and, if
 * spoken alerts are on, speaks the announcement. The real time is checked so a late alarm (Doze, or after a reboot)
 * shows the stage the pass is actually in.
 *
 * `goAsync` keeps the receiver alive while text-to-speech speaks; `finish()` is called when it is done or has timed
 * out, so the app needs no foreground service.
 */
class PassAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val firedStage = intent.getStringExtra(EXTRA_STAGE)?.let { name -> AlertStage.entries.firstOrNull { it.name == name } }
        val app = context.applicationContext as HamSatDroidApp
        val alert = app.passAlertStore.all().firstOrNull { it.id == id } ?: return

        val now = System.currentTimeMillis()
        val decision = AlertDecision.decide(alert, app.settings.alertLeadMinutes.value, firedStage, now)

        AlertNotifier(context).show(alert, decision.notificationStage)

        if (decision.removeAlert) {
            // the pass has ended: drop it and stop its remaining alarms
            try {
                app.passAlertStore.remove(id)
            } catch (e: IOException) {
                // The alert stays in the file until the next prune; its alarms are cancelled below regardless.
                Log.w(TAG, "Could not remove the ended alert $id: ${e.message}")
            }
            PassAlertScheduler(context, app.passAlertStore, app.settings).cancel(alert)
        }

        if (!app.settings.spokenAlerts.value) return
        val text = decision.announcement?.let { text(context, alert, it) } ?: return
        val result = goAsync()
        SpokenAnnouncer.speak(context, text) { result.finish() }
    }

    private fun text(context: Context, alert: PassAlert, announcement: Announcement): String = when (announcement) {
        is Announcement.Soon -> context.resources.getQuantityString(
            R.plurals.spoken_pass_soon, announcement.minutesToAos,
            alert.satName, announcement.minutesToAos, alert.aosAz, alert.maxElDeg.roundToInt(),
        )
        Announcement.Start ->
            context.resources.getQuantityString(R.plurals.spoken_pass_start, alert.aosAz, alert.satName, alert.aosAz)
        Announcement.End -> context.getString(R.string.spoken_pass_end, alert.satName)
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_STAGE = "stage"
        private const val TAG = "PassAlertReceiver"
    }
}
