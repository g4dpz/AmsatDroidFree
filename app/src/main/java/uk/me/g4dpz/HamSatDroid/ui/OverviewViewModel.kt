package uk.me.g4dpz.HamSatDroid.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.me.g4dpz.HamSatDroid.data.LocationText
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.AzEl
import uk.me.g4dpz.HamSatDroid.domain.OverviewCalculator
import uk.me.g4dpz.HamSatDroid.domain.OverviewItem
import uk.me.g4dpz.HamSatDroid.domain.OverviewRows
import uk.me.g4dpz.HamSatDroid.domain.UpNowRow
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.SatelliteFactory
import uk.me.g4dpz.satellite.TLE
import java.time.Instant
import java.util.Date

/** What the overview screen shows. */
data class OverviewUiState(
    val hours: Int,
    val minElevation: Int,
    /** False when the home location in settings is not valid; nothing is calculated then. */
    val locationValid: Boolean = true,
    val calculating: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    /** Satellites with results so far, by catalogue number. */
    val results: Map<Int, OverviewItem.Result> = emptyMap(),
    /** How long the last complete calculation took, for recording on the device (Requirement 16.10). */
    val elapsedMillis: Long? = null,
)

/**
 * The all-satellite overview. It belongs to the overview screen, so leaving the screen cancels a calculation in
 * progress. It recalculates when the elements, the home location or the window change, or when asked to; the
 * minimum-elevation filter only filters, it does not recalculate.
 */
class OverviewViewModel(
    private val tles: StateFlow<List<TLE>>,
    private val settings: SettingsRepository,
    private val calculator: OverviewCalculator = OverviewCalculator(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val positionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val log: (String) -> Unit = { Log.i(TAG, it) },
) : ViewModel() {

    private val _state = MutableStateFlow(
        OverviewUiState(hours = settings.overviewHours.value, minElevation = settings.overviewMinElevation.value)
    )
    val state: StateFlow<OverviewUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** The inputs last calculated for, so the same inputs are not calculated twice. */
    private data class Key(val tles: List<TLE>, val lat: String, val lon: String, val hours: Int)

    private var lastKey: Key? = null

    init {
        viewModelScope.launch {
            combine(tles, settings.homeLat.state, settings.homeLon.state, settings.overviewHours.state) { t, lat, lon, h ->
                Key(t, lat, lon, h)
            }.collect { key -> if (key != lastKey) start(key) }
        }
    }

    fun setHours(hours: Int) {
        settings.overviewHours.value = hours // recalculates through the settings flow
        _state.update { it.copy(hours = hours) }
    }

    fun setMinElevation(degrees: Int) {
        settings.overviewMinElevation.value = degrees
        _state.update { it.copy(minElevation = degrees) }
    }

    /** Calculates again from now with the same inputs. */
    fun refresh() {
        lastKey?.let { start(it) }
    }

    private fun start(key: Key) {
        lastKey = key
        job?.cancel()
        val lat = LocationText.toDegrees(key.lat)
        val lon = LocationText.toDegrees(key.lon)
        if (lat == null || lon == null || key.tles.isEmpty()) {
            _state.update {
                it.copy(
                    locationValid = lat != null && lon != null, calculating = false, done = 0, total = 0,
                    results = emptyMap(), elapsedMillis = null,
                )
            }
            return
        }
        val startedAt = clock()
        _state.update {
            it.copy(
                locationValid = true, calculating = true, done = 0, total = key.tles.size, results = emptyMap(),
                elapsedMillis = null,
            )
        }
        job = viewModelScope.launch {
            calculator.calculate(key.tles, lat, lon, Instant.ofEpochMilli(startedAt), key.hours).collect { item ->
                _state.update {
                    it.copy(
                        done = it.done + 1,
                        results = if (item is OverviewItem.Result) it.results + (item.catnum to item) else it.results,
                    )
                }
            }
            val elapsed = clock() - startedAt
            log("Overview of ${key.tles.size} satellites for ${key.hours} h took $elapsed ms")
            _state.update { it.copy(calculating = false, elapsedMillis = elapsed) }
        }
    }

    /**
     * The "Up now" list, with positions worked out again every [UP_NOW_REFRESH_MILLIS] while the screen is showing
     * it (and not at all otherwise).
     */
    val upNow: StateFlow<List<UpNowRow>> = flow {
        while (true) {
            emit(upNowAt(Instant.ofEpochMilli(clock())))
            delay(UP_NOW_REFRESH_MILLIS)
        }
    }.flowOn(positionDispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun upNowAt(now: Instant): List<UpNowRow> {
        val current = _state.value
        val lat = LocationText.toDegrees(settings.homeLat.value) ?: return emptyList()
        val lon = LocationText.toDegrees(settings.homeLon.value) ?: return emptyList()
        val byCatnum = tles.value.associateBy { it.catnum }
        val candidates = OverviewRows.upNowCandidates(current.results.values, now)
        val positions = candidates.associate { r ->
            r.catnum to byCatnum[r.catnum]?.let { tle -> position(tle, lat, lon, now) }
        }
        return OverviewRows.upNow(candidates, now) { positions[it] }
    }

    /** A fresh satellite and ground station each time: the prediction code writes into both. */
    private fun position(tle: TLE, lat: Double, lon: Double, now: Instant): AzEl? = try {
        val pos = SatelliteFactory.createSatellite(tle).getPosition(GroundStationPosition(lat, lon, 0.0), Date.from(now))
        AzEl(Math.toDegrees(pos.azimuth), Math.toDegrees(pos.elevation))
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        private const val TAG = "Overview"
        const val UP_NOW_REFRESH_MILLIS = 2_000L
    }
}
