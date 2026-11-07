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
 * allow exact alarms), updates the store and the alarms, and keeps the set of alert ids as state so rows can show a
 * bell. Remember one with [rememberAlertController] and share it between the screens that show passes.
 */
class AlertController internal constructor(
    private val context: Context,
    private val snackbar: SnackbarHostState,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private val app = context.applicationContext as HamSatDroidApp
    private val store = app.passAlertStore
    private val scheduler = PassAlertScheduler(context, store, app.settings)

    var alertIds by mutableStateOf(store.all().map { it.id }.toSet())
        private set

    /** A pass waiting for the notification permission answer; set an alert for it once granted. */
    internal var pendingAfterPermission: PassAlert? = null

    /** Set by [rememberAlertController]: launches the notification-permission request. */
    internal var permissionLauncher: (() -> Unit)? = null

    /** Whether to show the "allow exact alarms?" dialog. */
    var showExactAlarmDialog by mutableStateOf(false)
        internal set

    fun hasAlert(pass: PassSummary): Boolean = alertIds.contains(PassAlert.from(pass).id)

    private fun refresh() {
        alertIds = store.all().map { it.id }.toSet()
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
        if (store.contains(alert.id)) {
            clear(alert)
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
        try {
            store.add(alert)
        } catch (e: IOException) {
            // Not stored, so not scheduled either: the receiver would find nothing when the alarm fired.
            message(R.string.alert_save_failed, e.message ?: e.javaClass.simpleName)
            return
        }
        scheduler.schedule(alert)
        refresh()
        message(R.string.alert_set, alert.satName)
        maybeOfferExactAlarms()
    }

    private fun clear(alert: PassAlert) {
        val removed = try {
            store.remove(alert.id)
            true
        } catch (e: IOException) {
            // Still in the file, so the bell stays; its alarms and notification go regardless, and the next
            // successful write (a later clear, or the receiver's prune) drops it.
            message(R.string.alert_save_failed, e.message ?: e.javaClass.simpleName)
            false
        }
        scheduler.cancel(alert)
        AlertNotifier(context).show(alert, stage = null)
        refresh()
        if (removed) message(R.string.alert_cleared, alert.satName)
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
