// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.me.g4dpz.HamSatDroid.data.LocationText
import uk.me.g4dpz.HamSatDroid.data.SavedStation
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.ContactPlanner
import uk.me.g4dpz.HamSatDroid.domain.ContactPlannerCalculator
import uk.me.g4dpz.HamSatDroid.domain.ElementAge
import uk.me.g4dpz.HamSatDroid.domain.HomeLocation
import uk.me.g4dpz.HamSatDroid.domain.MutualMoment
import uk.me.g4dpz.HamSatDroid.domain.MutualWindow
import uk.me.g4dpz.HamSatDroid.domain.PlannerItem
import uk.me.g4dpz.HamSatDroid.domain.PlannerStation
import uk.me.g4dpz.satellite.TLE
import java.time.Instant

/** The orbital elements of the satellite searched are older than the app's stale limit; the screen words the warning. */
data class StaleElements(val satelliteName: String, val days: Int)

/** What the contact planner screen shows. */
data class PlannerUiState(
    /** False when the home location is not set; nothing is searched then. */
    val locationSet: Boolean,
    val stations: List<SavedStation>,
    /** The station chosen as the other end, or null when none is chosen (or the chosen one was deleted). */
    val chosen: SavedStation?,
    /** One minimum elevation, used for both stations. */
    val minElevation: Int,
    /** The satellite chosen on the passes screen; searched when [allSatellites] is false. 0 when none. */
    val selectedCatnum: Int,
    /** Its name, or null when it is not in the loaded elements. */
    val selectedName: String?,
    val hours: Int,
    val allSatellites: Boolean,
    val distanceKm: Double?,
    val searching: Boolean,
    /** Satellites done and in all, while searching all satellites; null otherwise. */
    val progress: Pair<Int, Int>?,
    /** Sorted by start. Always for the current inputs: results for older inputs are cleared at once. */
    val windows: List<MutualWindow>,
    /** Stationary satellites usable from both stations now (shown before the windows). */
    val usableNow: List<PlannerItem.UsableNow>,
    /** Set while searching the selected satellite and its elements are older than the stale limit. */
    val staleElements: StaleElements?,
    /** The window whose detail is open: its satellite and start. */
    val expanded: Pair<Int, Instant>?,
    /** The rows of the open window's detail; empty until they are worked out. */
    val timeline: List<MutualMoment>,
)

/**
 * The contact planner. It belongs to the planner screen, so popping the screen clears it and stops its work.
 *
 * **When it searches.** The screen tells it when it is on screen ([onStart] and [onStop], tied to the screen's
 * STARTED state, so opening the station editor or another screen, or the app going to the background, stops the
 * search). On start it searches if a station is chosen and the results are not already complete for the current
 * inputs. Finished results are kept while nothing they depend on changes; an unfinished search starts again.
 *
 * **What the results depend on** (the key): the home position, the chosen station (with its position), the minimum
 * elevation, the time window, the scope, the satellite selected on the passes screen when the scope is "selected", and
 * the loaded elements. When any of them changes, results for the old key are cleared at once, so they are never shown
 * as current, and a search starts if the screen is on screen.
 */
