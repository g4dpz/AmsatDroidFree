// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.data.KepsSource
import uk.me.g4dpz.HamSatDroid.domain.ElementAge
import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import uk.me.g4dpz.HamSatDroid.ui.components.DropdownField
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private const val HELP_URL = "https://sites.google.com/site/hamsatdroid/installation-and-user-guide-1"

/** The range choices, in the same order as the labels in `R.array.Times`. */
private val RANGE_HOURS = listOf(0, 6, 12, 24)

/** A message dialog raised by a keps event. */
private data class KepsDialog(val text: String)

/**
 * The main screen: the app bar, its menu and dialogs, and a body that depends on [layout]: the pass list alone, the
 * list beside the sky view, or the list beside the sky view and the map. Everything shown comes from [AppViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    layout: WindowLayout,
    keepScreenOn: Boolean,
    onOpenPass: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOverview: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formats = rememberDisplayFormats()
    val snackbar = remember { SnackbarHostState() }
    val alerts = rememberAlertController(snackbar)

    var menuOpen by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var kepsDialog by remember { mutableStateOf<KepsDialog?>(null) }

    // One-off messages from the view model
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.KepsUpdated ->
                    snackbar.showSnackbar(resources.getQuantityString(R.plurals.keps_updated, event.satelliteCount, event.satelliteCount))
                is UiEvent.KepsTooSoon -> {
                    val sourceName = resources.getString(event.source.labelRes)
                    kepsDialog = KepsDialog(
                        if (event.lastSuccessMillis != null) {
                            resources.getString(
                                R.string.keps_too_soon, sourceName,
                                formats.dateTime(event.lastSuccessMillis), formats.dateTime(event.nextAllowedAtMillis),
                            )
                        } else {
                            resources.getString(
                                R.string.keps_too_soon_no_time, sourceName, formats.dateTime(event.nextAllowedAtMillis),
                            )
                        }
                    )
                }
                is UiEvent.KepsFailed -> kepsDialog = KepsDialog(resources.getString(R.string.keps_failed, event.reason))
            }
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_set_home_location)) },
                                onClick = { menuOpen = false; onOpenSettings() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_overview)) },
                                enabled = state.satellites.isNotEmpty(),
                                onClick = { menuOpen = false; onOpenOverview() },
                            )
                            if (layout != WindowLayout.DASHBOARD) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_map_view)) },
                                    enabled = state.selectedSatellite != null,
                                    onClick = { menuOpen = false; onOpenMap() },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.menu_update_keps),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                enabled = false,
                                onClick = {},
                            )
                            for (source in KepsSource.entries) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(source.labelRes)) },
                                    onClick = { menuOpen = false; viewModel.refreshKeps(source) },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_help)) },
                                onClick = {
                                    menuOpen = false
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(HELP_URL)))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_about)) },
                                onClick = { menuOpen = false; showAbout = true },
                            )
                        }
                    },
                )
                if (state.downloadingKeps) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val onPassClick: (PassSummary) -> Unit = { pass ->
            viewModel.selectPass(pass.id)
            if (layout == WindowLayout.SINGLE_PANE) onOpenPass()
        }
        val onPassLongClick: (PassSummary) -> Unit = { pass -> alerts.toggle(pass) }
        val paneModifier = Modifier.padding(horizontal = 16.dp)
        when (layout) {
            WindowLayout.SINGLE_PANE -> PassesPane(
                viewModel, state, formats, alerts, highlightSelection = false,
                onChangeStartTime = { showStartPicker = true }, onPassClick = onPassClick, onPassLongClick = onPassLongClick,
                modifier = Modifier.fillMaxSize().padding(padding).then(paneModifier),
            )
            WindowLayout.TWO_PANE -> Row(Modifier.fillMaxSize().padding(padding)) {
                PassesPane(
                    viewModel, state, formats, alerts, highlightSelection = true,
                    onChangeStartTime = { showStartPicker = true }, onPassClick = onPassClick, onPassLongClick = onPassLongClick,
                    modifier = Modifier.weight(0.4f).fillMaxHeight().then(paneModifier),
                )
                VerticalDivider()
                SkyContent(
                    viewModel, state, keepScreenOn,
                    modifier = Modifier.weight(0.6f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp),
                )
            }
            WindowLayout.DASHBOARD -> Row(Modifier.fillMaxSize().padding(padding)) {
                PassesPane(
                    viewModel, state, formats, alerts, highlightSelection = true,
                    onChangeStartTime = { showStartPicker = true }, onPassClick = onPassClick, onPassLongClick = onPassLongClick,
                    modifier = Modifier.weight(0.3f).fillMaxHeight().then(paneModifier),
                )
                VerticalDivider()
                BoxWithConstraints(Modifier.weight(0.7f).fillMaxHeight()) {
                    val arrangement = DashboardLayout.choose(maxWidth.value, maxHeight.value)
                    val gap = DashboardLayout.GAP_DP.dp
                    if (arrangement == ChartArrangement.SIDE_BY_SIDE) {
                        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                            SkyContent(
                                viewModel, state, keepScreenOn,
                                modifier = Modifier.weight(0.4f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            )
                            MapContent(
                                viewModel, state, keepScreenOn,
                                modifier = Modifier.weight(0.6f).fillMaxHeight().verticalScroll(rememberScrollState()),
                            )
                        }
                    } else {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            MapContent(viewModel, state, keepScreenOn, Modifier.fillMaxWidth())
                            SkyContent(viewModel, state, keepScreenOn, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        StartTimePicker(
            initial = state.startTime ?: Instant.now(),
            onDismiss = { showStartPicker = false },
            onSet = {
                showStartPicker = false
                viewModel.setStartTime(it)
            },
        )
    }

    kepsDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { kepsDialog = null },
            text = { Text(dialog.text) },
            confirmButton = { TextButton(onClick = { kepsDialog = null }) { Text(stringResource(R.string.action_ok)) } },
        )
    }

    if (showAbout) AboutDialog(state.keps, formats, onDismiss = { showAbout = false })
}

/**
 * Satellite and range choices, the start time, the calculate button, and the list of passes.
 *
 * On a tall pane the controls stay pinned above a scrolling list. On a short one (a phone in landscape) pinned controls
 * would leave the list a single line, so the controls become the first item of one scrolling list; see
 * [PassesPaneLayout]. The two pickers stack when the pane is narrow, so their labels and names are not clipped.
 */
