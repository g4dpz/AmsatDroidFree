// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.data.SavedStation
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.LocatorSync
import uk.me.g4dpz.HamSatDroid.domain.StationEditing
import uk.me.g4dpz.HamSatDroid.ui.components.CommitField
import java.util.UUID

private val MAX_CONTENT_WIDTH = 600.dp

/** Keeps the position being edited across rotation and the process being recreated. */
private val DRAFT_SAVER = listSaver<StationEditing.Draft, String?>(
    save = {
        listOf(
            it.location.latitude, it.location.longitude, it.location.locator,
            it.latitude, it.longitude, it.locator, it.squareNote,
        )
    },
    restore = {
        StationEditing.Draft(
            LocatorSync.Location(it[0]!!, it[1]!!, it[2]!!),
            locator = it[5]!!, latitude = it[3]!!, longitude = it[4]!!, squareNote = it[6],
        )
    },
)

/**
 * Adds a station for the contact planner, or edits one ([stationId]) with Delete. A full screen, not a dialog, so there
 * is room at a large font size and the fields work like the home location in Settings: a locator, or a latitude and
 * longitude, each filling in the others, and a 4-character square such as EM29 accepted (it uses the square's centre).
 * There are no "use my position" buttons: the other station is not where this device is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerStationScreen(settings: SettingsRepository, stationId: String?, onDone: () -> Unit) {
    val existing = remember(stationId) { stationId?.let { id -> settings.plannerStations.value.firstOrNull { it.id == id } } }
    // Opened for a station that is not there any more: nothing to edit
    if (stationId != null && existing == null) {
        LaunchedEffect(Unit) { onDone() }
        return
    }

    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    // The position being edited: what is committed and what each field shows (see StationEditing.Draft)
    var draft by rememberSaveable(stateSaver = DRAFT_SAVER) {
        mutableStateOf(
            StationEditing.Draft(
                LocatorSync.Location(
                    existing?.latitude ?: LocatorSync.ZERO, existing?.longitude ?: LocatorSync.ZERO,
                    existing?.locator ?: LocatorSync.DEFAULT_LOCATOR,
                )
            )
        )
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }
    val invalidLatitude = stringResource(R.string.error_invalid_latitude)
    val invalidLongitude = stringResource(R.string.error_invalid_longitude)
    val invalidLocator = stringResource(R.string.error_invalid_locator, LocatorSync.DEFAULT_LOCATOR)
    val needPosition = stringResource(R.string.station_need_position)
    val full = pluralStringResource(R.plurals.station_full, SavedStation.MAX_STATIONS, SavedStation.MAX_STATIONS)

    /** Takes over the result of a commit; a rejected value is reported (its field shows the reset value). */
    fun commit(committed: StationEditing.Draft.Committed) {
        draft = committed.draft
        committed.rejected?.let {
            message = when (it) {
                LocatorSync.Field.LATITUDE -> invalidLatitude
                LocatorSync.Field.LONGITUDE -> invalidLongitude
                LocatorSync.Field.LOCATOR -> invalidLocator
            }
        }
    }

    fun save() {
        // The field typed in last has not been committed if the user never left it
        val committed = draft.commitPending()
        commit(committed)
        if (committed.rejected != null) return
        val position = committed.draft.location
        if (!committed.draft.canSave) {
            message = needPosition
            return
        }
        val station = SavedStation(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = name.trim(),
            latitude = position.latitude,
            longitude = position.longitude,
            locator = position.locator,
        )
        val updated = StationEditing.saved(settings.plannerStations.value, station)
        if (updated == null) {
            message = full
            return
        }
        settings.plannerStations.value = updated
        // A station just added is the one to use
        if (existing == null) settings.plannerStationId.value = station.id
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.station_title_add else R.string.station_title_edit)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = SavedStation.limitName(it) },
                    label = { Text(stringResource(R.string.station_name_label)) },
                    supportingText = {
                        Text(pluralStringResource(R.plurals.station_name_support, SavedStation.MAX_NAME_LENGTH, SavedStation.MAX_NAME_LENGTH))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                CommitField(
                    label = stringResource(R.string.settings_locator_label),
                    value = draft.locator,
                    onValueChange = { draft = draft.typeLocator(it) },
                    keyboardType = KeyboardType.Ascii,
                    onCommit = { commit(draft.commitLocator()) },
                )
                draft.squareNote?.let { Text(stringResource(R.string.station_square_note, it), style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    CommitField(
                        label = stringResource(R.string.settings_latitude_label),
                        value = draft.latitude,
                        onValueChange = { draft = draft.typeLatitude(it) },
                        keyboardType = KeyboardType.Ascii, // the numeric keypad has no minus sign
                        modifier = Modifier.weight(1f),
                        onCommit = { commit(draft.commitLatitude()) },
                    )
                    CommitField(
                        label = stringResource(R.string.settings_longitude_label),
                        value = draft.longitude,
                        onValueChange = { draft = draft.typeLongitude(it) },
                        keyboardType = KeyboardType.Ascii,
                        modifier = Modifier.weight(1f),
                        onCommit = { commit(draft.commitLongitude()) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDone) { Text(stringResource(R.string.action_cancel)) }
                    Button(onClick = ::save) { Text(stringResource(R.string.station_save)) }
                    if (existing != null) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text(stringResource(R.string.station_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.station_delete_title)) },
            text = { Text(stringResource(R.string.station_delete_message, existing.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    settings.plannerStations.value = StationEditing.without(settings.plannerStations.value, existing.id)
                    if (settings.plannerStationId.value == existing.id) settings.plannerStationId.value = ""
                    onDone()
                }) { Text(stringResource(R.string.station_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
