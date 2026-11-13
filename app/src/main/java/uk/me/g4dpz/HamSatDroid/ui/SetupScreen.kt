// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.HomeLocation
import uk.me.g4dpz.HamSatDroid.location.LocationFetcher
import uk.me.g4dpz.HamSatDroid.ui.components.DropdownField
import uk.me.g4dpz.HamSatDroid.ui.components.LocationEditor

/** The widest the guide's content is drawn, so lines stay readable on a tablet in landscape (requirement 8.1). */
private val MAX_CONTENT_WIDTH = 600.dp

/** One step of the guide. The guide's length and "Step N of M" come from the list, so a step can be added later. */
private class SetupStep(
    val title: Int,
    /** Whether Next (or Done, on the last step) can be pressed. */
    val canContinue: Boolean,
    val content: @Composable () -> Unit,
)

/**
 * The first-run setup guide: where you are, then a first satellite (first-run setup spec, decision O2). It opens by
 * itself on a fresh install ([StartRoute]) and from the "Setup guide" menu item, the passes-screen banner and the
 * overview.
 *
 * Back, Skip and Next (Done on the last step) stay in the same places on every step. Back on the first step, System
 * Back there and Skip on any step all [onLeave]; Done reports the chosen satellite through [onDone]. Either way the
 * caller marks the guide finished and leaves it (requirement 7.5). The step survives rotation and the system ending the
 * process in the background (requirement 7.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    viewModel: AppViewModel,
    settings: SettingsRepository,
    locationFetcher: LocationFetcher,
    onLeave: () -> Unit,
    /** The satellite chosen on the last step, or null if there was none to choose from. */
    onDone: (catnum: Int?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lat by settings.homeLat.state.collectAsStateWithLifecycle()
    val lon by settings.homeLon.state.collectAsStateWithLifecycle()
    val locator by settings.homeLocator.state.collectAsStateWithLifecycle()
    val locationSet = HomeLocation.isSet(lat, lon)

    var step by rememberSaveable { mutableIntStateOf(0) }
    // The satellite picked on the last step; saved only on Done, so Back or Skip leave the selection as it was.
    var pickedCatnum by rememberSaveable { mutableStateOf<Int?>(null) }
    val chosenCatnum = pickedCatnum ?: state.selectedCatnum

    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    val steps = listOf(
        SetupStep(R.string.setup_title_location, canContinue = locationSet) {
            Text(stringResource(R.string.setup_location_why), style = MaterialTheme.typography.bodyMedium)
            LocationEditor(settings, locationFetcher, onMessage = { message = it })
            Text(
                if (locationSet) stringResource(R.string.setup_location_summary, locator, lat, lon)
                else stringResource(R.string.setup_location_needed),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        // Done waits for the satellite list (after the app is recreated on this step it may still be loading); Done with
        // nothing to choose from would finish the guide and calculate nothing. Skip stays available.
        SetupStep(R.string.setup_title_satellite, canContinue = !state.loading && state.satellites.isNotEmpty()) {
            Text(stringResource(R.string.setup_satellite_why), style = MaterialTheme.typography.bodyMedium)
            if (state.satellites.isEmpty()) {
                Text(stringResource(R.string.passes_loading), style = MaterialTheme.typography.bodyMedium)
            } else {
                DropdownField(
                    label = stringResource(R.string.passes_satellite_label),
                    options = state.satellites,
                    selected = state.satellites.firstOrNull { it.catnum == chosenCatnum } ?: state.satellites.first(),
                    optionLabel = { it.name },
                    onSelected = { pickedCatnum = it.catnum },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
    val index = step.coerceIn(0, steps.lastIndex)
    val current = steps[index]
    val isLast = index == steps.lastIndex

    fun back() {
        if (index > 0) step = index - 1 else onLeave()
    }

    fun next() {
        if (!current.canContinue) return
        if (isLast) {
            onDone(state.satellites.firstOrNull { it.catnum == chosenCatnum }?.catnum ?: state.satellites.firstOrNull()?.catnum)
        } else {
            step = index + 1
        }
    }

    BackHandler(onBack = ::back)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                HorizontalDivider()
                // Same places on every step (requirement 7.4). Back stays visible on the first step, disabled, so the
                // buttons do not move; System Back there leaves the guide.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = ::back, enabled = index > 0) { Text(stringResource(R.string.setup_back)) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onLeave) { Text(stringResource(R.string.setup_skip)) }
                    Button(onClick = ::next, enabled = current.canContinue, modifier = Modifier.padding(start = 8.dp)) {
                        Text(stringResource(if (isLast) R.string.setup_done else R.string.setup_next))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The step indicator is text, not colour or dots (requirement 7.4); the title is a heading for
                // screen readers (requirement 8.3).
                Text(
                    stringResource(R.string.setup_step_n_of_m, index + 1, steps.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(current.title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                current.content()
            }
        }
    }
}
