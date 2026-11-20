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
        assertFalse(s.compassEnabled.value)
        assertEquals(PointingAxis.TOP_EDGE, s.pointingAxis.value)
        assertTrue(s.keepScreenOn.value)
        assertEquals(ThemeMode.SYSTEM, s.themeMode.value)
        assertFalse(s.autoRedNight.value)
        assertFalse(s.centreMapOnHome.value)
        assertEquals(12, s.overviewHours.value)
        assertEquals(0, s.overviewMinElevation.value)
        assertFalse(s.setupFinished.value)
        assertEquals(emptyList<SavedStation>(), s.plannerStations.value)
        assertEquals("", s.plannerStationId.value)
        assertEquals(0, s.plannerMinElevation.value)
        assertEquals(24, s.plannerHours.value)
        assertFalse(s.plannerAllSatellites.value)
    }

    @Test
    fun mySatellitesReadsAsTheIssWhenNothingIsSavedAndWritesNothing() {
        assertEquals(setOf(25544), settings().mySatellites.value)
        assertFalse(store.map.containsKey("mySatellites"))
    }
    @Test
    fun mySatellitesIsReadBackExactly() {
        settings().mySatellites.value = setOf(44909, 7530)
        assertEquals("7530,44909", store.map["mySatellites"])
        assertEquals(setOf(7530, 44909), settings().mySatellites.value)
    }
    @Test
    fun anEmptyMySatellitesListStaysEmpty() {
        settings().mySatellites.value = emptySet()
        assertEquals(emptySet<Int>(), settings().mySatellites.value)
    }
    @Test
    fun junkInASavedMySatellitesListIsIgnored() {
        store.map["mySatellites"] = "7530,abc,,0,7530"
        assertEquals(setOf(7530), settings().mySatellites.value)
    }
    @Test
    fun aMySatellitesValueOfTheWrongTypeReadsAsTheIss() {
        store.map["mySatellites"] = 5
        assertEquals(setOf(25544), settings().mySatellites.value)
    }
    @Test
    fun mySatellitesChosenIsFalseUntilAListIsSavedThenStaysTrue() {
        val a = settings()
        assertFalse(a.mySatellitesChosen.value)
        a.mySatellites.value = emptySet() // an empty list counts as chosen
        assertTrue(a.mySatellitesChosen.value)
        assertTrue(settings().mySatellitesChosen.value)
    }
    @Test
    fun theChoosePromptIsNotDismissedUntilNotNowAndStaysDismissed() {
        val a = settings()
        assertFalse(a.choosePromptDismissed.value)
        a.choosePromptDismissed.value = true
        assertTrue(settings().choosePromptDismissed.value)
        assertFalse(settings().mySatellitesChosen.value) // Not now does not choose a list
        assertEquals(setOf(25544), settings().mySatellites.value)
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
        a.compassEnabled.value = true
        a.pointingAxis.value = PointingAxis.BACK
        a.keepScreenOn.value = false
        a.themeMode.value = ThemeMode.RED_NIGHT
        a.autoRedNight.value = true
        a.centreMapOnHome.value = true
        a.overviewHours.value = 24
        a.overviewMinElevation.value = 20
        a.setupFinished.value = true
        val kansas = SavedStation("id-1", "W0KC", "39.5", "-95", "EM29mm")
        a.plannerStations.value = listOf(kansas)
        a.plannerStationId.value = "id-1"
        a.plannerMinElevation.value = 10
        a.plannerHours.value = 48
        a.plannerAllSatellites.value = true

        val b = settings()
        assertEquals("52.25", b.homeLat.value)
        assertEquals(KepsSource.AMATEUR_AMSAT, b.kepsSource.value)
        assertFalse(b.autoKepsRefresh.value)
        assertEquals(25544, b.selectedCatnum.value)
        assertEquals(15, b.alertLeadMinutes.value)
        assertTrue(b.spokenAlerts.value)
        assertTrue(b.compassEnabled.value)
        assertEquals(PointingAxis.BACK, b.pointingAxis.value)
        assertFalse(b.keepScreenOn.value)
        assertEquals(ThemeMode.RED_NIGHT, b.themeMode.value)
        assertTrue(b.autoRedNight.value)
        assertTrue(b.centreMapOnHome.value)
        assertEquals(24, b.overviewHours.value)
        assertEquals(20, b.overviewMinElevation.value)
        assertTrue(b.setupFinished.value)
        assertEquals(listOf(kansas), b.plannerStations.value)
        assertEquals("id-1", b.plannerStationId.value)
        assertEquals(10, b.plannerMinElevation.value)
        assertEquals(48, b.plannerHours.value)
        assertTrue(b.plannerAllSatellites.value)
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
        store.map["plannerMinElevation"] = 7
        store.map["plannerHours"] = 72 // the planner offers at most 48
        store.map["plannerStations"] = "{damaged"
        store.map["plannerAllSatellites"] = "yes" // wrong type
        val s = settings()
        assertEquals(5, s.alertLeadMinutes.value)
        assertEquals(12, s.overviewHours.value)
        assertEquals(0, s.overviewMinElevation.value)
        assertEquals(ThemeMode.SYSTEM, s.themeMode.value)
        assertEquals(PointingAxis.TOP_EDGE, s.pointingAxis.value)
        assertEquals(KepsSource.DEFAULT, s.kepsSource.value)
        assertEquals(0, s.selectedCatnum.value)
        assertFalse(s.compassEnabled.value)
        assertEquals(0, s.plannerMinElevation.value)
        assertEquals(24, s.plannerHours.value)
        assertEquals(emptyList<SavedStation>(), s.plannerStations.value)
        assertFalse(s.plannerAllSatellites.value)
    }

    @Test
    fun settersRejectValuesOutsideTheAllowedChoices() {
        val s = settings()
        for (action in listOf<() -> Unit>(
            { s.alertLeadMinutes.value = 7 },
            { s.overviewHours.value = 3 },
            { s.overviewMinElevation.value = 5 },
            { s.plannerMinElevation.value = 25 },
            { s.plannerHours.value = 72 },
            { s.plannerStations.value = List(SavedStation.MAX_STATIONS + 1) { SavedStation("id-$it", "", "1", "2", "") } },
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
