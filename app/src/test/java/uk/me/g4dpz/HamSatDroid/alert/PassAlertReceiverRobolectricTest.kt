// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.alert

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import uk.me.g4dpz.HamSatDroid.HamSatDroidApp
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.testsupport.TestHamSatDroidApp

/**
 * The receiver's Android-side work, run on the JVM with Robolectric: what it does to the store, the alarms and the
 * notification when an alarm fires. The decision itself is pure and covered by [AlertDecisionTest]; these tests check
 * that the decision is carried out.
 *
 * Spoken alerts stay off: there is no text-to-speech engine here, and a receiver invoked directly has no pending
 * result for goAsync(). The announcement is asserted through the decision instead.
 *
 * `sdk = [34]`: Robolectric 4.14 ships `android-all` jars up to API 35, not the project's compileSdk 36, and 34 is
 * new enough to exercise the exact-alarm permission path (API 31+). Move it with the Robolectric version when
 * compileSdk changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestHamSatDroidApp::class)
class PassAlertReceiverRobolectricTest {

    private val app: HamSatDroidApp get() = ApplicationProvider.getApplicationContext()
    private val alarmManager get() = app.getSystemService(AlarmManager::class.java)
    private val notificationManager get() = app.getSystemService(NotificationManager::class.java)

    private fun alert(aosMillis: Long, losMillis: Long) = PassAlert(25544, "ISS", aosMillis, losMillis, 45.0, 114, 339)

    private fun fire(alert: PassAlert, stage: AlertStage) {
        val intent = Intent(app, PassAlertReceiver::class.java)
            .putExtra(PassAlertReceiver.EXTRA_ID, alert.id)
            .putExtra(PassAlertReceiver.EXTRA_STAGE, stage.name)
        PassAlertReceiver().onReceive(app, intent)
    }

    @Before
    fun setUp() {
        app.settings.spokenAlerts.value = false
        app.settings.alertLeadMinutes.value = 5
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @Test
    fun `at LOS the alert is dropped, its alarms cancelled and its notification cleared`() {
        val now = System.currentTimeMillis()
        val alert = alert(aosMillis = now - 10 * 60_000L, losMillis = now - 1_000L)
        app.passAlertStore.add(alert)
        // Scheduled from before the pass, as the user would have done: three alarms, and the AOS notification up.
        val scheduler = PassAlertScheduler(app, app.passAlertStore, app.settings)
        scheduler.schedule(alert, nowMillis = alert.aosMillis - 60 * 60_000L)
        assertEquals("fixture: three alarms set", 3, shadowOf(alarmManager).scheduledAlarms.size)
        AlertNotifier(app).show(alert, AlertStage.AOS)
        assertNotNull("fixture: AOS notification posted", shadowOf(notificationManager).getNotification(alert.id))

        fire(alert, AlertStage.LOS)

        assertTrue("alert removed from the store", app.passAlertStore.all().isEmpty())
        assertEquals("all three alarms cancelled", 0, shadowOf(alarmManager).scheduledAlarms.size)
        assertNull("notification cleared", shadowOf(notificationManager).getNotification(alert.id))
        // The spoken part cannot run here; the receiver speaks exactly what the decision says, which is End.
        assertEquals(
            Announcement.End,
            AlertDecision.decide(alert, app.settings.alertLeadMinutes.value, AlertStage.LOS, now).announcement,
        )
    }

    @Test
    fun `at LEAD the countdown notification is posted and the alert kept`() {
        val now = System.currentTimeMillis()
        val alert = alert(aosMillis = now + 3 * 60_000L, losMillis = now + 13 * 60_000L)
        app.passAlertStore.add(alert)

        fire(alert, AlertStage.LEAD)

        val notification = shadowOf(notificationManager).getNotification(alert.id)
        assertNotNull("notification posted under the alert id", notification)
        notification!!
        assertEquals(app.getString(R.string.alert_lead_title, "ISS"), notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            app.getString(R.string.alert_body, 114, String.format(java.util.Locale.getDefault(), "%.1f", 45.0)),
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
        assertTrue("counts down", notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals("counts down to AOS", alert.aosMillis, notification.`when`)
        assertTrue("ongoing", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("the alert stays until the pass ends", listOf(alert), app.passAlertStore.all())
    }

    @Test
    fun `an alarm for an alert that was cleared does nothing`() {
        val now = System.currentTimeMillis()
        val alert = alert(aosMillis = now + 3 * 60_000L, losMillis = now + 13 * 60_000L)
        // never added to the store
        fire(alert, AlertStage.LEAD)
        assertNull(shadowOf(notificationManager).getNotification(alert.id))
    }
}
