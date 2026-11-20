// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uk.me.g4dpz.HamSatDroid.data.SavedStation
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.ContactPlannerCalculator
import uk.me.g4dpz.HamSatDroid.domain.ElementAge
import uk.me.g4dpz.HamSatDroid.testsupport.MemoryStore
import uk.me.g4dpz.satellite.TLE
import java.time.Instant

/**
 * The contact planner's view model, with fixed orbital elements and a fixed clock. Everything runs on one unconfined
 * test dispatcher, so a search has finished by the time the call that started it returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContactPlannerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val settings = SettingsRepository(MemoryStore())

    private fun tle(resource: String): TLE =
        checkNotNull(javaClass.getResourceAsStream("/$resource")) { "missing test resource $resource" }
            .use { TLE.importSat(it) }.first()

    private val rs44 = tle("rs44_20261114.tle")
    private val iss = tle("iss_20261114.tle")
    private val ao7 = tle("ao7_20261114.tle")
    private val tles = MutableStateFlow(listOf(rs44, iss, ao7))
    private val selected = MutableStateFlow<Int?>(44909)

    /** 2026-11-14 14:55:24 UTC, when the RS-44 elements were current. */
    private var now = Instant.parse("2026-11-14T14:55:24Z").toEpochMilli()
    private var checkpoints = 0
    private var onCheckpoint: () -> Unit = {}
    private val logs = mutableListOf<String>()

    private val kansas = SavedStation("kc", "W0KC", "39.5", "-95", "EM29mm")
    private val paris = SavedStation("par", "", "48.85", "2.35", "JN18eu")

    private fun viewModel() = ContactPlannerViewModel(
        tles, selected, settings,
        ContactPlannerCalculator(dispatcher) {
            checkpoints++
            onCheckpoint()
        },
        { now }, dispatcher, log = { logs += it },
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings.homeLat.value = "52.4375"
        settings.homeLon.value = "-2.04167"
        settings.homeLocator.value = "IO82xk"
        settings.plannerStations.value = listOf(kansas, paris)
        settings.plannerStationId.value = "kc"
        settings.mySatellites.value = setOf(44909, 25544, 7530) // all three, as the older tests expect
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- opening ----

    @Test
    fun `nothing is searched until the screen starts`() {
        val vm = viewModel()
        assertEquals(0, checkpoints)
        assertTrue(vm.state.value.windows.isEmpty())
        assertFalse(vm.state.value.searching)
        assertFalse("not searched yet is not the same as no windows", vm.state.value.done)
        assertEquals(kansas, vm.state.value.chosen)
        assertEquals("IO82xk", vm.state.value.homeLocator)
    }

    @Test
    fun `starting with a station chosen searches the selected satellite for the default 24 hours`() {
        val vm = viewModel()
        vm.onStart()
        val s = vm.state.value
        assertEquals(24, s.hours)
        assertFalse(s.allSatellites)
        assertFalse(s.searching)
        assertNull(s.progress)
        assertTrue(s.done)
        assertEquals(1, s.windows.size) // RS-44 over the UK and Kansas City: Saturday 18:01 UTC in the next 24 hours
        assertEquals(44909, s.windows.single().catnum)
        assertEquals("RS-44 & BREEZE-KM R/B", s.selectedName)
        assertEquals(6818.0, s.distanceKm!!, 5.0)
        assertTrue(s.locationSet)
        assertEquals(1, logs.size)
    }

    @Test
    fun `no search while the home location is not set`() {
        settings.homeLat.value = "0"
        settings.homeLon.value = "0"
        val vm = viewModel()
        vm.onStart()
        assertFalse(vm.state.value.locationSet)
        assertNull(vm.state.value.distanceKm)
        assertEquals(0, checkpoints)
        assertTrue(vm.state.value.windows.isEmpty())
        assertFalse(vm.state.value.searching)
        assertTrue("nothing to search is a finished answer", vm.state.value.done)
    }

    @Test
    fun `no search without a chosen station, also when the chosen one was deleted`() {
        settings.plannerStationId.value = ""
        val vm = viewModel()
        vm.onStart()
        assertNull(vm.state.value.chosen)
        assertEquals(0, checkpoints)
        settings.plannerStationId.value = "gone" // names no saved station
        assertNull(vm.state.value.chosen)
        assertEquals(0, checkpoints)
    }

    @Test
    fun `no search without a selected satellite unless all satellites are searched`() {
        selected.value = null
        val vm = viewModel()
        vm.onStart()
        assertEquals(0, checkpoints)
        assertNull(vm.state.value.selectedName)
        vm.setAllSatellites(true)
        assertTrue("all satellites are searched whatever is selected", checkpoints > 0)
    }

    // ---- what restarts the search ----

    @Test
    fun `each choice restarts the search and is remembered`() {
        val vm = viewModel()
        vm.onStart()
        assertEquals(1, vm.state.value.windows.size)
        vm.setHours(48)
        assertEquals(48, settings.plannerHours.value)
        assertEquals(2, vm.state.value.windows.size) // the Sunday window is in range now
        vm.setMinElevation(5)
        assertEquals(5, settings.plannerMinElevation.value)
        assertEquals(5, vm.state.value.minElevation)
        assertTrue("no window reaches 5 degrees at both stations", vm.state.value.windows.isEmpty())
        vm.setMinElevation(0)
        vm.chooseStation("par")
        assertEquals("par", settings.plannerStationId.value)
        assertEquals(paris, vm.state.value.chosen)
        assertTrue("Paris is about 500 km away, so most passes are mutual", vm.state.value.windows.size > 4)
        assertEquals(505.0, vm.state.value.distanceKm!!, 5.0)
    }

    @Test
    fun `changing the selected satellite or the home position restarts the search`() {
        val vm = viewModel()
        settings.plannerHours.value = 48
        vm.onStart()
        assertEquals(setOf(44909), vm.state.value.windows.map { it.catnum }.toSet())
        selected.value = 7530
        assertEquals("AO-7 now", setOf(7530), vm.state.value.windows.map { it.catnum }.toSet())
        assertEquals("OSCAR 7 (AO-7)", vm.state.value.selectedName)
        val before = checkpoints
        settings.homeLat.value = "51.5"
        assertTrue("a different home position searches again", checkpoints > before)
    }

    @Test
    fun `the selected satellite does not matter while all satellites are searched`() {
        settings.plannerAllSatellites.value = true
        val vm = viewModel()
        vm.onStart()
        val before = checkpoints
        selected.value = 7530
        assertEquals(before, checkpoints)
        assertEquals(7530, vm.state.value.selectedCatnum) // still shown
    }

    @Test
    fun `new orbital elements restart the search but the same elements do not`() {
        val vm = viewModel()
        vm.onStart()
        val before = checkpoints
        tles.value = listOf(rs44, iss, ao7) // a new list with the same elements
        assertEquals(before, checkpoints)
        tles.value = listOf(rs44)
        assertTrue("different elements search again", checkpoints > before)
    }

    @Test
    fun `an edit of the chosen station searches again, an edit of another does not`() {
        val vm = viewModel()
        vm.onStart()
        val before = checkpoints
        settings.plannerStations.value = listOf(kansas, paris.copy(name = "Paris"))
        assertEquals(before, checkpoints)
        assertEquals("Paris", vm.state.value.stations[1].name)
        settings.plannerStations.value = listOf(kansas.copy(latitude = "38.0"), paris)
        assertTrue(checkpoints > before)
        assertEquals("38.0", vm.state.value.chosen!!.latitude)
    }

    // ---- the screen starting and stopping ----

    @Test
    fun `finished results are kept when the screen stops and starts again with nothing changed`() {
        val vm = viewModel()
        vm.onStart()
        val results = vm.state.value.windows
        val done = checkpoints
        vm.onStop()
        assertEquals("stopping keeps finished results", results, vm.state.value.windows)
        vm.onStart()
        assertEquals("no new search", done, checkpoints)
        assertEquals(results, vm.state.value.windows)
    }

    @Test
    fun `an unfinished search stops with the screen and starts again`() {
        val vm = viewModel()
        onCheckpoint = { if (checkpoints == 3) vm.onStop() } // the screen goes away part-way through
        vm.onStart()
        assertEquals("the work stopped at the checkpoint after the stop", 3, checkpoints)
        assertFalse(vm.state.value.searching)
        assertFalse("an unfinished search is not an answer", vm.state.value.done)
        onCheckpoint = {}
        vm.onStart()
        assertTrue("a full search ran", checkpoints > 3)
        assertTrue(vm.state.value.done)
        assertEquals(1, vm.state.value.windows.size)
        assertFalse(vm.state.value.searching)
    }

    @Test
    fun `results for older choices are cleared at once, also while the screen is stopped`() {
        val vm = viewModel()
        vm.onStart()
        assertEquals(1, vm.state.value.windows.size)
        vm.onStop()
        val done = checkpoints
        settings.plannerMinElevation.value = 5
        assertTrue("old windows are not shown as current", vm.state.value.windows.isEmpty())
        assertFalse("and the empty list is not an answer", vm.state.value.done)
        assertEquals("no search while stopped", done, checkpoints)
        settings.plannerMinElevation.value = 0
        vm.onStart()
        assertEquals(1, vm.state.value.windows.size)
        assertTrue(checkpoints > done)
    }

    @Test
    fun `calculate again searches again from now with the same choices`() {
        val vm = viewModel()
        vm.onStart()
        val done = checkpoints
        now += 3_600_000L // an hour later
        vm.refresh()
        assertTrue(checkpoints > done)
        assertEquals(2, logs.size)
    }

    // ---- all satellites ----

    @Test
    fun `searching all satellites shows progress and merges the windows by start`() {
        settings.plannerAllSatellites.value = true
        settings.plannerStationId.value = "par"
        val vm = viewModel()
        vm.onStart()
        val s = vm.state.value
        assertFalse(s.searching)
        assertNull("progress goes away when done", s.progress)
        assertEquals(s.windows.sortedBy { it.start }, s.windows)
        assertTrue("windows of more than one satellite: ${s.windows.map { it.catnum }.toSet()}", s.windows.map { it.catnum }.toSet().size >= 2)
        assertNull("no stale warning when searching all", s.staleElements)
    }

    @Test
    fun `progress counts the satellites while searching all of them`() {
        settings.plannerAllSatellites.value = true
        val seen = mutableListOf<Pair<Int, Int>?>()
        val vm = viewModel()
        val collector = vm.state
        onCheckpoint = { collector.value.progress.let { if (seen.lastOrNull() != it) seen += it } }
        vm.onStart()
        assertTrue("progress was shown while searching: $seen", seen.any { it != null && it.second == 3 })
        assertNull(vm.state.value.progress)
    }

    @Test
    fun `my satellites searches only the chosen ones and counts those left out`() {
        settings.plannerAllSatellites.value = true
        settings.plannerStationId.value = "par"
        settings.mySatellites.value = setOf(25544, 99999)
        val vm = viewModel()
        vm.onStart()
        val s = vm.state.value
        assertEquals(setOf(25544), s.windows.map { it.catnum }.toSet())
        assertEquals(2, s.myCount)
        assertEquals(1, s.myMissing)
    }
    @Test
    fun `an empty list searches nothing`() {
        settings.plannerAllSatellites.value = true
        settings.mySatellites.value = emptySet()
        val vm = viewModel()
        vm.onStart()
        assertEquals(0, checkpoints)
        assertTrue(vm.state.value.windows.isEmpty())
        assertEquals(0, vm.state.value.myCount)
        assertTrue(vm.state.value.done)
    }
    @Test
    fun `a change of the list restarts a my satellites search and drops the old results`() {
        settings.plannerAllSatellites.value = true
        settings.plannerStationId.value = "par"
        val vm = viewModel()
        vm.onStart()
        assertTrue(vm.state.value.windows.any { it.catnum == 44909 })
        settings.mySatellites.value = setOf(7530)
        assertEquals(setOf(7530), vm.state.value.windows.map { it.catnum }.toSet())
    }
    @Test
    fun `a change of the list during a my satellites search never shows old results under the new list`() {
        settings.plannerAllSatellites.value = true
        settings.plannerStationId.value = "par"
        var changed = false
        val leaked = mutableListOf<Set<Int>>()
        lateinit var vm: ContactPlannerViewModel
        // Change the list at the first checkpoint, while the search over all three satellites is running; at every later
        // checkpoint, a state for the new list must hold no window of the old one.
        onCheckpoint = {
            if (!changed) {
                changed = true
                settings.mySatellites.value = setOf(7530)
            }
            val st = vm.state.value
            if (st.myCount == 1 && st.windows.any { it.catnum != 7530 }) leaked += st.windows.map { it.catnum }.toSet()
        }
        vm = viewModel()
        vm.onStart()
        assertTrue(changed)
        assertTrue("old results shown under the new list: $leaked", leaked.isEmpty())
        val s = vm.state.value
        assertTrue("windows for AO-7", s.windows.isNotEmpty())
        assertEquals(setOf(7530), s.windows.map { it.catnum }.toSet())
        assertTrue(s.done)
        assertFalse(s.searching)
    }
    @Test
    fun `a change of the list does not touch a selected satellite search`() {
        val vm = viewModel()
        vm.onStart()
        val windows = vm.state.value.windows
        val before = checkpoints
        settings.mySatellites.value = setOf(7530)
        assertEquals(before, checkpoints)
        assertEquals(windows, vm.state.value.windows)
        assertEquals(1, vm.state.value.myCount) // the count shown is up to date
    }
    // ---- warnings ----

    @Test
    fun `old elements for the selected satellite give a stale warning`() {
        val vm = viewModel()
        vm.onStart()
        assertNull("elements from today", vm.state.value.staleElements)
        now = Instant.parse("2027-01-20T00:00:00Z").toEpochMilli()
        vm.refresh()
        val stale = vm.state.value.staleElements
        assertNotNull(stale)
        assertEquals("RS-44 & BREEZE-KM R/B", stale!!.satelliteName)
        assertEquals(ElementAge.staleDays(ElementAge.epochMillis(rs44), now), stale.days)
        assertTrue(stale.days in 60..70)
    }

    @Test
    fun `the stale warning follows the clock while results are kept`() {
        val vm = viewModel()
        vm.onStart()
        assertNull(vm.state.value.staleElements)
        vm.onStop()
        val done = checkpoints
        now = Instant.parse("2026-12-20T00:00:00Z").toEpochMilli() // 36 days after the epoch: now stale
        vm.onStart()
        assertEquals("the results were kept, not calculated again", done, checkpoints)
        val first = vm.state.value.staleElements
        assertNotNull("the warning appears without a new search", first)
        assertEquals(ElementAge.staleDays(ElementAge.epochMillis(rs44), now), first!!.days)
        now = Instant.parse("2026-12-25T00:00:00Z").toEpochMilli()
        dispatcher.scheduler.advanceTimeBy(ContactPlannerViewModel.TICK_MILLIS + 1)
        dispatcher.scheduler.runCurrent()
        assertEquals("the minute tick keeps the day count current", first.days + 5, vm.state.value.staleElements!!.days)
        assertEquals(done, checkpoints)
        vm.onStop()
    }

    @Test
    fun `no state is ever published with the new selection and the old results`() {
        settings.plannerHours.value = 48
        val vm = viewModel()
        vm.onStart()
        now = Instant.parse("2027-01-20T00:00:00Z").toEpochMilli()
        vm.refresh() // an old satellite with a stale warning, results and an open detail
        vm.toggleDetail(vm.state.value.windows.first())
        assertNotNull(vm.state.value.staleElements)
        assertNotNull(vm.state.value.expanded)
        val seen = mutableListOf<PlannerUiState>()
        val collector = CoroutineScope(dispatcher).launch { vm.state.collect { seen += it } }
        selected.value = 7530
        collector.cancel()
        assertTrue("states were published: ${seen.size}", seen.size >= 2)
        for (s in seen.filter { it.selectedCatnum == 7530 }) {
            assertTrue("old windows under the new selection: ${s.windows.map { it.catnum }}", s.windows.none { it.catnum == 44909 })
            assertNull("old warning under the new selection", s.staleElements?.takeIf { it.satelliteName.startsWith("RS-44") })
            assertNull("old detail under the new selection", s.expanded?.takeIf { it.first == 44909 })
        }
    }

    // ---- windows that have ended ----

    @Test
    fun `windows that have ended are dropped`() {
        val vm = viewModel()
        vm.onStart()
        val w = vm.state.value.windows.single()
        now = w.end.toEpochMilli() - 1_000 // one second before it ends
        vm.dropEnded()
        assertEquals(1, vm.state.value.windows.size)
        now = w.end.toEpochMilli() + 1_000
        vm.dropEnded()
        assertTrue(vm.state.value.windows.isEmpty())
    }

    @Test
    fun `the minute tick drops ended windows while the screen is on`() {
        val vm = viewModel()
        vm.onStart()
        assertEquals(1, vm.state.value.windows.size)
        now = vm.state.value.windows.single().end.toEpochMilli() + 1_000
        dispatcher.scheduler.advanceTimeBy(ContactPlannerViewModel.TICK_MILLIS + 1)
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.windows.isEmpty())
        vm.onStop()
    }

    // ---- the detail of a window ----

    @Test
    fun `opening a window works out its timeline, opening it again closes it`() {
        settings.plannerHours.value = 48
        val vm = viewModel()
        vm.onStart()
        val sunday = vm.state.value.windows[1]
        vm.toggleDetail(sunday)
        var s = vm.state.value
        assertEquals(sunday.catnum to sunday.start, s.expanded)
        assertEquals(sunday.start, s.timeline.first().time)
        assertEquals(sunday.end, s.timeline.last().time)
        assertTrue(s.timeline.any { it.time == sunday.best.time })
        vm.toggleDetail(vm.state.value.windows[0]) // another window replaces it
        s = vm.state.value
        assertEquals(vm.state.value.windows[0].start, s.expanded!!.second)
        assertEquals(s.windows[0].start, s.timeline.first().time)
        vm.toggleDetail(vm.state.value.windows[0])
        assertNull(vm.state.value.expanded)
        assertTrue(vm.state.value.timeline.isEmpty())
    }

    @Test
    fun `a change of choices or a new search closes the open detail`() {
        val vm = viewModel()
        vm.onStart()
        vm.toggleDetail(vm.state.value.windows.single())
        assertNotNull(vm.state.value.expanded)
        vm.setHours(48)
        assertNull(vm.state.value.expanded)
        assertTrue(vm.state.value.timeline.isEmpty())
        vm.toggleDetail(vm.state.value.windows.first())
        vm.refresh()
        assertNull(vm.state.value.expanded)
    }

    @Test
    fun `the detail closes when its window has ended`() {
        val vm = viewModel()
        vm.onStart()
        val w = vm.state.value.windows.single()
        vm.toggleDetail(w)
        now = w.end.toEpochMilli() + 1_000
        vm.dropEnded()
        assertNull(vm.state.value.expanded)
    }
}
