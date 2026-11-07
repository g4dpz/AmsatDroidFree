// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.me.g4dpz.HamSatDroid.data.DownloadResponse
import uk.me.g4dpz.HamSatDroid.data.FileStore
import uk.me.g4dpz.HamSatDroid.data.KepsRepository
import uk.me.g4dpz.HamSatDroid.data.KepsSource
import uk.me.g4dpz.HamSatDroid.data.PassStore
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.data.StoredPasses
import uk.me.g4dpz.HamSatDroid.domain.CalcResult
import uk.me.g4dpz.HamSatDroid.domain.PassCalculator
import uk.me.g4dpz.HamSatDroid.domain.PassHeader
import uk.me.g4dpz.HamSatDroid.testsupport.FakeDownloader
import uk.me.g4dpz.HamSatDroid.testsupport.MemoryStore
import uk.me.g4dpz.satellite.SatPassTime
import uk.me.g4dpz.satellite.TLE
import java.io.File
import java.time.Instant
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = UnconfinedTestDispatcher()
    private val downloader = FakeDownloader()
    private val memory = MemoryStore()
    private val settings = SettingsRepository(memory)

    // 2010-01-29 00:00 UTC, close to the epochs of the bundled keps, so passes are found
    private val now = 1264723200000L

    private val bundledTle get() = File("src/main/res/raw/nasabare.txt").readText()
    private val celestrakJson get() =
        javaClass.classLoader!!.getResourceAsStream("celestrak_amateur_sample.json")!!.bufferedReader().use { it.readText() }

    private fun files() = FileStore(File(tmp.root, "data"))
    private fun keps(clock: () -> Long = { now }) = KepsRepository(files(), downloader, { bundledTle }, clock)
    private fun passStore() = PassStore(files())

    private fun viewModel(calculator: PassCalculator = PassCalculator()) =
        AppViewModel(keps(), passStore(), settings, calculator, { now }, dispatcher)

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

    // ---- loading ----

    @Test
    fun loadsTheBundledSatellitesWhenTheDownloadFails() {
        val vm = viewModel() // the downloader fails: no response scripted
        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(60, s.satellites.size)
        assertTrue(s.keps!!.isBundled)
        assertEquals(s.satellites.first().catnum, s.selectedCatnum) // nothing stored: first satellite
    }

    @Test
    fun startsFromTheBundledKepsThenDownloadsOnceAndKeepsTheSelection() = runTest {
        // SO-50 is in both the bundled list and the download, but is not first in either, so keeping the selection
        // is not the same as falling back to the first satellite.
        settings.selectedCatnum.value = 27607
        downloader.next = { DownloadResponse(200, celestrakJson) }
        val vm = viewModel()

        val s = vm.state.value
        assertEquals(8, s.satellites.size)
        assertEquals(27607, s.selectedCatnum)
        assertEquals("SAUDISAT 1C (SO-50)", s.selectedSatellite!!.name)
        assertEquals("AMATEUR_CELESTRAK", s.keps!!.source)
        assertFalse(s.keps.isBundled)
        assertTrue(s.satellites.any { it.catnum == 101098 })
        assertEquals(1, downloader.requested.size)
        assertEquals(KepsSource.DEFAULT.url, downloader.requested.single())
        assertEquals(UiEvent.KepsUpdated(8), withTimeout(1000) { vm.events.first() })
    }

    @Test
    fun aSelectionThatIsNoLongerInTheListFallsBackToTheFirstSatellite() {
        settings.selectedCatnum.value = 999999
        val vm = viewModel()
        assertEquals(vm.state.value.satellites.first().catnum, vm.state.value.selectedCatnum)
    }

    // ---- selections ----

    @Test
    fun selectingASatelliteIsRememberedInSettings() {
        val vm = viewModel()
        vm.selectSatellite(25509)
        assertEquals(25509, vm.state.value.selectedCatnum)
        assertEquals(25509, settings.selectedCatnum.value)
        vm.selectSatellite(123456789) // not in the list: ignored
        assertEquals(25509, vm.state.value.selectedCatnum)
    }

    @Test
    fun hoursAheadAndStartTimeAreKept() {
        val vm = viewModel()
        assertEquals(0, vm.state.value.hoursAhead)
        vm.setHoursAhead(12)
        assertEquals(12, vm.state.value.hoursAhead)
        assertEquals(12, settings.calcHoursAhead.value)
        try {
            vm.setHoursAhead(7)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // fine
        }
        val t = Instant.ofEpochMilli(now + 3600_000)
        vm.setStartTime(t)
        assertEquals(t, vm.state.value.startTime)
        vm.setStartTime(null)
        assertNull(vm.state.value.startTime)
    }

    // ---- calculating ----

    @Test
    fun calculatesPassesAndSavesThem() {
        val vm = viewModel()
        vm.selectSatellite(7530)
        vm.setHoursAhead(24)
        vm.calculate()

        val s = vm.state.value
        assertFalse(s.calculating)
        assertTrue("expected several passes, got ${s.passes.size}", s.passes.size >= 3)
        assertEquals(s.passes.sortedBy { it.start }, s.passes)
        assertTrue(s.passes.all { it.catnum == 7530 && it.satName == "AO-07" })
        val header = s.header as PassHeader.Predictions
        assertEquals("AO-07", header.satName)
        assertEquals(51.5, header.latitude, 0.0)
        assertEquals(-0.1, header.longitude, 0.0)
        assertEquals("IO91wm", header.locator)
        assertEquals(24, header.hoursAhead)
        assertEquals(Instant.ofEpochMilli(now), header.startTime)

        val saved = passStore().load()!!
        assertEquals(7530, saved.catnum)
        assertEquals(24, saved.hoursAhead)
        assertEquals(s.passes.size, saved.passes.size)
    }

    @Test
    fun theChosenStartTimeIsUsed() {
        var usedStart: Date? = null
        val spy = object : PassCalculator() {
            override fun calculate(tle: TLE, latitude: Double, longitude: Double, start: Date, hoursAhead: Int): CalcResult {
                usedStart = start
                return CalcResult.NoPasses
            }
        }
        val vm = viewModel(spy)
        val chosen = Instant.ofEpochMilli(now + 5 * 3600_000)
        vm.setStartTime(chosen)
        vm.calculate()
        assertEquals(Date.from(chosen), usedStart)
        vm.setStartTime(null)
        vm.calculate()
        assertEquals(Date(now), usedStart) // "now" comes from the injected clock
    }

    @Test
    fun aSatelliteThatNeverRisesGivesTheUnknownSatelliteHeader() {
        val vm = viewModel()
        settings.homeLat.value = "85"
        settings.homeLon.value = "0"
        vm.selectSatellite(25509) // SO-33, inclination 31 degrees
        vm.calculate()
        assertEquals(PassHeader.UnknownSatellite, vm.state.value.header)
        assertTrue(vm.state.value.passes.isEmpty())
    }

    @Test
    fun anInvalidHomeLocationIsReportedWithoutCalculating() {
        var called = false
        val spy = object : PassCalculator() {
            override fun calculate(tle: TLE, latitude: Double, longitude: Double, start: Date, hoursAhead: Int): CalcResult {
                called = true
                return CalcResult.NoPasses
            }
        }
        val vm = viewModel(spy)
        settings.homeLat.value = "not a number"
        vm.calculate()
        assertEquals(PassHeader.InvalidLocation, vm.state.value.header)
        assertFalse(called)
        assertNull(vm.newGroundStation())
    }

    @Test
    fun aCommaDecimalLocationFromAnOldVersionStillWorks() {
        settings.homeLat.value = "51,5"
        settings.homeLon.value = "-0,1"
        val vm = viewModel()
        vm.calculate()
        assertEquals(51.5, (vm.state.value.header as PassHeader.Predictions).latitude, 0.0)
        assertEquals(51.5, vm.newGroundStation()!!.latitude, 0.0)
    }

    @Test
    fun everyCalculationOutcomeBecomesAHeader() {
        val cases = listOf(
            CalcResult.NoPasses to PassHeader.NoPasses("AO-07", 51.5, -0.1, "IO91wm", 24),
            CalcResult.BadElements to PassHeader.BadElements,
            CalcResult.UnknownSatellite to PassHeader.UnknownSatellite,
            CalcResult.Stationary(uk.me.g4dpz.HamSatDroid.domain.SatelliteMotion.FixedPosition(145.0, 22.0)) to
                PassHeader.Stationary("AO-07", uk.me.g4dpz.HamSatDroid.domain.SatelliteMotion.FixedPosition(145.0, 22.0)),
            CalcResult.Error("boom") to PassHeader.Error("boom"),
        )
        for ((result, expected) in cases) {
            val fake = object : PassCalculator() {
                override fun calculate(tle: TLE, latitude: Double, longitude: Double, start: Date, hoursAhead: Int) = result
            }
            val vm = viewModel(fake)
            vm.selectSatellite(7530)
            vm.calculate()
            assertEquals(expected, vm.state.value.header)
            assertTrue(vm.state.value.passes.isEmpty())
            assertFalse(vm.state.value.calculating)
        }
    }

    @Test
    fun selectingAPassAndClearingIt() {
        val vm = viewModel()
        vm.selectSatellite(7530)
        vm.setHoursAhead(24)
        vm.calculate()
        val pass = vm.state.value.passes[1]
        vm.selectPass(pass.id)
        assertEquals(pass, vm.state.value.selectedPass)
        vm.selectPass(null)
        assertNull(vm.state.value.selectedPass)
        vm.selectPass("no such pass")
        assertNull(vm.state.value.selectedPass)
    }

    @Test
    fun aNewCalculationClearsTheSelectedPass() {
        val vm = viewModel()
        vm.selectSatellite(7530)
        vm.setHoursAhead(24)
        vm.calculate()
        vm.selectPass(vm.state.value.passes.first().id)
        vm.calculate()
        assertNull(vm.state.value.selectedPassId)
    }

    // ---- restoring the previous session ----

    @Test
    fun restoresThePassesOfThePreviousSession() {
        val old = listOf(
            SatPassTime(Date(now), Date(now + 600_000), Date(now + 300_000), "north", 10, 200, 40.0),
            SatPassTime(Date(now + 6000_000), Date(now + 6600_000), Date(now + 6300_000), "none", 20, 190, 12.5),
        )
        passStore().save(StoredPasses(7530, "AO-07", now - 60_000, 12, old))
        settings.calcHoursAhead.value = 6 // the control follows the setting, not the range of the old list
        val vm = viewModel()
        val s = vm.state.value
        assertEquals(2, s.passes.size)
        assertEquals(6, s.hoursAhead)
        assertEquals(PassHeader.Restored("AO-07", Instant.ofEpochMilli(now - 60_000)), s.header)
        assertEquals(40.0, s.passes[0].maxElevationDeg, 0.0)
    }

    @Test
    fun anEmptyStoredListShowsNoHeader() {
        passStore().save(StoredPasses(7530, "AO-07", now, 0, emptyList()))
        val vm = viewModel()
        assertTrue(vm.state.value.passes.isEmpty())
        assertNull(vm.state.value.header)
    }

    // ---- keps ----

    @Test
    fun aManualDownloadRemembersTheSourceAndReportsFailure() = runTest {
        val vm = viewModel() // initial automatic download fails silently
        downloader.next = { DownloadResponse(403, "GP data has not updated") }
        vm.refreshKeps(KepsSource.AMATEUR_AMSAT)
        assertEquals(KepsSource.AMATEUR_AMSAT, settings.kepsSource.value)
        assertEquals(UiEvent.KepsFailed("HTTP 403"), withTimeout(1000) { vm.events.first() })
        assertFalse(vm.state.value.downloadingKeps)
        assertTrue(vm.state.value.keps!!.isBundled) // old data kept
        assertNotNull(vm.state.value.keps!!.lastFailure)
    }

    @Test
    fun aSecondManualDownloadInsideTwoHoursIsReportedAsTooSoon() = runTest {
        downloader.next = { DownloadResponse(200, celestrakJson) }
        val vm = viewModel()
        assertEquals(UiEvent.KepsUpdated(8), withTimeout(1000) { vm.events.first() })
        vm.refreshKeps(KepsSource.AMATEUR_CELESTRAK)
        val event = withTimeout(1000) { vm.events.first() } as UiEvent.KepsTooSoon
        assertEquals(now, event.lastSuccessMillis)
        assertEquals(now + 2 * 3600_000, event.nextAllowedAtMillis)
        assertEquals(1, downloader.requested.size)
    }

    @Test
    fun theAutomaticDownloadStaysQuietWhenItFails() = runTest {
        val vm = viewModel() // fails
        vm.refreshKeps(KepsSource.AMATEUR_CELESTRAK, userRequested = false)
        val event = kotlinx.coroutines.withTimeoutOrNull(200) { vm.events.first() }
        assertNull(event)
    }

    @Test
    fun onStartPicksUpElementsStoredByABackgroundUpdate() {
        settings.selectedCatnum.value = 27607 // not the first satellite in either list
        val vm = viewModel() // bundled, download fails
        assertEquals(60, vm.state.value.satellites.size)
        assertEquals(27607, vm.state.value.selectedCatnum)

        // The background worker has its own repository over the same files.
        val worker = FakeDownloader().apply { next = { DownloadResponse(200, celestrakJson) } }
        val result = KepsRepository(files(), worker, { bundledTle }, { now }).refresh(KepsSource.AMATEUR_CELESTRAK)
        assertTrue(result is uk.me.g4dpz.HamSatDroid.data.RefreshResult.Updated)

        vm.onStart()
        val s = vm.state.value
        assertEquals(8, s.satellites.size)
        assertEquals(27607, s.selectedCatnum)
        assertEquals("AMATEUR_CELESTRAK", s.keps!!.source)
    }

    @Test
    fun onStartDoesNothingWhenTheElementsHaveNotChanged() {
        val vm = viewModel()
        val before = vm.state.value
        vm.onStart()
        assertEquals(before, vm.state.value)
    }

    // ---- for the sky and map screens ----

    @Test
    fun providesElementsAndAFreshGroundStationEachTime() {
        val vm = viewModel()
        assertEquals("AO-07", vm.tleFor(7530)!!.name)
        assertNull(vm.tleFor(1))
        val a = vm.newGroundStation()!!
        val b = vm.newGroundStation()!!
        assertEquals(51.5, a.latitude, 0.0)
        assertTrue("each caller needs its own instance", a !== b)
    }

    // ---- opening a pass from the overview ----

    @Test
    fun showPassSelectsTheSatelliteAndTheMatchingCalculatedPass() {
        val vm = viewModel()
        vm.selectSatellite(25509) // something else first
        vm.setHoursAhead(24)
        // A pass from the overview, as it calculated it (its AOS may differ from the passes screen's by seconds)
        val calculated = (PassCalculator().calculate(
            vm.tleFor(7530)!!, 51.5, -0.1, Date(now), 24,
        ) as CalcResult.Passes).passes[1]
        val fromOverview = uk.me.g4dpz.HamSatDroid.domain.PassSummary.from(calculated, 7530, "AO-07").let {
            it.copy(start = it.start.plusSeconds(5))
        }
        vm.showPass(fromOverview)

        val s = vm.state.value
        assertEquals(7530, s.selectedCatnum)
        assertNull(s.startTime)
        assertTrue("several passes calculated", s.passes.size >= 3)
        // the overlapping calculated pass is selected, not a duplicate added
        assertEquals(1, s.passes.count { it.start.isBefore(fromOverview.end) && fromOverview.start.isBefore(it.end) })
        assertNotNull(s.selectedPass)
        assertEquals(calculated.startTime.toInstant(), s.selectedPass!!.start)
    }

    @Test
    fun showPassAddsThePassWhenTheCalculationDoesNotIncludeIt() {
        val vm = viewModel()
        vm.setHoursAhead(0) // next pass only: a later pass from the overview is not in the list
        val passes = (PassCalculator().calculate(vm.tleFor(7530)!!, 51.5, -0.1, Date(now), 24) as CalcResult.Passes).passes
        val later = uk.me.g4dpz.HamSatDroid.domain.PassSummary.from(passes.last(), 7530, "AO-07")
        vm.showPass(later)

        val s = vm.state.value
        assertEquals(later, s.selectedPass)
        assertEquals(s.passes.sortedBy { it.start }, s.passes)
        assertTrue(s.passes.size >= 2)
    }

    @Test
    fun showPassForASatelliteNotInTheListDoesNothing() {
        val vm = viewModel()
        val before = vm.state.value
        val stranger = uk.me.g4dpz.HamSatDroid.domain.PassSummary(
            999999, "NOBODY", Instant.ofEpochMilli(now), Instant.ofEpochMilli(now + 600_000), Instant.ofEpochMilli(now + 300_000),
            uk.me.g4dpz.HamSatDroid.domain.PoleCrossing.NONE, 0, 0, 10.0,
        )
        vm.showPass(stranger)
        assertEquals(before, vm.state.value)
    }
}