@Composable
private fun PassesPane(
    viewModel: AppViewModel,
    state: AppUiState,
    formats: DisplayFormats,
    alerts: AlertController,
    highlightSelection: Boolean,
    onChangeStartTime: () -> Unit,
    onPassClick: (PassSummary) -> Unit,
    onPassLongClick: (PassSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val stackPickers = PassesPaneLayout.stackPickers(maxWidth.value)
        val scrollWholePane = PassesPaneLayout.scrollWholePane(maxHeight.value)
        val controls: @Composable () -> Unit = {
            PassesControls(viewModel, state, formats, stackPickers, onChangeStartTime)
        }
        val passes: LazyListScope.() -> Unit = {
            items(state.passes, key = { it.id }) { pass ->
                PassCard(
                    pass, formats,
                    selected = highlightSelection && pass.id == state.selectedPassId,
                    hasAlert = alerts.hasAlert(pass),
                    onClick = { onPassClick(pass) },
                    onLongClick = { onPassLongClick(pass) },
                )
            }
        }

        if (scrollWholePane) {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "controls") { controls() }
                passes()
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                controls()
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) { passes() }
            }
        }
    }
}

/** The part of the passes pane above the list: pickers, start time, Calculate, the stale warning and the heading. */
@Composable
private fun PassesControls(
    viewModel: AppViewModel,
    state: AppUiState,
    formats: DisplayFormats,
    stackPickers: Boolean,
    onChangeStartTime: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val rangeLabels = stringArrayResource(R.array.Times)
        val satellitePicker: @Composable (Modifier) -> Unit = { pickerModifier ->
            DropdownField(
                label = stringResource(R.string.passes_satellite_label),
                options = state.satellites,
                selected = state.selectedSatellite,
                optionLabel = { it.name },
                onSelected = { viewModel.selectSatellite(it.catnum) },
                modifier = pickerModifier,
                enabled = !state.loading,
            )
        }
        val rangePicker: @Composable (Modifier) -> Unit = { pickerModifier ->
            DropdownField(
                label = stringResource(R.string.passes_range_label),
                options = RANGE_HOURS,
                selected = state.hoursAhead,
                optionLabel = { rangeLabels.getOrElse(RANGE_HOURS.indexOf(it)) { it.toString() } },
                onSelected = viewModel::setHoursAhead,
                modifier = pickerModifier,
            )
        }
        if (stackPickers) {
            satellitePicker(Modifier.fillMaxWidth())
            rangePicker(Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                satellitePicker(Modifier.weight(1f))
                rangePicker(Modifier.weight(1f))
            }
        }

        // The start time gets a line of its own and the buttons sit below it. In one row the buttons took the width
        // they needed and squeezed the text into a narrow column (one or two characters a line on a phone with a
        // larger font). The buttons wrap onto a further line when even they do not fit side by side.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.startTime?.let { stringResource(R.string.passes_starting_at, formats.dateTime(it)) }
                    ?: stringResource(R.string.passes_starting_now),
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onChangeStartTime) { Text(stringResource(R.string.passes_change_start_time)) }
                if (state.startTime != null) {
                    TextButton(onClick = { viewModel.setStartTime(null) }) {
                        Text(stringResource(R.string.passes_use_current_time))
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = viewModel::calculate, enabled = state.selectedCatnum != null && !state.calculating) {
                Text(stringResource(R.string.passes_calculate))
            }
            if (state.calculating) CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        }

        // Old elements give wrong predictions; measured from the prediction start, which may be in the future
        state.selectedSatellite?.let { sat ->
            val reference = state.startTime?.toEpochMilli() ?: System.currentTimeMillis()
            ElementAge.staleDays(sat.epochMillis, reference)?.let { days ->
                Text(
                    pluralStringResource(R.plurals.passes_elements_stale, days, sat.name, days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        when {
            state.loading -> Text(stringResource(R.string.passes_loading))
            state.header != null -> Text(state.header.text(formats), style = MaterialTheme.typography.bodyMedium)
            else -> Text(stringResource(R.string.passes_none_yet), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PassCard(
    pass: PassSummary,
    formats: DisplayFormats,
    selected: Boolean,
    hasAlert: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (line in pass.lines(formats)) Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            if (hasAlert) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = stringResource(R.string.alert_bell),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Two steps: pick the date, then the time. The result is in the device's time zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimePicker(initial: Instant, onDismiss: () -> Unit, onSet: (Instant) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val initialLocal = LocalDateTime.ofInstant(initial, zone)
    // The date picker works in UTC midnight milliseconds of the chosen calendar day.
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialLocal.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    )
    val timeState = rememberTimePickerState(
        initialHour = initialLocal.hour, initialMinute = initialLocal.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    var step by remember { mutableIntStateOf(0) }

    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { step = 1 }, enabled = dateState.selectedDateMillis != null) {
                    Text(stringResource(R.string.picker_next))
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        ) { DatePicker(state = dateState) }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) { TimePicker(state = timeState) }
            },
            confirmButton = {
                TextButton(onClick = {
                    val day = Instant.ofEpochMilli(dateState.selectedDateMillis ?: 0L).atZone(ZoneOffset.UTC).toLocalDate()
                    val local = LocalDateTime.of(day, java.time.LocalTime.of(timeState.hour, timeState.minute))
                    onSet(local.atZone(zone).toInstant())
                }) { Text(stringResource(R.string.picker_set)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
fun AboutDialog(keps: KepsInfo?, formats: DisplayFormats, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            ""
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_dismiss)) } },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.AboutString).trimIndent().trim(), style = MaterialTheme.typography.bodyMedium)
                if (keps != null) {
                    Text(
                        if (keps.isBundled) {
                            stringResource(R.string.about_keps_bundled)
                        } else {
                            val source = KepsSource.entries.firstOrNull { it.key == keps.source }
                                ?.let { stringResource(it.labelRes) } ?: keps.source
                            pluralStringResource(
                                R.plurals.about_keps_downloaded, keps.satelliteCount,
                                source, keps.fetchedAtMillis?.let { formats.dateTime(it) } ?: "", keps.satelliteCount,
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    keps.lastFailure?.let {
                        Text(
                            stringResource(R.string.about_keps_failure, formats.dateTime(it.millis), it.reason),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Text(stringResource(R.string.about_transponder_db), style = MaterialTheme.typography.bodySmall)
            }
        },
    )
}
