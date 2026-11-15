// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.domain.LookAngle
import uk.me.g4dpz.HamSatDroid.domain.MutualMoment
import uk.me.g4dpz.HamSatDroid.domain.MutualWindow

/**
 * The detail of one window, shown under its card: where each station points at the best moment, the total path, and
 * the timeline through the window. The planner is for working out when and where to point; Doppler-corrected
 * frequencies are left to the tracking software or to this app's sky screen at the time of the contact.
 *
 * It reads in this order: the path, then for each station its name, azimuth, elevation and range, then the timeline.
 */
@Composable
internal fun WindowDetail(
    window: MutualWindow,
    timeline: List<MutualMoment>,
    homeLocator: String,
    otherName: String,
    formats: DisplayFormats,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(
            stringResource(R.string.planner_total_path, formats.integer(window.best.totalPathKm)),
            style = MaterialTheme.typography.bodyMedium,
        )
        StationBlock(stringResource(R.string.planner_your_station, homeLocator), window.best.home, formats)
        StationBlock(stringResource(R.string.planner_detail_other, otherName), window.best.other, formats)
        Timeline(window, timeline, formats)
    }
}

/** One station at the best moment: where it points, and how far away the satellite is. */
@Composable
private fun StationBlock(title: String, look: LookAngle, formats: DisplayFormats) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.sky_azimuth, formats.integer(look.azimuthDeg)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.sky_elevation, formats.oneDecimal(look.elevationDeg)), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.sky_range, formats.integer(look.rangeKm)), style = MaterialTheme.typography.bodyMedium)
    }
}

/** One row a minute (fewer for a very long window), plus the start, the best moment and the end. */
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
