// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.me.g4dpz.HamSatDroid.data.DownloadOutcome
import uk.me.g4dpz.HamSatDroid.data.KepsRepository
import uk.me.g4dpz.HamSatDroid.data.KepsSource
import uk.me.g4dpz.HamSatDroid.data.LoadedKeps
import uk.me.g4dpz.HamSatDroid.data.LocationText
import uk.me.g4dpz.HamSatDroid.data.PassStore
import uk.me.g4dpz.HamSatDroid.data.RefreshResult
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.data.StoredPasses
import uk.me.g4dpz.HamSatDroid.domain.CalcResult
import uk.me.g4dpz.HamSatDroid.domain.ElementAge
import uk.me.g4dpz.HamSatDroid.domain.HomeLocation
import uk.me.g4dpz.HamSatDroid.domain.PassCalculator
import uk.me.g4dpz.HamSatDroid.domain.PassHeader
import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import uk.me.g4dpz.HamSatDroid.domain.SatelliteOrder
import uk.me.g4dpz.HamSatDroid.utils.IaruLocator
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.SatPassTime
import uk.me.g4dpz.satellite.TLE
import java.io.IOException
import java.time.Instant
import java.util.Date

/**
 * Holds the app's state and does its slow work (loading keps, downloading, calculating passes) off the main thread.
 * It replaces the static fields and thread helpers of the old activity, and survives rotation and window resizing.
 *
 * All the heavy dependencies are passed in, so it can be tested without Android.
 *
 * @param workDispatcher where loading, downloading and calculating run
 */
