// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.aim.PointingAxis
import uk.me.g4dpz.HamSatDroid.night.ThemeMode

class SettingsRepositoryTest {

    /** In-memory store; typed values are kept as Any so wrong-type reads can be simulated. */
    private class MemoryStore : KeyValueStore {
        val map = HashMap<String, Any>()
        override fun getString(key: String, default: String) = map[key] as? String ?: default
        override fun putString(key: String, value: String) { map[key] = value }
        override fun getBoolean(key: String, default: Boolean) = map[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { map[key] = value }
        override fun getInt(key: String, default: Int) = map[key] as? Int ?: default
        override fun putInt(key: String, value: Int) { map[key] = value }
    }

    private val store = MemoryStore()
    private fun settings() = SettingsRepository(store)

    @Test
    fun defaultsMatchTheSpec() {
        val s = settings()
        assertEquals("0", s.homeLat.value)
        assertEquals("0", s.homeLon.value)
        assertEquals("JJ00aa", s.homeLocator.value)
        assertEquals(KepsSource.AMATEUR_CELESTRAK, s.kepsSource.value)
        assertTrue(s.autoKepsRefresh.value)
        assertEquals(0, s.selectedCatnum.value)
        assertEquals(5, s.alertLeadMinutes.value)
        assertFalse(s.spokenAlerts.value)
        assertTrue(s.compassEnabled.value)
        assertEquals(PointingAxis.TOP_EDGE, s.pointingAxis.value)
        assertTrue(s.keepScreenOn.value)
        assertEquals(ThemeMode.SYSTEM, s.themeMode.value)
        assertFalse(s.autoRedNight.value)
        assertEquals(12, s.overviewHours.value)
        assertEquals(0, s.overviewMinElevation.value)
    }

    @Test
    fun existingHomeLocationFromAnOlderVersionIsRead() {
        // Values exactly as the original app stored them under the same keys.
        store.map["homeLat"] = "51.5"
        store.map["homeLon"] = "-0,1"
        store.map["homeLocator"] = "IO91wm"
        val s = settings()
        assertEquals("51.5", s.homeLat.value)
        assertEquals("-0,1", s.homeLon.value)
        assertEquals("IO91wm", s.homeLocator.value)
        assertEquals(51.5, LocationText.toDegrees(s.homeLat.value)!!, 0.0)
        assertEquals(-0.1, LocationText.toDegrees(s.homeLon.value)!!, 0.0)
    }

    @Test
    fun valuesPersistAcrossInstances() {
        val a = settings()
        a.homeLat.value = "52.25"
        a.kepsSource.value = KepsSource.AMATEUR_AMSAT
        a.autoKepsRefresh.value = false
        a.selectedCatnum.value = 25544
        a.alertLeadMinutes.value = 15
        a.spokenAlerts.value = true
        a.compassEnabled.value = false
        a.pointingAxis.value = PointingAxis.BACK
        a.keepScreenOn.value = false
        a.themeMode.value = ThemeMode.RED_NIGHT
        a.autoRedNight.value = true
        a.overviewHours.value = 24
        a.overviewMinElevation.value = 20

        val b = settings()
        assertEquals("52.25", b.homeLat.value)
        assertEquals(KepsSource.AMATEUR_AMSAT, b.kepsSource.value)
        assertFalse(b.autoKepsRefresh.value)
        assertEquals(25544, b.selectedCatnum.value)
        assertEquals(15, b.alertLeadMinutes.value)
        assertTrue(b.spokenAlerts.value)
        assertFalse(b.compassEnabled.value)
        assertEquals(PointingAxis.BACK, b.pointingAxis.value)
        assertFalse(b.keepScreenOn.value)
        assertEquals(ThemeMode.RED_NIGHT, b.themeMode.value)
        assertTrue(b.autoRedNight.value)
        assertEquals(24, b.overviewHours.value)
        assertEquals(20, b.overviewMinElevation.value)
    }

    @Test
    fun theStateFlowUpdatesWhenTheValueChanges() {
        val s = settings()
        assertEquals(5, s.alertLeadMinutes.state.value)
        s.alertLeadMinutes.value = 30
        assertEquals(30, s.alertLeadMinutes.state.value)
        assertEquals(30, store.map["alertLeadMinutes"])
    }

    @Test
    fun invalidStoredValuesFallBackToDefaults() {
        store.map["alertLeadMinutes"] = 7
        store.map["overviewHours"] = 99
        store.map["overviewMinElevation"] = 45
        store.map["themeMode"] = "NEON"
        store.map["pointingAxis"] = "SIDEWAYS"
        store.map["kepsSource"] = "NOT_A_SOURCE"
        store.map["selectedCatnum"] = -5
        store.map["compassEnabled"] = "yes" // wrong type
        val s = settings()
        assertEquals(5, s.alertLeadMinutes.value)
        assertEquals(12, s.overviewHours.value)
        assertEquals(0, s.overviewMinElevation.value)
        assertEquals(ThemeMode.SYSTEM, s.themeMode.value)
        assertEquals(PointingAxis.TOP_EDGE, s.pointingAxis.value)
        assertEquals(KepsSource.DEFAULT, s.kepsSource.value)
        assertEquals(0, s.selectedCatnum.value)
        assertTrue(s.compassEnabled.value)
    }

    @Test
    fun settersRejectValuesOutsideTheAllowedChoices() {
        val s = settings()
        for (action in listOf<() -> Unit>(
            { s.alertLeadMinutes.value = 7 },
            { s.overviewHours.value = 3 },
            { s.overviewMinElevation.value = 5 },
        )) {
            try {
                action()
                fail("expected IllegalArgumentException")
            } catch (expected: IllegalArgumentException) {
                // fine
            }
        }
        assertEquals(5, s.alertLeadMinutes.value) // unchanged
        assertEquals(12, s.overviewHours.value)
        assertFalse(store.map.containsKey("alertLeadMinutes"))
    }

    @Test
    fun locationTextIsParsedWithoutRegardToLocale() {
        assertEquals(51.5, LocationText.toDegrees("51.5")!!, 0.0)
        assertEquals(51.5, LocationText.toDegrees("51,5")!!, 0.0)
        assertEquals(-79.0, LocationText.toDegrees(" -79 ")!!, 0.0)
        assertNull(LocationText.toDegrees(""))
        assertNull(LocationText.toDegrees("abc"))
        assertNull(LocationText.toDegrees("NaN"))
        assertNull(LocationText.toDegrees("Infinity"))
        assertEquals("51.5", LocationText.fromDegrees(51.5))
        assertEquals(-0.1, LocationText.toDegrees(LocationText.fromDegrees(-0.1))!!, 0.0)
    }

    @Test
    fun everyChoiceListContainsItsDefault() {
        assertTrue(SettingsRepository.DEFAULT_ALERT_LEAD_MINUTES in SettingsRepository.ALERT_LEAD_CHOICES)
        assertTrue(SettingsRepository.DEFAULT_OVERVIEW_HOURS in SettingsRepository.OVERVIEW_HOUR_CHOICES)
        assertTrue(0 in SettingsRepository.OVERVIEW_MIN_ELEVATION_CHOICES)
    }
}
