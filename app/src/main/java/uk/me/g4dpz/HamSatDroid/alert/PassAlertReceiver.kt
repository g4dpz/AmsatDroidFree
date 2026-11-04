package uk.me.g4dpz.HamSatDroid.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import uk.me.g4dpz.HamSatDroid.HamSatDroidApp
import uk.me.g4dpz.HamSatDroid.R
import kotlin.math.roundToInt

/**
 * Fires at each stage of a pass. It loads the alert from the store by id (so a cleared alert does nothing), shows or
 * updates the notification, and, if spoken alerts are on, speaks the announcement. The real time is checked so a late
 * alarm (Doze, or after a reboot) shows the stage the pass is actually in.
 *
 * `goAsync` keeps the receiver alive while text-to-speech speaks; `finish()` is called when it is done or has timed
 * out, so the app needs no foreground service.
 */
class PassAlertReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val app = context.applicationContext as HamSatDroidApp
        val alert = app.passAlertStore.all().firstOrNull { it.id == id } ?: return

        val now = System.currentTimeMillis()
        val stage = AlertTiming(alert, app.settings.alertLeadMinutes.value, now).stageNow()

        val notifier = AlertNotifier(context)
        notifier.show(alert, stage)

        if (stage == null) {
            // the pass has ended: drop it and stop its remaining alarms
            app.passAlertStore.remove(id)
            PassAlertScheduler(context, app.passAlertStore, app.settings).cancel(alert)
            return
        }

        if (!app.settings.spokenAlerts.value) return
        val text = announcement(context, alert, stage, now) ?: return
        val result = goAsync()
        SpokenAnnouncer.speak(context, text) { result.finish() }
    }

    private fun announcement(context: Context, alert: PassAlert, stage: AlertStage, now: Long): String? = when (stage) {
        AlertStage.LEAD -> {
            val minutes = ((alert.aosMillis - now) / 60_000.0).roundToInt().coerceAtLeast(0)
            context.resources.getQuantityString(
                R.plurals.spoken_pass_soon, minutes, alert.satName, minutes, alert.aosAz, alert.maxElDeg.roundToInt(),
            )
        }
        AlertStage.AOS ->
            context.resources.getQuantityString(R.plurals.spoken_pass_start, alert.aosAz, alert.satName, alert.aosAz)
        AlertStage.LOS -> context.getString(R.string.spoken_pass_end, alert.satName)
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_STAGE = "stage"
    }
}
