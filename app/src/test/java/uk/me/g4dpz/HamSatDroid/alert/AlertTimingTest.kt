// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertTimingTest {

    private val aos = 1_000_000_000_000L
    private val los = aos + 10 * 60_000L // 10 minutes later
    private val alert = PassAlert(25544, "ISS", aos, los, 45.0, 114, 339)

    private fun timing(now: Long, lead: Int = 5) = AlertTiming(alert, lead, now)

    @Test
    fun `lead time is AOS minus the lead minutes`() {
        assertEquals(aos - 5 * 60_000L, timing(0).leadMillis)
        assertEquals(aos - 15 * 60_000L, timing(0, lead = 15).leadMillis)
    }

    @Test
    fun `before the pass all three stages are scheduled in order`() {
        val triggers = timing(aos - 60 * 60_000L).futureTriggers()
        assertEquals(listOf(AlertStage.LEAD, AlertStage.AOS, AlertStage.LOS), triggers.map { it.first })
        assertEquals(listOf(aos - 5 * 60_000L, aos, los), triggers.map { it.second })
    }

    @Test
    fun `past stages are dropped`() {
        // between lead and AOS: only AOS and LOS remain
        val betweenLeadAndAos = timing(aos - 2 * 60_000L).futureTriggers()
        assertEquals(listOf(AlertStage.AOS, AlertStage.LOS), betweenLeadAndAos.map { it.first })
        // during the pass: only LOS remains
        assertEquals(listOf(AlertStage.LOS), timing(aos + 60_000L).futureTriggers().map { it.first })
        // after the pass: nothing
        assertTrue(timing(los + 1).futureTriggers().isEmpty())
    }

    @Test
    fun `stageNow reflects the moment, so a late alert shows the right state`() {
        assertEquals(AlertStage.LEAD, timing(aos - 3 * 60_000L).stageNow())
        assertEquals(AlertStage.AOS, timing(aos + 60_000L).stageNow())
        assertEquals(AlertStage.AOS, timing(los - 1).stageNow())
        assertNull(timing(los).stageNow())
        assertNull(timing(los + 10_000L).stageNow())
    }

    @Test
    fun `hasFuture is true until the pass ends`() {
        assertTrue(timing(aos - 60_000L).hasFuture())
        assertTrue(timing(aos + 60_000L).hasFuture())
        assertFalse(timing(los).hasFuture())
    }
}
