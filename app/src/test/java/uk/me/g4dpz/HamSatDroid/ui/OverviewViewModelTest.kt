// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.OverviewCalculator
import uk.me.g4dpz.HamSatDroid.testsupport.MemoryStore
import uk.me.g4dpz.satellite.TLE
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = SettingsRepository(MemoryStore())
    private val all = File("src/main/res/raw/nasabare.txt").inputStream().use { TLE.importSat(it) }
    private val tles = MutableStateFlow(all)
    private val now = 1264723200000L // 2010-01-29 00:00 UTC, near the bundled keps' epoch
    private val completed = mutableListOf<String>()

    private fun viewModel() = OverviewViewModel(
        tles, settings, OverviewCalculator(dispatcher), { now }, dispatcher, log = { completed += it },
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings.homeLat.value = "51.5"
        settings.homeLon.value = "-0.1"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `calculates every satellite on creation with the default window`() {
        val vm = viewModel()
        val s = vm.state.value
        assertEquals(12, s.hours)
        assertEquals(0, s.minElevation)
        assertFalse(s.calculating)
        assertEquals(all.size, s.total)
        assertEquals(all.size, s.done)
        assertTrue("most satellites have results", s.results.size > all.size / 2)
        assertNotNull(s.elapsedMillis)
        assertEquals(1, completed.size)
    }

    @Test
    fun `changing the window recalculates and is remembered`() {
        val vm = viewModel()
        vm.setHours(24)
        assertEquals(24, vm.state.value.hours)
        assertEquals(24, settings.overviewHours.value)
        assertEquals(2, completed.size)
        assertTrue(completed.last().contains("24 h"))
    }

    @Test
    fun `the elevation filter is remembered but does not recalculate`() {
        val vm = viewModel()
        vm.setMinElevation(20)
        assertEquals(20, vm.state.value.minElevation)
        assertEquals(20, settings.overviewMinElevation.value)
        assertEquals(1, completed.size)
    }

    @Test
    fun `new elements or a new location recalculate, the same inputs do not`() {
        viewModel()
        tles.value = all.take(10)
        assertEquals(2, completed.size)
        settings.homeLat.value = "40.0"
        assertEquals(3, completed.size)
        settings.homeLat.value = "40.0" // unchanged
        assertEquals(3, completed.size)
    }

    @Test
    fun `refresh calculates again`() {
        val vm = viewModel()
        vm.refresh()
        assertEquals(2, completed.size)
    }

    @Test
    fun `an unset home location calculates nothing`() {
        settings.homeLat.value = "0"
        settings.homeLon.value = "0"
        val vm = viewModel()
        assertFalse(vm.state.value.locationValid)
        assertTrue(vm.state.value.results.isEmpty())
        assertEquals(0, completed.size)
        settings.homeLat.value = "51.5"
        assertTrue(vm.state.value.locationValid)
        assertEquals(1, completed.size)
    }

    @Test
    fun `an invalid home location calculates nothing`() {
        settings.homeLat.value = "north"
        val vm = viewModel()
        val s = vm.state.value
        assertFalse(s.locationValid)
        assertTrue(s.results.isEmpty())
        assertEquals(0, completed.size)
        settings.homeLat.value = "51.5"
        assertTrue(vm.state.value.locationValid)
        assertEquals(1, completed.size)
    }

    @Test
    fun `remembered window and filter are used`() {
        settings.overviewHours.value = 6
        settings.overviewMinElevation.value = 30
        val vm = viewModel()
        assertEquals(6, vm.state.value.hours)
        assertEquals(30, vm.state.value.minElevation)
        assertTrue(completed.single().contains("6 h"))
    }
}
