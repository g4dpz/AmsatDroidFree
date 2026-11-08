// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import uk.me.g4dpz.HamSatDroid.HamSatDroidApp
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.alert.AlertNotifier
import uk.me.g4dpz.HamSatDroid.alert.PassAlert
import uk.me.g4dpz.HamSatDroid.alert.PassAlertScheduler
import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import java.io.IOException

/**
 * Setting and clearing pass alerts from the UI. It owns the permission flow (notifications, and the one-time offer to
 * allow exact alarms), updates the store and the alarms, and keeps the stored alerts as state so rows can show a
 * bell. Remember one with [rememberAlertController] and share it between the screens that show passes.
 *
 * A pass is recognised by satellite and overlapping time ([PassAlert.isSamePass]), not by an exact AOS: the overview
 * and the passes screen predict the same pass from different start times and get times a few seconds apart.
 */
class AlertController internal constructor(
    private val context: Context,
    private val snackbar: SnackbarHostState,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private val app = context.applicationContext as HamSatDroidApp
    private val store = app.passAlertStore
    private val scheduler = PassAlertScheduler(context, store, app.settings)

    var alerts by mutableStateOf(store.all())
        private set

    /** A pass waiting for the notification permission answer; set an alert for it once granted. */
    internal var pendingAfterPermission: PassAlert? = null

    /** Set by [rememberAlertController]: launches the notification-permission request. */
    internal var permissionLauncher: (() -> Unit)? = null

    /** Whether to show the "allow exact alarms?" dialog. */
    var showExactAlarmDialog by mutableStateOf(false)
        internal set

    fun hasAlert(pass: PassSummary): Boolean = alerts.any { it.isSamePass(pass) }

    private fun refresh() {
        alerts = store.all()
    }

    private fun message(resId: Int, arg: String) {
        scope.launch { snackbar.showSnackbar(context.getString(resId, arg)) }
    }

    /** Called by the UI when the notification permission is granted. */
    internal fun onNotificationsGranted() {
        pendingAfterPermission?.let { set(it) }
        pendingAfterPermission = null
    }

    internal fun onNotificationsDenied() {
        pendingAfterPermission = null
        scope.launch { snackbar.showSnackbar(context.getString(R.string.alert_needs_notifications)) }
    }

    /** Toggles the alert for [pass], asking for the notification permission first if it is needed. */
    fun toggle(pass: PassSummary) {
        val alert = PassAlert.from(pass)
        // Clear the alerts as stored: their notifications and alarms are registered under the stored ids, which may
        // differ from this prediction's id by a few seconds of AOS. There can be more than one for the pass on a
        // device that set it from both screens before passes were matched by overlap.
        val existing = store.findAll(alert.catnum, alert.aosMillis, alert.losMillis)
        if (existing.isNotEmpty()) {
            clear(existing)
            return
        }
        if (alert.hasEnded(System.currentTimeMillis())) {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.alert_pass_over)) }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingAfterPermission = alert
            permissionLauncher?.invoke()
            return
        }
        set(alert)
    }

    private fun set(alert: PassAlert) {
        val replaced = try {
            store.add(alert)
        } catch (e: IOException) {
            // Not stored, so not scheduled either: the receiver would find nothing when the alarm fired.
            message(R.string.alert_save_failed, e.message ?: e.javaClass.simpleName)
            return
        }
        // An alert for the same pass under another id (set while the permission dialog was up, say) is replaced;
        // its alarms and notification would otherwise fire alongside the new ones.
        for (old in replaced) {
            scheduler.cancel(old)
            AlertNotifier(context).show(old, stage = null)
        }
        scheduler.schedule(alert)
        refresh()
        message(R.string.alert_set, alert.satName)
        maybeOfferExactAlarms()
    }

    private fun clear(existing: List<PassAlert>) {
        val removed = try {
            store.removeAll(existing.map { it.id }.toSet())
            true
        } catch (e: IOException) {
            // Still in the file, so the bell stays; their alarms and notifications go regardless, and the next
            // successful write (a later clear, or the receiver's prune) drops them.
            message(R.string.alert_save_failed, e.message ?: e.javaClass.simpleName)
            false
        }
        val notifier = AlertNotifier(context)
        for (alert in existing) {
            scheduler.cancel(alert)
            notifier.show(alert, stage = null)
        }
        refresh()
        if (removed) message(R.string.alert_cleared, existing.first().satName)
    }

    /** Reschedule everything after the lead time changes. */
    fun rescheduleAll() {
        scheduler.rescheduleAll()
        refresh()
    }

    private fun maybeOfferExactAlarms() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java) ?: return
        if (alarmManager.canScheduleExactAlarms()) return
        if (app.settings.askedExactAlarm.value) return
        app.settings.askedExactAlarm.value = true
        showExactAlarmDialog = true
    }

    fun openExactAlarmSettings() {
        showExactAlarmDialog = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

}

// POST_NOTIFICATIONS names an API 33 constant; the request is only launched on API 33+ (guarded in toggle).
@SuppressLint("InlinedApi")
@Composable
fun rememberAlertController(snackbar: SnackbarHostState): AlertController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { AlertController(context, snackbar, scope) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.onNotificationsGranted() else controller.onNotificationsDenied()
    }
    // Hold the launcher so toggle()'s PermissionRequest can be acted on
    controller.permissionLauncher = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }

    if (controller.showExactAlarmDialog) {
        AlertDialog(
            onDismissRequest = { controller.showExactAlarmDialog = false },
            title = { Text(stringResource(R.string.alert_exact_title)) },
            text = { Text(stringResource(R.string.alert_exact_message)) },
            confirmButton = {
                TextButton(onClick = { controller.openExactAlarmSettings() }) {
                    Text(stringResource(R.string.alert_exact_open))
                }
            },
            dismissButton = {
                TextButton(onClick = { controller.showExactAlarmDialog = false }) {
                    Text(stringResource(R.string.alert_exact_later))
                }
            },
        )
    }
    return controller
}
