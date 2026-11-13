// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.Countdown
import uk.me.g4dpz.HamSatDroid.domain.OverviewRows
import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import uk.me.g4dpz.HamSatDroid.domain.UpNowRow
import uk.me.g4dpz.HamSatDroid.ui.components.DropdownField
import uk.me.g4dpz.HamSatDroid.ui.components.rememberTicker
import java.time.Duration
import java.time.Instant

/** How often the countdowns on the screen are redrawn. */
private const val COUNTDOWN_TICK_MILLIS = 1_000L

/**
 * Every satellite at once: those up now (with where to point) and the passes coming up in the next hours, sorted by
 * AOS. Rows appear as each satellite's calculation finishes. Tapping a row shows that pass in the sky view.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onOpenPass: (PassSummary) -> Unit,
    onBack: () -> Unit,
    /** Opens the setup guide; offered while no home location is set. */
    onOpenSetup: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val upNow by viewModel.upNow.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val alerts = rememberAlertController(snackbar)
    val tick by rememberTicker(COUNTDOWN_TICK_MILLIS)
    val now = Instant.ofEpochMilli(tick)
    val formats = rememberDisplayFormats()
    // Rebuilt only when the results or the filter change, or a pass ends (checked each second against `now`)
    val upcoming = remember(state.results, state.minElevation, tick / 1000) {
        OverviewRows.upcoming(state.results.values, state.minElevation, now)
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.overview_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::refresh, enabled = !state.calculating) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.overview_refresh))
                        }
                    },
                )
                if (state.calculating && state.total > 0) {
                    LinearProgressIndicator(
                        progress = { state.done.toFloat() / state.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "controls") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DropdownField(
                        label = stringResource(R.string.overview_window),
                        options = SettingsRepository.OVERVIEW_HOUR_CHOICES,
                        selected = state.hours,
                        optionLabel = { stringResource(R.string.overview_hours_option, it) },
                        onSelected = viewModel::setHours,
                        modifier = Modifier.weight(1f),
                    )
                    DropdownField(
                        label = stringResource(R.string.overview_min_elevation),
                        options = SettingsRepository.OVERVIEW_MIN_ELEVATION_CHOICES,
                        selected = state.minElevation,
                        optionLabel = { stringResource(R.string.overview_degrees_option, it) },
                        onSelected = viewModel::setMinElevation,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item(key = "status") {
                Text(
                    text = when {
                        !state.locationValid -> stringResource(R.string.overview_no_location)
                        state.calculating -> pluralStringResource(R.plurals.overview_progress, state.total, state.done, state.total)
                        state.elapsedMillis != null -> pluralStringResource(
                            R.plurals.overview_done, state.total, state.total, formats.oneDecimal(state.elapsedMillis!! / 1000.0),
                        )
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (!state.locationValid) {
                item(key = "set-up") { Button(onClick = onOpenSetup) { Text(stringResource(R.string.banner_set_up)) } }
            }
            item(key = "up-now-title") { SectionHeading(stringResource(R.string.overview_up_now)) }
            if (upNow.isEmpty()) {
                item(key = "up-now-none") {
                    Text(stringResource(R.string.overview_none_up), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(upNow, key = { "now-${it.catnum}" }) { row ->
                UpNowCard(row, now, formats, onClick = row.pass?.let { pass -> { onOpenPass(pass) } })
            }

            item(key = "upcoming-title") {
                SectionHeading(stringResource(R.string.overview_upcoming, state.hours))
            }
            if (upcoming.isEmpty() && !state.calculating) {
                item(key = "upcoming-none") {
                    Text(stringResource(R.string.overview_none_upcoming), style = MaterialTheme.typography.bodyMedium)
                }
            }
            items(upcoming, key = { it.id }) { pass ->
                UpcomingCard(
                    pass, now, formats, hasAlert = alerts.hasAlert(pass),
                    onClick = { onOpenPass(pass) }, onLongClick = { alerts.toggle(pass) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun UpNowCard(row: UpNowRow, now: Instant, formats: DisplayFormats, onClick: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(row.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(
                        R.string.overview_az_el,
                        formats.integer(row.position.azimuth), formats.integer(row.position.elevation),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    row.los?.let { stringResource(R.string.overview_los_in, Countdown.format(Duration.between(now, it))) }
                        ?: stringResource(R.string.overview_always_up),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UpcomingCard(
    pass: PassSummary,
    now: Instant,
    formats: DisplayFormats,
    hasAlert: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val countdown = if (pass.start.isAfter(now)) {
        stringResource(R.string.overview_aos_in, Countdown.format(Duration.between(now, pass.start)))
    } else {
        stringResource(R.string.overview_aos_ago, Countdown.format(Duration.between(pass.start, now)))
    }
    Card(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    pass.satName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                if (hasAlert) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.alert_bell),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(countdown, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                stringResource(
                    R.string.overview_pass_line,
                    formats.time(pass.start), formats.oneDecimal(pass.duration.seconds / 60.0),
                    formats.oneDecimal(pass.maxElevationDeg),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
