// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui.components

import android.content.Intent
import android.location.LocationManager
import android.os.CancellationSignal
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.LocatorSync
import uk.me.g4dpz.HamSatDroid.location.LocationFetcher

/** What the location editor is doing. */
private sealed interface LocationUi {
    data object Idle : LocationUi
    data class Fetching(val provider: String) : LocationUi
    data class Message(val text: String, val openLocationSettings: Boolean = false) : LocationUi
}

/**
 * Editing the home location: the IARU locator, latitude and longitude fields kept in step by [LocatorSync], and the
 * Use GPS and Use network location buttons (one fix each, asking for permission first if needed). Used by the
 * Settings screen and by the first-run setup guide, so both behave the same.
 *
 * Changes are saved as soon as they are made. A field is checked when the user finishes editing it (keyboard "Done",
 * or moving to another field). A rejected value is reported through [onMessage] (the caller shows it, for example in a
 * snackbar); the progress and result of getting a position are shown in dialogs owned by this editor.
 */
@Composable
fun LocationEditor(
    settings: SettingsRepository,
    locationFetcher: LocationFetcher,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val storedLat by settings.homeLat.state.collectAsStateWithLifecycle()
    val storedLon by settings.homeLon.state.collectAsStateWithLifecycle()
    val storedLocator by settings.homeLocator.state.collectAsStateWithLifecycle()

    // What the user is typing; reset whenever the stored value changes (after a check, or a position from the device).
    var locatorDraft by rememberSaveable(storedLocator) { mutableStateOf(storedLocator) }
    var latDraft by rememberSaveable(storedLat) { mutableStateOf(storedLat) }
    var lonDraft by rememberSaveable(storedLon) { mutableStateOf(storedLon) }

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
                onMessage(
                    when (result.field) {
                        LocatorSync.Field.LATITUDE -> invalidLatitude
                        LocatorSync.Field.LONGITUDE -> invalidLongitude
                        LocatorSync.Field.LOCATOR -> invalidLocator
                    }
                )
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
                    val accuracy = outcome.accuracyMeters
                        ?.let { resources.getQuantityString(R.plurals.location_accuracy, it.toInt(), it.toInt()) }
                        ?: unknownAccuracy
                    val set = resources.getString(
                        R.string.location_set, location.latitude, location.longitude, location.locator,
                    )
                    LocationUi.Message(resources.getString(R.string.location_set_with_accuracy, set, accuracy))
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