class AppViewModel(
    private val keps: KepsRepository,
    private val passStore: PassStore,
    private val settings: SettingsRepository,
    private val calculator: PassCalculator = PassCalculator(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Called when the keps or the selected satellite change, so the widget can be redrawn. Default no-op (tests). */
    private val onDataChanged: suspend () -> Unit = {},
    /**
     * Where loading and downloading run. Blocking network and file I/O must not sit on [Dispatchers.Default], whose
     * thread count is the CPU count and which the overview's calculations share: one download waiting on a 30 s
     * read timeout would hold a quarter of a four-core tablet's CPU workers.
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(AppUiState(hoursAhead = settings.calcHoursAhead.value))
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)

    /** One-off messages for the user; collect them once and show them. */
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private val _tles = MutableStateFlow<List<TLE>>(emptyList())

    /** The current elements of every satellite (for the overview); changes when new keps are loaded. */
    val tles: StateFlow<List<TLE>> = _tles.asStateFlow()
    private var kepsModified: Long? = null
    private var calcJob: Job? = null

    /** Counts calculations started; see [calculate]. Only touched from the callers' thread (the main thread). */
    private var calcGeneration = 0
    private var rawPasses: List<SatPassTime> = emptyList()

    // Declared before init: the first load starts there.
    private val reloadLock = Any()

    init {
        viewModelScope.launch(ioDispatcher) { initialLoad() }
        // A change of source, from the menu or from the Settings screen, shows that source's stored copy at once and
        // without a download (each source keeps its own copy). A download that follows reloads again when it arrives.
        viewModelScope.launch(ioDispatcher) {
            settings.kepsSource.state.drop(1).collect { reload(currentSelection()) }
        }
    }

    /** The satellite to keep selected across a reload: the current one, or before the first load the saved one. */
    private fun currentSelection(): Int? = _state.value.selectedCatnum ?: settings.selectedCatnum.value.takeIf { it > 0 }

    // ---- selections -------------------------------------------------------------------------------------------------

    fun selectSatellite(catnum: Int) {
        if (_tles.value.none { it.catnum == catnum }) return
        settings.selectedCatnum.value = catnum
        _state.update { it.copy(selectedCatnum = catnum) }
        viewModelScope.launch { onDataChanged() }
    }

    /** @param hours 0 for "next pass only", otherwise 6, 12 or 24 */
    fun setHoursAhead(hours: Int) {
        settings.calcHoursAhead.value = hours // rejects values other than 0, 6, 12 or 24
        _state.update { it.copy(hoursAhead = hours) }
    }

    /** Sets the time to start looking from, or null to start from "now". */
    fun setStartTime(start: Instant?) {
        _state.update { it.copy(startTime = start) }
    }

    fun selectPass(id: String?) {
        _state.update { it.copy(selectedPassId = id) }
    }

    /**
     * Shows [pass] (from the overview) as if it had been chosen on the passes screen: selects its satellite, calculates
     * that satellite's passes from now, and selects [pass]. If the calculation does not include it (it starts beyond
     * the chosen range) it is added to the list, so the sky view always has it.
     */
    fun showPass(pass: PassSummary) {
        selectSatellite(pass.catnum)
        if (_state.value.selectedCatnum != pass.catnum) return
        _state.update { it.copy(startTime = null, passes = listOf(pass), selectedPassId = pass.id) }
        calculate(keepSelected = pass)
    }

    // ---- pass calculation -------------------------------------------------------------------------------------------

    /** Calculates passes for the selected satellite, home location, start time and range. A newer call replaces an older one. */
    fun calculate() = calculate(keepSelected = null)

    private fun calculate(keepSelected: PassSummary?) {
        val current = _state.value
        val tle = _tles.value.firstOrNull { it.catnum == current.selectedCatnum } ?: return
        val lat = LocationText.toDegrees(settings.homeLat.value)
        val lon = LocationText.toDegrees(settings.homeLon.value)
        if (lat != null && lon != null && !HomeLocation.isSet(settings.homeLat.value, settings.homeLon.value)) {
            // Drop a calculation still running for the old location, so it cannot overwrite this state
            calcJob?.cancel()
            ++calcGeneration
            showFailure(PassHeader.LocationNotSet) // also drops any passes from an earlier location
            _state.update { it.copy(calculating = false) }
            passStore.clear() // so they do not come back after a restart
            return
        }
        if (lat == null || lon == null) {
            _state.update { it.copy(header = PassHeader.InvalidLocation) }
            return
        }
        val start = current.startTime ?: Instant.ofEpochMilli(clock())
        val hours = current.hoursAhead

        calcJob?.cancel()
        // The library call cannot be interrupted, so a cancelled job only notices after it returns. Its finally block
        // must then leave `calculating` alone: by then a newer job owns the flag. Each job remembers the generation
        // it belongs to and only the newest clears the flag.
        val generation = ++calcGeneration
        _state.update { it.copy(calculating = true) }
        calcJob = viewModelScope.launch(workDispatcher) {
            try {
                val result = calculator.calculate(tle, lat, lon, Date.from(start), hours)
                ensureActive() // a newer calculation has replaced this one
                if (generation != calcGeneration) return@launch // or the location was unset meanwhile
                val locator = IaruLocator(lat, lon).toMaidenhead()
                when (result) {
                    is CalcResult.Passes -> {
                        rawPasses = result.passes
                        val calculated = result.passes.map { PassSummary.from(it, tle.catnum, tle.name) }
                        val (summaries, selected) = withPass(calculated, keepSelected)
                        _state.update {
                            it.copy(
                                passes = summaries, selectedPassId = selected,
                                header = PassHeader.Predictions(tle.name, lat, lon, locator, hours, start),
                            )
                        }
                    }
                    CalcResult.NoPasses -> {
                        rawPasses = emptyList()
                        _state.update {
                            it.copy(
                                passes = listOfNotNull(keepSelected), selectedPassId = keepSelected?.id,
                                header = PassHeader.NoPasses(tle.name, lat, lon, locator, NO_PASS_SEARCH_HOURS),
                            )
                        }
                    }
                    is CalcResult.Stationary -> showFailure(PassHeader.Stationary(tle.name, result.position))
                    CalcResult.BadElements -> showFailure(PassHeader.BadElements)
                    CalcResult.UnknownSatellite -> showFailure(PassHeader.UnknownSatellite)
                    is CalcResult.Error -> showFailure(PassHeader.Error(result.message))
                }
                savePasses(tle)
            } finally {
                if (generation == calcGeneration) _state.update { it.copy(calculating = false) }
            }
        }
    }

    /**
     * [passes] and the id to select for [extra]. The same pass calculated from a different start time can differ by a
     * few seconds, so a calculated pass that overlaps [extra] is taken to be it; otherwise [extra] is added.
     */
    private fun withPass(passes: List<PassSummary>, extra: PassSummary?): Pair<List<PassSummary>, String?> {
        if (extra == null) return passes to null
        val same = passes.firstOrNull {
            it.catnum == extra.catnum && it.start.isBefore(extra.end) && extra.start.isBefore(it.end)
        }
        return if (same != null) passes to same.id else (passes + extra).sortedBy { it.start } to extra.id
    }

    private fun showFailure(header: PassHeader) {
        rawPasses = emptyList()
        _state.update { it.copy(passes = emptyList(), selectedPassId = null, header = header) }
    }

    private fun savePasses(tle: TLE) {
        try {
            passStore.save(StoredPasses(tle.catnum, tle.name, clock(), _state.value.hoursAhead, rawPasses))
        } catch (e: IOException) {
            // The saved list is a convenience; failing to save it loses nothing important.
        }
    }

    // ---- keps -------------------------------------------------------------------------------------------------------

    /**
     * Downloads [source] if the download rules allow, and reloads the satellite list when new elements arrive. The
     * repository keeps the existing elements on any failure.
     *
     * @param userRequested true for a menu choice, which also remembers the source and reports "already up to date"
     *   and failures; false for the automatic download, which only reports success
     */
    fun refreshKeps(source: KepsSource = settings.kepsSource.value, userRequested: Boolean = true) {
        viewModelScope.launch(ioDispatcher) { refreshKepsNow(source, userRequested) }
    }

    /** Call when the screen becomes visible: picks up elements a background update has stored meanwhile. */
    fun onStart() {
        viewModelScope.launch(ioDispatcher) {
            val modified = keps.lastModifiedMillis()
            // At start-up this can run before the first load has set the selection, so fall back to the saved one
            // (otherwise the first satellite in the list would replace it)
            val preferred = _state.value.selectedCatnum ?: settings.selectedCatnum.value.takeIf { it > 0 }
            if (modified != null && modified != kepsModified) reload(preferred)
        }
    }

    private fun refreshKepsNow(source: KepsSource, userRequested: Boolean) {
        if (userRequested) {
            settings.kepsSource.value = source
            // Show the chosen source's stored copy now, before the (possibly slow, possibly failing) download. The
            // observer in init does the same for a change made elsewhere, but it runs on its own schedule; doing it here
            // as well makes the order certain.
            reload(currentSelection())
        }
        _state.update { it.copy(downloadingKeps = true) }
        try {
            when (val result = keps.refresh(source)) {
                is RefreshResult.Updated -> {
                    reload(_state.value.selectedCatnum)
                    _events.trySend(UiEvent.KepsUpdated(result.count))
                }
                // Nothing to download. The chosen source's stored copy is already showing: setting the source switched
                // to it (see init), so choosing a source never needs a download and is never locked out by the
                // two-hour rule.
                is RefreshResult.TooSoon -> if (userRequested) {
                    _events.trySend(UiEvent.KepsTooSoon(source, result.lastSuccessMillis, result.nextAllowedAtMillis))
                }
                // Failed: the stored copy of the chosen source if it has one, otherwise the list stays as it was
                is RefreshResult.Failed -> if (userRequested) {
                    val reason = when (val o = result.outcome) {
                        is DownloadOutcome.Transient -> o.reason
                        is DownloadOutcome.Permanent -> o.reason
                        DownloadOutcome.Success -> ""
                    }
                    _events.trySend(UiEvent.KepsFailed(reason))
                }
            }
        } finally {
            // The outcome (including a failure) is recorded by the repository; show it in the keps info.
            val lastFailure = keps.status().lastFailure
            _state.update { it.copy(downloadingKeps = false, keps = it.keps?.copy(lastFailure = lastFailure)) }
        }
    }

    private fun initialLoad() {
        val loaded = loadAndApply(settings.selectedCatnum.value.takeIf { it > 0 })

        // Show the passes from the previous session, if any. Not while the location is unset (or not a number): up to
        // version 2.0.2 Calculate pass saved passes for the default 0, 0, and they must not appear under the "location
        // not set" banner. Delete them so they cannot come back once a location is set.
        if (!HomeLocation.isSet(settings.homeLat.value, settings.homeLon.value)) {
            passStore.clear()
        } else {
            passStore.load()?.let { stored ->
                rawPasses = stored.passes
                _state.update {
                    it.copy(
                        passes = stored.passes.map { p -> PassSummary.from(p, stored.catnum, stored.satName) },
                        header = if (stored.passes.isEmpty()) null
                        else PassHeader.Restored(stored.satName, Instant.ofEpochMilli(stored.computedAtMillis)),
                    )
                }
            }
        }

        // Until the first successful download only the elements shipped with the app are available, so fetch once
        // without being asked. Only when the user allows automatic downloads: with the setting off every launch
        // would otherwise try the network (a failed connection does not start the two-hour wait).
        if (loaded.isBundled && settings.autoKepsRefresh.value) {
            refreshKepsNow(settings.kepsSource.value, userRequested = false)
        }
    }

    private fun reload(preferred: Int?) {
        loadAndApply(preferred)
    }

    /**
     * Reads the stored elements and shows them. Serialised: a reload for a change of source and one after a download
     * can be asked for at the same moment, and each reads the files afresh, so whichever runs last shows the latest.
     */
    private fun loadAndApply(preferred: Int?): LoadedKeps = synchronized(reloadLock) {
        val loaded = keps.load()
        kepsModified = keps.lastModifiedMillis()
        apply(loaded, preferred)
        loaded
    }


    private fun apply(loaded: LoadedKeps, preferred: Int?) {
        viewModelScope.launch { onDataChanged() }
        _tles.value = loaded.tles
        val items = loaded.tles
            .map { SatelliteItem(it.catnum, it.name, ElementAge.epochMillis(it)) }
            .sortedWith(SatelliteOrder.byName { it.name })
        val selected = items.firstOrNull { it.catnum == preferred }?.catnum ?: items.firstOrNull()?.catnum
        _state.update {
            it.copy(
                loading = false,
                satellites = items,
                selectedCatnum = selected,
                keps = KepsInfo(
                    source = loaded.source,
                    fetchedAtMillis = loaded.fetchedAtMillis,
                    isBundled = loaded.isBundled,
                    satelliteCount = loaded.tles.size,
                    lastFailure = keps.status().lastFailure,
                ),
            )
        }
    }

    // ---- for the sky and map screens --------------------------------------------------------------------------------

    /** The elements for [catnum], or null if it is not in the current list. */
    fun tleFor(catnum: Int): TLE? = _tles.value.firstOrNull { it.catnum == catnum }

    /**
     * A new ground station at the home location, or null if the location in settings is not valid. Each caller gets
     * its own instance because the prediction code writes into it.
     */
    fun newGroundStation(): GroundStationPosition? {
        val lat = LocationText.toDegrees(settings.homeLat.value) ?: return null
        val lon = LocationText.toDegrees(settings.homeLon.value) ?: return null
        return GroundStationPosition(lat, lon, 0.0)
    }

    private companion object {
        /** How far ahead the "no passes found" message says it looked (as in the original app). */
        const val NO_PASS_SEARCH_HOURS = 24
    }
}
