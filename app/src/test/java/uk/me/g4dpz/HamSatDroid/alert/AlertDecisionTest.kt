// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The receiver's decisions, stage by stage. The LOS cases are the ones that were wrong before: the LOS alarm fires at
 * or after LOS, when the pass counts as ended, so "pass complete" was never spoken and the LOS branches were dead.
 */
class AlertDecisionTest {

    private val aos = 1_000_000_000_000L
    private val los = aos + 10 * 60_000L // 10 minutes later
    private val lead = 5
    private val alert = PassAlert(25544, "ISS", aos, los, 45.0, 114, 339)

    private fun decide(now: Long, fired: AlertStage?) = AlertDecision.decide(alert, lead, fired, now)

    @Test
    fun `the lead alarm shows the lead notification and says how many minutes to AOS`() {
        val d = decide(aos - 5 * 60_000L, AlertStage.LEAD)
        assertEquals(AlertStage.LEAD, d.notificationStage)
        assertFalse(d.removeAlert)
        assertEquals(Announcement.Soon(5), d.announcement)
    }

    @Test
    fun `a late lead alarm rounds the remaining minutes`() {
        // 2 minutes 40 seconds before AOS rounds to 3
        assertEquals(Announcement.Soon(3), decide(aos - 160_000L, AlertStage.LEAD).announcement)
        // 20 seconds before AOS rounds to 0, never negative
        assertEquals(Announcement.Soon(0), decide(aos - 20_000L, AlertStage.LEAD).announcement)
    }

    @Test
    fun `the AOS alarm shows the up notification and says it is rising`() {
        val d = decide(aos, AlertStage.AOS)
        assertEquals(AlertStage.AOS, d.notificationStage)
        assertFalse(d.removeAlert)
        assertEquals(Announcement.Start, d.announcement)
    }

    @Test
    fun `a lead alarm arriving during the pass shows and announces AOS, not the lead`() {
        // Doze delayed the lead alarm past AOS: the pass is under way, so say so.
        val d = decide(aos + 60_000L, AlertStage.LEAD)
        assertEquals(AlertStage.AOS, d.notificationStage)
        assertEquals(Announcement.Start, d.announcement)
        assertFalse(d.removeAlert)
    }

    @Test
    fun `the LOS alarm clears the notification, drops the alert and says the pass is complete`() {
        for (now in listOf(los, los + 1, los + 30_000L)) {
            val d = decide(now, AlertStage.LOS)
            assertNull("notification at $now", d.notificationStage)
            assertTrue("removed at $now", d.removeAlert)
            assertEquals("announcement at $now", Announcement.End, d.announcement)
        }
    }

    @Test
    fun `an AOS or lead alarm arriving after LOS clears quietly`() {
        for (fired in listOf(AlertStage.AOS, AlertStage.LEAD, null)) {
            val d = decide(los + 60_000L, fired)
            assertNull("notification for $fired", d.notificationStage)
            assertTrue("removed for $fired", d.removeAlert)
            assertNull("nothing spoken for $fired", d.announcement)
        }
    }

    @Test
    fun `an alarm firing before the lead time shows the lead stage with the real minutes`() {
        // As AlertTiming.stageNow documents: before the lead time LEAD is the first stage. An early alarm is a clock
        // oddity; the notification is harmless and the minutes are right.
        val d = decide(aos - 8 * 60_000L, AlertStage.LEAD)
        assertEquals(AlertStage.LEAD, d.notificationStage)
        assertFalse(d.removeAlert)
        assertEquals(Announcement.Soon(8), d.announcement)
    }

    @Test
    fun `an unknown fired stage is treated like a missing one`() {
        // The receiver passes null when the intent's stage extra is absent or unrecognised.
        assertEquals(Announcement.Start, decide(aos + 1, null).announcement)
        assertNull(decide(los, null).announcement)
    }
}
