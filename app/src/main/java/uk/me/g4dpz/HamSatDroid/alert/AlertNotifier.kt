// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.alert

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.ui.MainActivity
import uk.me.g4dpz.HamSatDroid.ui.Routes

/**
 * Posts and clears the alert notifications. The countdown is drawn by the system (a chronometer counting down to AOS,
 * then to LOS), so the app does not need to run while it counts. On Android 16 the ongoing notification asks to be a
 * Live Update when the system allows it; otherwise it is a normal ongoing notification with the same content.
 */
class AlertNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        // minSdk is 30, so notification channels always exist.
        val channel = NotificationChannel(
            CHANNEL, context.getString(R.string.alert_channel_name), NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.alert_channel_description) }
        manager.createNotificationChannel(channel)
    }

    /** Shows the notification for [alert] in [stage], or clears it when [stage] is null (the pass has ended). */
    @SuppressLint("MissingPermission") // the caller sets alerts only after POST_NOTIFICATIONS is granted
    fun show(alert: PassAlert, stage: AlertStage?) {
        if (stage == null) {
            manager.cancel(alert.id)
            return
        }
        ensureChannel()
        if (!manager.areNotificationsEnabled()) return

        if (stage == AlertStage.LOS) {
            manager.cancel(alert.id)
            return
        }
        val title = when (stage) {
            AlertStage.LEAD -> context.getString(R.string.alert_lead_title, alert.satName)
            AlertStage.AOS -> context.getString(R.string.alert_aos_title, alert.satName)
            AlertStage.LOS -> return // handled above
        }
        val text = context.getString(
            R.string.alert_body, alert.aosAz, oneDecimal(alert.maxElDeg),
        )
        // Count down to AOS during the lead stage, then to LOS once the satellite is up.
        val countTo = if (stage == AlertStage.LEAD) alert.aosMillis else alert.losMillis

        val tapIntent = PendingIntent.getActivity(
            context, alert.id,
            MainActivity.intent(context, Routes.SKY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.saticon)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(countTo)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(tapIntent)

        val notification = builder.build()
        if (Build.VERSION.SDK_INT >= 36) requestLiveUpdate(notification)
        manager.notify(alert.id, notification)
    }

    /** On Android 16, ask the system to promote this ongoing notification to a Live Update when it is allowed. */
    private fun requestLiveUpdate(notification: Notification) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val canPromote = try {
            NotificationManager::class.java.getMethod("canPostPromotedNotifications").invoke(nm) as? Boolean ?: false
        } catch (e: ReflectiveOperationException) {
            false
        }
        if (canPromote) notification.extras.putBoolean("android.requestPromotedOngoing", true)
    }

    private fun oneDecimal(value: Double): String = String.format(java.util.Locale.getDefault(), "%.1f", value)

    private companion object {
        const val CHANNEL = "pass_alerts"
    }
}
