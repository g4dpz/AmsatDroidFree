package uk.me.g4dpz.HamSatDroid.alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository

/**
 * Schedules the alarms that drive the alert notifications. One alarm per stage (lead, AOS, LOS), each a broadcast to
 * [PassAlertReceiver] carrying only the alert id and stage; the receiver loads the alert from the store, so a cleared
 * alert does nothing when it fires.
 *
 * Exact alarms are used when the system allows them, otherwise inexact ones, which may fire a little late; the
 * receiver copes by checking the real time.
 */
class PassAlertScheduler(
    private val context: Context,
    private val store: PassAlertStore,
    private val settings: SettingsRepository,
) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(alert: PassAlert, nowMillis: Long = System.currentTimeMillis()) {
        val timing = AlertTiming(alert, settings.alertLeadMinutes.value, nowMillis)
        for ((stage, at) in timing.futureTriggers()) setAlarm(alert.id, stage, at)
    }

    fun cancel(alert: PassAlert) {
        for (stage in AlertStage.entries) alarmManager?.cancel(pendingIntent(alert.id, stage, mutableUpdate = false))
    }

    /** Prunes ended alerts (cancelling their alarms) and reschedules the rest. Called after a reboot or a time change. */
    fun rescheduleAll(nowMillis: Long = System.currentTimeMillis()) {
        for (ended in store.pruneEnded(nowMillis)) cancel(ended)
        for (alert in store.all()) {
            cancel(alert) // clear any stale alarms before setting fresh ones (lead time may have changed)
            schedule(alert, nowMillis)
        }
    }

    private fun setAlarm(id: Int, stage: AlertStage, atMillis: Long) {
        val manager = alarmManager ?: return
        val pending = pendingIntent(id, stage, mutableUpdate = true)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        if (exact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    private fun pendingIntent(id: Int, stage: AlertStage, mutableUpdate: Boolean): PendingIntent {
        val intent = Intent(context, PassAlertReceiver::class.java).apply {
            action = stage.action
            data = android.net.Uri.parse("hamsatdroid://alert/$id/${stage.name}")
            putExtra(PassAlertReceiver.EXTRA_ID, id)
            putExtra(PassAlertReceiver.EXTRA_STAGE, stage.name)
        }
        var flags = PendingIntent.FLAG_IMMUTABLE
        if (mutableUpdate) flags = flags or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getBroadcast(context, requestCode(id, stage), intent, flags)
    }

    private fun requestCode(id: Int, stage: AlertStage): Int = id * 3 + stage.ordinal
}

/** The broadcast action for each stage. */
val AlertStage.action: String
    get() = when (this) {
        AlertStage.LEAD -> "uk.me.g4dpz.HamSatDroid.ALERT_LEAD"
        AlertStage.AOS -> "uk.me.g4dpz.HamSatDroid.ALERT_AOS"
        AlertStage.LOS -> "uk.me.g4dpz.HamSatDroid.ALERT_LOS"
    }
