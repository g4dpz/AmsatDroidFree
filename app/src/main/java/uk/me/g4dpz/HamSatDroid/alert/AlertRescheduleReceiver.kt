package uk.me.g4dpz.HamSatDroid.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uk.me.g4dpz.HamSatDroid.HamSatDroidApp

/**
 * Re-registers all alarms when they would otherwise be lost or shifted: after a reboot, an app update, or a change to
 * the clock or time zone, and when permission to schedule exact alarms is granted or revoked. Any other action is
 * ignored.
 */
class AlertRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> Unit
            else -> return
        }
        val app = context.applicationContext as HamSatDroidApp
        AlertNotifier(context).ensureChannel()
        PassAlertScheduler(context, app.passAlertStore, app.settings).rescheduleAll()
    }
}
