// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.data.SavedStation
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.MutualWindow
import uk.me.g4dpz.HamSatDroid.domain.PlannerItem
import uk.me.g4dpz.HamSatDroid.ui.components.DropdownField
import java.time.Duration

/** Wide screens keep the content this wide, centred, as the setup guide does. */
private val MAX_CONTENT_WIDTH = 600.dp

/**
 * The contact planner: pick the other station, and see when a satellite is above the horizon at both ends, with the
 * best moment of each window. The search runs while this screen is on screen (the view model is told when it starts and
 * stops), and starts by itself when a station is chosen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPlannerScreen(
    viewModel: ContactPlannerViewModel,
    onBack: () -> Unit,
    /** Opens the setup guide; offered while no home location is set. */
    onOpenSetup: () -> Unit,
    onAddStation: () -> Unit,
    onEditStation: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = rememberDisplayFormats()
    // Opening the station editor or another screen, or the app going to the background, stops the search
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.planner_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                )
                if (state.searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.locationSet) {
                    // Nothing can be searched without a home location (requirement 1.2)
                    item(key = "no-location") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.overview_no_location), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = onOpenSetup) { Text(stringResource(R.string.banner_set_up)) }
                        }
                    }
                    return@LazyColumn
                }
                item(key = "stations") {
                    StationsSection(state, viewModel, onAddStation, onEditStation, formats)
                }
                if (state.stations.isNotEmpty()) {
                    item(key = "search") { SearchSection(state, viewModel) }
                    item(key = "status") { StatusLine(state) }
                }
                state.staleElements?.let { stale ->
                    item(key = "stale") {
                        Text(
                            pluralStringResource(R.plurals.passes_elements_stale, stale.days, stale.satelliteName, stale.days),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (state.chosen != null) resultItems(state, formats)
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun StationsSection(
    state: PlannerUiState,
    viewModel: ContactPlannerViewModel,
    onAddStation: () -> Unit,
    onEditStation: (String) -> Unit,
    formats: DisplayFormats,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(stringResource(R.string.planner_section_stations))
        Text(stringResource(R.string.planner_your_station, state.homeLocator), style = MaterialTheme.typography.bodyLarge)
        DropdownField(
            label = stringResource(R.string.planner_min_elevation),
            options = SettingsRepository.PLANNER_MIN_ELEVATION_CHOICES,
            selected = state.minElevation,
            optionLabel = { stringResource(R.string.overview_degrees_option, it) },
            onSelected = viewModel::setMinElevation,
        )
        if (state.stations.isEmpty()) {
            Text(stringResource(R.string.planner_no_station), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAddStation) { Text(stringResource(R.string.planner_add_station)) }
        } else {
            DropdownField(
                label = stringResource(R.string.planner_other_station),
                options = state.stations,
                selected = state.chosen,
                optionLabel = { it.displayName },
                onSelected = { viewModel.chooseStation(it.id) },
            )
            if (state.chosen == null) {
                Text(stringResource(R.string.planner_choose_station), style = MaterialTheme.typography.bodyMedium)
            }
            val full = state.stations.size >= SavedStation.MAX_STATIONS
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { state.chosen?.let { onEditStation(it.id) } }, enabled = state.chosen != null) {
                    Text(stringResource(R.string.planner_edit_station))
                }
                OutlinedButton(onClick = onAddStation, enabled = !full) { Text(stringResource(R.string.planner_add_station)) }
            }
            if (full) {
                Text(
                    pluralStringResource(R.plurals.planner_stations_full, SavedStation.MAX_STATIONS, SavedStation.MAX_STATIONS),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.distanceKm?.let {
                Text(
                    stringResource(R.string.planner_distance, formats.integer(it)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSection(state: PlannerUiState, viewModel: ContactPlannerViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeading(stringResource(R.string.planner_section_search))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !state.allSatellites,
                onClick = { viewModel.setAllSatellites(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.planner_scope_selected)) }
            SegmentedButton(
                selected = state.allSatellites,
                onClick = { viewModel.setAllSatellites(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.planner_scope_all)) }
        }
        if (!state.allSatellites) {
            Text(
                state.selectedName?.let { stringResource(R.string.planner_selected_is, it) }
                    ?: stringResource(R.string.planner_no_satellite),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        DropdownField(
            label = stringResource(R.string.planner_window),
            options = SettingsRepository.PLANNER_HOUR_CHOICES,
            selected = state.hours,
            optionLabel = { stringResource(R.string.overview_hours_option, it) },
            onSelected = viewModel::setHours,
        )
        Button(onClick = viewModel::refresh, enabled = state.chosen != null && !state.searching) {
            Text(stringResource(R.string.overview_refresh))
        }
    }
}

@Composable
private fun StatusLine(state: PlannerUiState) {
    val progress = state.progress
    val text = when {
        progress != null -> pluralStringResource(R.plurals.planner_progress, progress.second, progress.first, progress.second)
        state.searching -> stringResource(R.string.planner_searching)
        else -> ""
    }
    if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodySmall)
}

/** The results: stationary satellites usable now first, then the windows, or a message when there are none. */
private fun androidx.compose.foundation.lazy.LazyListScope.resultItems(state: PlannerUiState, formats: DisplayFormats) {
    item(key = "results-title") { SectionHeading(stringResource(R.string.planner_section_results)) }
    if (state.usableNow.isNotEmpty()) {
        item(key = "usable-title") {
            Text(
                stringResource(R.string.planner_usable_now_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
        }
        items(state.usableNow, key = { "usable-${it.catnum}" }) { UsableNowCard(it, formats) }
    }
    items(state.windows, key = { "${it.catnum}-${it.start.toEpochMilli()}" }) { window ->
        WindowCard(window, showName = state.allSatellites, formats)
    }
    if (state.done && state.windows.isEmpty() && state.usableNow.isEmpty()) {
        item(key = "none") {
            val distance = state.distanceKm
            Text(
                if (distance != null) {
                    pluralStringResource(R.plurals.planner_none_distance, state.hours, state.hours, formats.integer(distance))
                } else {
                    pluralStringResource(R.plurals.planner_none, state.hours, state.hours)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun UsableNowCard(item: PlannerItem.UsableNow, formats: DisplayFormats) {
    Card(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                stringResource(
                    R.string.planner_usable_now_line,
                    formats.integer(item.home.azimuthDeg), formats.oneDecimal(item.home.elevationDeg),
                    formats.integer(item.other.azimuthDeg), formats.oneDecimal(item.other.elevationDeg),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** One window: its date and times in the device's time zone, and its best moment in local time and in UTC. */
@Composable
private fun WindowCard(window: MutualWindow, showName: Boolean, formats: DisplayFormats) {
    val minutes = Duration.between(window.start, window.end).seconds / 60.0
    Card(modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showName) Text(window.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                stringResource(
                    R.string.planner_window_when,
                    formats.date(window.start), formats.time(window.start), formats.time(window.end), formats.oneDecimal(minutes),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(
                    R.string.planner_window_best,
                    formats.time(window.best.time), formats.utcTime(window.best.time),
                    formats.oneDecimal(window.best.lowerElevationDeg),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
