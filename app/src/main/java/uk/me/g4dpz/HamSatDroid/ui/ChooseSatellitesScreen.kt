// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.SatelliteChooser

/**
 * Keeps the ticked catalogue numbers across rotation and the process being recreated. A marker is saved first so an
 * empty set is a non-empty saved list: an emptied draft must come back empty, not as the saved list.
 */
private val CATNUM_SET_SAVER = listSaver<Set<Int>, Int>(save = { listOf(0) + it.toList() }, restore = { it.drop(1).toSet() })

/**
 * Lets the user choose the satellites the app offers (the Satellite picker, the All satellites overview and the Contact
 * planner). A full screen with a search box. The ticks are a draft: Done saves them (an empty list too), Back or the
 * arrow leaves with nothing changed. Which satellites are listed first is decided when the screen opens, so ticking a
 * row does not make it jump.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseSatellitesScreen(viewModel: AppViewModel, settings: SettingsRepository, onLeave: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val initial = rememberSaveable(saver = CATNUM_SET_SAVER) {
        // Before the elements have loaded the state has no selection yet, so fall back to the saved one.
        val selected = state.selectedCatnum ?: settings.selectedCatnum.value.takeIf { it > 0 }
        SatelliteChooser.opening(settings.mySatellites.value, settings.mySatellitesChosen.value, selected)
    }
    var draft by rememberSaveable(stateSaver = CATNUM_SET_SAVER) { mutableStateOf(initial) }
    var query by rememberSaveable { mutableStateOf("") }

    val shown = remember(state.satellites, initial, query) { SatelliteChooser.shown(state.satellites, initial, query) }
    val absent = remember(state.satellites, draft, query) { SatelliteChooser.notInElements(state.satellites, draft, query) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_title)) },
                navigationIcon = {
                    IconButton(onClick = onLeave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        settings.mySatellites.value = draft
                        onLeave()
                    }) { Text(stringResource(R.string.choose_done)) }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.choose_search)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.choose_count, draft.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { draft = SatelliteChooser.selectAll(draft, shown) }) {
                    Text(stringResource(R.string.choose_select_all))
                }
                TextButton(onClick = { draft = SatelliteChooser.clearAll(draft, shown, absent) }) {
                    Text(stringResource(R.string.choose_clear_all))
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp), modifier = Modifier.fillMaxSize()) {
                if (shown.isEmpty() && absent.isEmpty()) {
                    item { Text(stringResource(R.string.choose_none_found), modifier = Modifier.padding(vertical = 16.dp)) }
                }
                items(shown, key = { it.catnum }) { item ->
                    ChooseRow(item.name, item.catnum, item.catnum in draft) { draft = SatelliteChooser.toggle(draft, item.catnum) }
                }
                if (absent.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.choose_not_in_elements),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(absent, key = { -it }) { catnum ->
                        ChooseRow(stringResource(R.string.choose_absent_row, catnum), catnum, catnum in draft) {
                            draft = SatelliteChooser.toggle(draft, catnum)
                        }
                    }
                }
            }
        }
    }
}

/** One checkbox row; the whole row is the tap target and reads as one checkbox to a screen reader. */
@Composable
private fun ChooseRow(name: String, catnum: Int, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(vertical = 8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(catnum.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
