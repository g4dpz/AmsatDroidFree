// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uk.me.g4dpz.HamSatDroid.HamSatDroidApp
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.data.DopplerDirection
import uk.me.g4dpz.HamSatDroid.data.TransponderMode
import uk.me.g4dpz.HamSatDroid.domain.DopplerMath
import uk.me.g4dpz.HamSatDroid.domain.LookAngle
import uk.me.g4dpz.HamSatDroid.domain.MutualMoment
import uk.me.g4dpz.HamSatDroid.domain.MutualWindow
import uk.me.g4dpz.HamSatDroid.ui.components.DropdownField

/**
 * The detail of one window, shown under its card: where each station points at the best moment, the total path, what
 * each station tunes and transmits on for a transponder mode (each corrected for its own Doppler shift), and the timeline
 * through the window.
 *
 * It reads in this order: the path, the mode, then for each station its name, azimuth, elevation and range, and then its
 * frequencies, then the timeline.
 */
@Composable
internal fun WindowDetail(
    window: MutualWindow,
    timeline: List<MutualMoment>,
    homeLocator: String,
    otherName: String,
    formats: DisplayFormats,
) {
    // Observed, not read once: the database arrives after start-up and changes after a background sync (as on the sky screen)
    val database by (LocalContext.current.applicationContext as HamSatDroidApp).transponderDatabase.collectAsStateWithLifecycle()
    val modes = remember(database, window.catnum) { database.modesForNoradId(window.catnum) }
    var selectedType by rememberSaveable(modes) { mutableStateOf<String?>(null) }
    val mode = modes.firstOrNull { it.type == selectedType } ?: modes.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(
            stringResource(R.string.planner_total_path, formats.integer(window.best.totalPathKm)),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (modes.isEmpty()) {
            Text(stringResource(R.string.doppler_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            DropdownField(
                label = stringResource(R.string.doppler_mode),
                options = modes,
                selected = mode,
                optionLabel = { it.type },
                onSelected = { selectedType = it.type },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        StationBlock(stringResource(R.string.planner_your_station, homeLocator), window.best.home, mode, formats)
        StationBlock(stringResource(R.string.planner_detail_other, otherName), window.best.other, mode, formats)
        mode?.let { ModeNotes(it, formats) }
        Timeline(window, timeline, formats)
    }
}

/** One station at the best moment: where it points, and what it tunes and transmits on for [mode]. */
@Composable
private fun StationBlock(title: String, look: LookAngle, mode: TransponderMode?, formats: DisplayFormats) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.sky_azimuth, formats.integer(look.azimuthDeg)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.sky_elevation, formats.oneDecimal(look.elevationDeg)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.sky_range, formats.integer(look.rangeKm)), style = MaterialTheme.typography.bodyMedium)
        if (mode != null) {
            // This station's own Doppler shift: its range rate at the best moment
            val downlink = DopplerMath.downlinkHz(mode.downlinkHz, look.rangeRateKmS)
            Text(
                if (mode.downlinkMode.isBlank()) stringResource(R.string.doppler_downlink)
                else stringResource(R.string.doppler_downlink_mode, mode.downlinkMode),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(stringResource(R.string.doppler_corrected, formats.groupedInteger(downlink)), style = MaterialTheme.typography.bodyMedium)
            mode.uplinkHz?.let { up ->
                // The uplink correction is the same for normal and inverting transponders (see DopplerMath)
                val uplink = DopplerMath.uplinkHz(up, look.rangeRateKmS)
                Text(
                    if (mode.uplinkMode.isBlank()) stringResource(R.string.doppler_uplink)
                    else stringResource(R.string.doppler_uplink_mode, mode.uplinkMode),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(stringResource(R.string.doppler_corrected_tx, formats.groupedInteger(uplink)), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** What applies to the mode whichever station is using it. */
@Composable
private fun ModeNotes(mode: TransponderMode, formats: DisplayFormats) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (mode.uplinkHz == null) {
            Text(stringResource(R.string.doppler_beacon_only), style = MaterialTheme.typography.bodySmall)
        }
        if (mode.doppler == DopplerDirection.REV) {
            Text(stringResource(R.string.doppler_inverting), style = MaterialTheme.typography.bodySmall)
        }
        mode.ctcssHz?.let { tone ->
            Text(stringResource(R.string.doppler_ctcss, formats.oneDecimal(tone)), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** One row a minute, plus the start, the best moment and the end, with both stations' azimuth and elevation. */
@Composable
private fun Timeline(window: MutualWindow, rows: List<MutualMoment>, formats: DisplayFormats) {
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.planner_timeline_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        for (row in rows) {
            val marker = when (row.time) {
                window.best.time -> stringResource(R.string.planner_marker_best)
                window.start -> stringResource(R.string.planner_marker_start)
                window.end -> stringResource(R.string.planner_marker_end)
                else -> ""
            }
            Text(
                stringResource(
                    R.string.planner_timeline_row,
                    formats.timeSeconds(row.time), marker,
                    formats.integer(row.home.azimuthDeg), formats.oneDecimal(row.home.elevationDeg),
                    formats.integer(row.other.azimuthDeg), formats.oneDecimal(row.other.elevationDeg),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
