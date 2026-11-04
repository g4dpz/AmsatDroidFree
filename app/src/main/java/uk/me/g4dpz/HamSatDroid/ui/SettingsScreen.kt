package uk.me.g4dpz.HamSatDroid.ui

import android.content.Intent
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.data.KepsSource
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.LocatorSync
import uk.me.g4dpz.HamSatDroid.location.LocationFetcher
import uk.me.g4dpz.HamSatDroid.aim.PointingAxis
import uk.me.g4dpz.HamSatDroid.night.ThemeMode
import uk.me.g4dpz.HamSatDroid.night.labelRes
import uk.me.g4dpz.HamSatDroid.ui.components.DropdownField
import uk.me.g4dpz.HamSatDroid.ui.components.SwitchRow

/** What the location part of the screen is doing. */
private sealed interface LocationUi {
    data object Idle : LocationUi
    data class Fetching(val provider: String) : LocationUi
    data class Message(val text: String, val openLocationSettings: Boolean = false) : LocationUi
}

/**
 * The settings screen: home location (locator, latitude, longitude, and getting them from the device), keps update
 * options, display options and a note about background restrictions.
 *
 * Changes are saved as soon as they are made. The location fields are checked when the user finishes editing a field
 * (keyboard "Done", or moving to another field), by [LocatorSync].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    locationFetcher: LocationFetcher,
    hasLightSensor: Boolean,
    onLeadTimeChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }

    val storedLat by settings.homeLat.state.collectAsStateWithLifecycle()
    val storedLon by settings.homeLon.state.collectAsStateWithLifecycle()
    val storedLocator by settings.homeLocator.state.collectAsStateWithLifecycle()
    val autoRefresh by settings.autoKepsRefresh.state.collectAsStateWithLifecycle()
    val kepsSource by settings.kepsSource.state.collectAsStateWithLifecycle()
    val keepScreenOn by settings.keepScreenOn.state.collectAsStateWithLifecycle()
    val alertLead by settings.alertLeadMinutes.state.collectAsStateWithLifecycle()
    val spokenAlerts by settings.spokenAlerts.state.collectAsStateWithLifecycle()
    val pointingAxis by settings.pointingAxis.state.collectAsStateWithLifecycle()
    val themeMode by settings.themeMode.state.collectAsStateWithLifecycle()
    val autoRedNight by settings.autoRedNight.state.collectAsStateWithLifecycle()

    // What the user is typing; reset whenever the stored value changes (after a check, or a position from the device).
    var locatorDraft by rememberSaveable(storedLocator) { mutableStateOf(storedLocator) }
    var latDraft by rememberSaveable(storedLat) { mutableStateOf(storedLat) }
    var lonDraft by rememberSaveable(storedLon) { mutableStateOf(storedLon) }

    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    fun current() = LocatorSync.Location(storedLat, storedLon, storedLocator)

    fun save(location: LocatorSync.Location) {
        settings.homeLat.value = location.latitude
        settings.homeLon.value = location.longitude
        settings.homeLocator.value = location.locator
    }

    val invalidLatitude = stringResource(R.string.error_invalid_latitude)
    val invalidLongitude = stringResource(R.string.error_invalid_longitude)
    val invalidLocator = stringResource(R.string.error_invalid_locator, LocatorSync.DEFAULT_LOCATOR)

    fun commit(result: LocatorSync.Result) {
        when (result) {
            is LocatorSync.Result.Accepted -> save(result.location)
            is LocatorSync.Result.Rejected -> {
                save(result.location)
                message = when (result.field) {
                    LocatorSync.Field.LATITUDE -> invalidLatitude
                    LocatorSync.Field.LONGITUDE -> invalidLongitude
                    LocatorSync.Field.LOCATOR -> invalidLocator
                }
                // The stored value may be unchanged (already 0); make the field show it again anyway.
                when (result.field) {
                    LocatorSync.Field.LATITUDE -> latDraft = result.location.latitude
                    LocatorSync.Field.LONGITUDE -> lonDraft = result.location.longitude
                    LocatorSync.Field.LOCATOR -> locatorDraft = result.location.locator
                }
            }
        }
    }

    // ---- getting the position from the device ----
    var locationUi by remember { mutableStateOf<LocationUi>(LocationUi.Idle) }
    var cancelSignal by remember { mutableStateOf<CancellationSignal?>(null) }
    var pendingProvider by remember { mutableStateOf<String?>(null) }

    val gpsName = stringResource(R.string.location_provider_gps)
    val networkName = stringResource(R.string.location_provider_network)
    val notFound = stringResource(R.string.location_not_found)
    val permissionNeeded = stringResource(R.string.location_permission_needed)
    val unknownAccuracy = stringResource(R.string.location_accuracy_unknown)

    fun startFetch(provider: String) {
        val signal = CancellationSignal()
        cancelSignal = signal
        locationUi = LocationUi.Fetching(provider)
        locationFetcher.request(provider, signal) { outcome ->
            cancelSignal = null
            locationUi = when (outcome) {
                is LocationFetcher.Outcome.Found -> {
                    val location = LocatorSync.fromCoordinates(outcome.latitude, outcome.longitude)
                    save(location)
                    val accuracy = outcome.accuracyMeters?.let { resources.getString(R.string.location_accuracy, it.toInt()) }
                        ?: unknownAccuracy
                    LocationUi.Message(
                        resources.getString(R.string.location_set, location.latitude, location.longitude, location.locator) +
                            " " + accuracy
                    )
                }
                LocationFetcher.Outcome.ProviderDisabled ->
                    LocationUi.Message(
                        resources.getString(
                            R.string.location_provider_disabled,
                            if (provider == LocationManager.GPS_PROVIDER) gpsName else networkName
                        ),
                        openLocationSettings = true,
                    )
                LocationFetcher.Outcome.NoFix -> LocationUi.Message(notFound)
                LocationFetcher.Outcome.NoPermission -> LocationUi.Message(permissionNeeded)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val provider = pendingProvider
        pendingProvider = null
        if (provider != null) {
            if (locationFetcher.missingPermissions(provider).isEmpty()) startFetch(provider)
            else locationUi = LocationUi.Message(permissionNeeded)
        }
    }

    fun useProvider(provider: String) {
        val missing = locationFetcher.missingPermissions(provider)
        if (missing.isEmpty()) {
            startFetch(provider)
        } else {
            pendingProvider = provider
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(R.string.settings_section_location)
            Text(stringResource(R.string.settings_location_help), style = MaterialTheme.typography.bodyMedium)

            // The locator comes first: it is the one way to set the location that works on every device.
            CommitField(
                label = stringResource(R.string.settings_locator_label),
                value = locatorDraft,
                onValueChange = { locatorDraft = it },
                keyboardType = KeyboardType.Ascii,
                onCommit = { if (locatorDraft != storedLocator) commit(LocatorSync.editLocator(locatorDraft, current())) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                CommitField(
                    label = stringResource(R.string.settings_latitude_label),
                    value = latDraft,
                    onValueChange = { latDraft = it },
                    keyboardType = KeyboardType.Ascii, // the numeric keypad has no minus sign
                    modifier = Modifier.weight(1f),
                    onCommit = { if (latDraft != storedLat) commit(LocatorSync.editLatitude(latDraft, current())) },
                )
                CommitField(
                    label = stringResource(R.string.settings_longitude_label),
                    value = lonDraft,
                    onValueChange = { lonDraft = it },
                    keyboardType = KeyboardType.Ascii, // the numeric keypad has no minus sign
                    modifier = Modifier.weight(1f),
                    onCommit = { if (lonDraft != storedLon) commit(LocatorSync.editLongitude(lonDraft, current())) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (locationFetcher.hasGps) {
                    OutlinedButton(onClick = { useProvider(LocationManager.GPS_PROVIDER) }) {
                        Text(stringResource(R.string.settings_use_gps))
                    }
                }
                OutlinedButton(onClick = { useProvider(LocationManager.NETWORK_PROVIDER) }) {
                    Text(stringResource(R.string.settings_use_network))
                }
            }
            if (!locationFetcher.hasGps) {
                Text(stringResource(R.string.settings_no_gps), style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider()
            SectionTitle(R.string.settings_section_keps)
            SwitchRow(
                label = stringResource(R.string.settings_auto_refresh),
                checked = autoRefresh,
                onCheckedChange = { settings.autoKepsRefresh.value = it },
            )
            DropdownField(
                label = stringResource(R.string.settings_keps_source),
                options = KepsSource.entries,
                selected = kepsSource,
                optionLabel = { stringResource(it.labelRes) },
                onSelected = { settings.kepsSource.value = it },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            SectionTitle(R.string.settings_section_display)
            DropdownField(
                label = stringResource(R.string.settings_theme),
                options = ThemeMode.entries,
                selected = themeMode,
                optionLabel = { stringResource(it.labelRes) },
                onSelected = { settings.themeMode.value = it },
                modifier = Modifier.fillMaxWidth(),
            )
            // Only offered when there is a light sensor to switch by
            if (hasLightSensor) {
                SwitchRow(
                    label = stringResource(R.string.settings_auto_red_night),
                    checked = autoRedNight,
                    onCheckedChange = { settings.autoRedNight.value = it },
                )
            }
            SwitchRow(
                label = stringResource(R.string.settings_keep_screen_on),
                checked = keepScreenOn,
                onCheckedChange = { settings.keepScreenOn.value = it },
            )
            DropdownField(
                label = stringResource(R.string.settings_pointing_axis),
                options = PointingAxis.entries,
                selected = pointingAxis,
                optionLabel = { stringResource(if (it == PointingAxis.TOP_EDGE) R.string.pointing_top_edge else R.string.pointing_back) },
                onSelected = { settings.pointingAxis.value = it },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()
            SectionTitle(R.string.settings_section_alerts)
            DropdownField(
                label = stringResource(R.string.settings_alert_lead),
                options = SettingsRepository.ALERT_LEAD_CHOICES,
                selected = alertLead,
                optionLabel = { stringResource(R.string.settings_alert_lead_option, it) },
                onSelected = { settings.alertLeadMinutes.value = it; onLeadTimeChanged() },
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(
                label = stringResource(R.string.settings_spoken_alerts),
                checked = spokenAlerts,
                onCheckedChange = { settings.spokenAlerts.value = it },
            )

            HorizontalDivider()
            SectionTitle(R.string.settings_section_background)
            Text(stringResource(R.string.settings_background_text), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }) { Text(stringResource(R.string.settings_open_app_settings)) }
        }
    }

    when (val ui = locationUi) {
        LocationUi.Idle -> Unit
        is LocationUi.Fetching -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {
                TextButton(onClick = {
                    cancelSignal?.cancel()
                    cancelSignal = null
                    locationUi = LocationUi.Idle
                }) { Text(stringResource(R.string.action_cancel)) }
            },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.settings_getting_location))
                }
            },
        )
        is LocationUi.Message -> AlertDialog(
            onDismissRequest = { locationUi = LocationUi.Idle },
            text = { Text(ui.text) },
            confirmButton = {
                if (ui.openLocationSettings) {
                    Button(onClick = {
                        locationUi = LocationUi.Idle
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }) { Text(stringResource(R.string.action_open_settings)) }
                } else {
                    TextButton(onClick = { locationUi = LocationUi.Idle }) { Text(stringResource(R.string.action_ok)) }
                }
            },
            dismissButton = if (ui.openLocationSettings) {
                { TextButton(onClick = { locationUi = LocationUi.Idle }) { Text(stringResource(R.string.action_cancel)) } }
            } else null,
        )
    }
}

@Composable
private fun SectionTitle(textRes: Int) {
    Text(stringResource(textRes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/** A text field that reports when the user has finished with it: keyboard "Done", or focus moving elsewhere. */
@Composable
private fun CommitField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var hadFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit() }),
        modifier = modifier.fillMaxWidth().onFocusChanged { state ->
            if (hadFocus && !state.isFocused) onCommit()
            hadFocus = state.isFocused
        },
    )
}