class ContactPlannerViewModel(
    private val tles: StateFlow<List<TLE>>,
    /** The satellite selected on the passes screen, or null when none. */
    private val selectedCatnum: StateFlow<Int?>,
    private val settings: SettingsRepository,
    private val calculator: ContactPlannerCalculator = ContactPlannerCalculator(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) : ViewModel() {

    /** Everything a search depends on. */
    private data class Key(
        val tles: List<TLE>,
        /** The selected satellite, or null when searching all satellites (which then ignores the selection). */
        val satellite: Int?,
        val lat: String,
        val lon: String,
        val chosen: SavedStation?,
        val minElevation: Int,
        val hours: Int,
        val all: Boolean,
    )

    private var lastKey: Key = currentKey()

    /** The key the results in the state belong to, and whether that search ran to the end. */
    private var searchedKey: Key? = null
    private var finished = false
    private var active = false
    private var job: Job? = null
    private var detailJob: Job? = null
    private var tickJob: Job? = null

    private val _state = MutableStateFlow(stateFor(lastKey, selectedCatnum.value ?: 0))
    val state: StateFlow<PlannerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            merge<Any?>(
                tles, selectedCatnum, settings.homeLat.state, settings.homeLon.state, settings.plannerStations.state,
                settings.plannerStationId.state, settings.plannerMinElevation.state, settings.plannerHours.state,
                settings.plannerAllSatellites.state,
            ).collect { onInputsChanged() }
        }
    }

    // ---- what the screen asks for ----

    /** The planner is on screen: search if the results are not complete for the current inputs. */
    fun onStart() {
        active = true
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                delay(TICK_MILLIS)
                dropEnded()
            }
        }
        if (searchedKey != lastKey || !finished) startSearch(lastKey) else dropEnded()
    }

    /** The planner is no longer on screen: stop searching. An unfinished search starts again on [onStart]. */
    fun onStop() {
        active = false
        tickJob?.cancel()
        job?.cancel()
        _state.update { it.copy(searching = false, progress = null) }
    }

    /** Searches again from now with the same choices. */
    fun refresh() = startSearch(lastKey)

    fun chooseStation(id: String) {
        settings.plannerStationId.value = id
    }

    fun setMinElevation(degrees: Int) {
        settings.plannerMinElevation.value = degrees
    }

    fun setHours(hours: Int) {
        settings.plannerHours.value = hours
    }

    fun setAllSatellites(all: Boolean) {
        settings.plannerAllSatellites.value = all
    }

    /** Opens the detail of [window], or closes it when it is already open. The rows are worked out in the background. */
    fun toggleDetail(window: MutualWindow) {
        val id = window.catnum to window.start
        if (_state.value.expanded == id) {
            closeDetail()
            return
        }
        detailJob?.cancel()
        _state.update { it.copy(expanded = id, timeline = emptyList()) }
        val key = lastKey
        val tle = key.tles.firstOrNull { it.catnum == window.catnum } ?: return
        val home = homeStation(key) ?: return
        val other = otherStation(key) ?: return
        val detail = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val rows = withContext(workDispatcher) { ContactPlanner.timeline(tle, home, other, window) }
            _state.update { if (it.expanded == id) it.copy(timeline = rows) else it }
        }
        detailJob = detail
        detail.start()
    }

    /** Drops the windows that have ended (requirement 5.5). Called every minute while the planner is on screen. */
    internal fun dropEnded() {
        val now = Instant.ofEpochMilli(clock())
        val open = _state.value.expanded
        _state.update { s -> s.copy(windows = s.windows.filter { it.end.isAfter(now) }) }
        if (open != null && _state.value.windows.none { it.catnum == open.first && it.start == open.second }) closeDetail()
    }

    // ---- inputs changing ----

    private fun currentKey(): Key {
        val chosen = settings.plannerStations.value.firstOrNull { it.id == settings.plannerStationId.value }
        val all = settings.plannerAllSatellites.value
        return Key(
            tles = tles.value,
            satellite = if (all) null else selectedCatnum.value,
            lat = settings.homeLat.value,
            lon = settings.homeLon.value,
            chosen = chosen,
            minElevation = settings.plannerMinElevation.value,
            hours = settings.plannerHours.value,
            all = all,
        )
    }

    private fun onInputsChanged() {
        val key = currentKey()
        // the list of saved stations and the selection are shown even when they do not change the search
        _state.update {
            it.copy(
                stations = settings.plannerStations.value,
                selectedCatnum = selectedCatnum.value ?: 0,
                selectedName = tles.value.firstOrNull { t -> t.catnum == selectedCatnum.value }?.name,
            )
        }
        if (key == lastKey) return
        lastKey = key
        job?.cancel()
        closeDetail()
        _state.update { stateFor(key, selectedCatnum.value ?: 0) }
        searchedKey = null
        finished = false
        if (active) startSearch(key)
    }

    /** The state for [key] before any result: everything the inputs say, no windows, not searching. */
    private fun stateFor(key: Key, selected: Int): PlannerUiState {
        val home = homeStation(key)
        val other = otherStation(key)
        val locationSet = HomeLocation.isSet(key.lat, key.lon)
        return PlannerUiState(
            locationSet = locationSet,
            stations = settings.plannerStations.value,
            chosen = key.chosen,
            minElevation = key.minElevation,
            selectedCatnum = selected,
            selectedName = key.tles.firstOrNull { it.catnum == selected }?.name,
            hours = key.hours,
            allSatellites = key.all,
            distanceKm = if (locationSet && home != null && other != null) ContactPlanner.greatCircleKm(home, other) else null,
            searching = false,
            progress = null,
            windows = emptyList(),
            usableNow = emptyList(),
            staleElements = null,
            expanded = null,
            timeline = emptyList(),
        )
    }

    private fun homeStation(key: Key): PlannerStation? {
        val lat = LocationText.toDegrees(key.lat) ?: return null
        val lon = LocationText.toDegrees(key.lon) ?: return null
        return PlannerStation(lat, lon)
    }

    private fun otherStation(key: Key): PlannerStation? {
        val station = key.chosen ?: return null
        val lat = LocationText.toDegrees(station.latitude) ?: return null
        val lon = LocationText.toDegrees(station.longitude) ?: return null
        return PlannerStation(lat, lon)
    }

    // ---- searching ----

    private fun startSearch(key: Key) {
        job?.cancel()
        closeDetail()
        searchedKey = key
        finished = false
        val startedAt = clock()
        val home = homeStation(key)
        val other = otherStation(key)
        val satellites = if (key.all) key.tles else listOfNotNull(key.tles.firstOrNull { it.catnum == key.satellite })
        if (!HomeLocation.isSet(key.lat, key.lon) || home == null || other == null || satellites.isEmpty()) {
            // nothing to search: not set, no station chosen, or no satellite to search
            finished = true
            _state.update { it.copy(searching = false, progress = null, windows = emptyList(), usableNow = emptyList(), staleElements = null) }
            return
        }
        val stale = if (key.all) null else ElementAge.staleDays(ElementAge.epochMillis(satellites.first()), startedAt)
            ?.let { StaleElements(satellites.first().name, it) }
        _state.update {
            it.copy(
                searching = true, progress = if (key.all) 0 to satellites.size else null, windows = emptyList(),
                usableNow = emptyList(), staleElements = stale,
            )
        }
        // Assigned before it runs: with an immediate dispatcher the body starts inside launch, and a stop that arrives
        // from within the search must find the job it is meant to cancel.
        val search = viewModelScope.launch(start = CoroutineStart.LAZY) {
            calculator.calculate(satellites, home, other, key.minElevation.toDouble(), Instant.ofEpochMilli(startedAt), key.hours)
                .collect { item ->
                    ensureActive() // a result that arrives after the search was stopped or replaced is not used
                    _state.update { s ->
                        s.copy(
                            progress = s.progress?.let { (done, total) -> done + 1 to total },
                            windows = if (item is PlannerItem.Windows) (s.windows + item.windows).sortedBy { it.start } else s.windows,
                            usableNow = if (item is PlannerItem.UsableNow) s.usableNow + item else s.usableNow,
                        )
                    }
                }
            finished = true
            _state.update { it.copy(searching = false, progress = null) }
            dropEnded()
            log("Contact planner: ${satellites.size} satellite(s) over ${key.hours} h took ${clock() - startedAt} ms")
        }
        job = search
        search.start()
    }

    private fun closeDetail() {
        detailJob?.cancel()
        _state.update { if (it.expanded == null && it.timeline.isEmpty()) it else it.copy(expanded = null, timeline = emptyList()) }
    }

    companion object {
        private const val TAG = "ContactPlanner"
        const val TICK_MILLIS = 60_000L
    }
}
