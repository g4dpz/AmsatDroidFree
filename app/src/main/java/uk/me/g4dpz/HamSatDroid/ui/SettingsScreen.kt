// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.data.KepsSource
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.location.LocationFetcher
import uk.me.g4dpz.HamSatDroid.aim.PointingAxis
import uk.me.g4dpz.HamSatDroid.night.ThemeMode
import uk.me.g4dpz.HamSatDroid.night.labelRes
import uk.me.g4dpz.HamSatDroid.ui.components.DropdownField
import uk.me.g4dpz.HamSatDroid.ui.components.SwitchRow
import uk.me.g4dpz.HamSatDroid.ui.components.LocationEditor

/**
 * The settings screen: home location (locator, latitude, longitude, and getting them from the device), keps update
 * options, display options and a note about background restrictions.
 *
 * Changes are saved as soon as they are made. The location part is the shared [LocationEditor], so it behaves the same
 * here and in the first-run setup guide.
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
    val snackbar = remember { SnackbarHostState() }

    val autoRefresh by settings.autoKepsRefresh.state.collectAsStateWithLifecycle()
    val kepsSource by settings.kepsSource.state.collectAsStateWithLifecycle()
    val keepScreenOn by settings.keepScreenOn.state.collectAsStateWithLifecycle()
    val alertLead by settings.alertLeadMinutes.state.collectAsStateWithLifecycle()
    val spokenAlerts by settings.spokenAlerts.state.collectAsStateWithLifecycle()
    val pointingAxis by settings.pointingAxis.state.collectAsStateWithLifecycle()
    val themeMode by settings.themeMode.state.collectAsStateWithLifecycle()
    val autoRedNight by settings.autoRedNight.state.collectAsStateWithLifecycle()

    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
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

            LocationEditor(settings, locationFetcher, onMessage = { message = it })

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
            // Per-app language is an Android 13+ feature; hidden on older versions.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                }) { Text(stringResource(R.string.settings_language)) }
            }

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

}

@Composable
private fun SectionTitle(textRes: Int) {
    Text(stringResource(textRes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}
