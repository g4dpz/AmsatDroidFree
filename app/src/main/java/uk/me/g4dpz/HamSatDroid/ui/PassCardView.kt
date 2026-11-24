// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.domain.Countdown
import uk.me.g4dpz.HamSatDroid.domain.PassCardState
import uk.me.g4dpz.HamSatDroid.domain.PassCardStates
import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import java.time.Duration
import java.time.Instant

/** The colours of one card state: the card, its text, and the pill (none for grey cards). */
private data class CardColours(val container: Color, val content: Color, val pill: Color?, val onPill: Color?)

/**
 * Grey until 5 minutes before AOS, orange with a countdown to AOS, then green with "Passing" (Peter's OscarWatch cards,
 * David 24 Nov 2026). Light and dark themes use green and orange; red night uses shades of red only, so the state is
 * carried by the pill's words there, not by the colour.
 */
@Composable
private fun cardColours(state: PassCardState): CardColours {
    val scheme = MaterialTheme.colorScheme
    val grey = CardColours(scheme.surfaceVariant, scheme.onSurfaceVariant, null, null)
    if (LocalRedNight.current) {
        return when (state) {
            PassCardState.UPCOMING, PassCardState.OVER -> grey
            PassCardState.SOON -> CardColours(scheme.secondaryContainer, scheme.onSecondaryContainer, scheme.secondary, scheme.onSecondary)
            PassCardState.PASSING -> CardColours(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary, scheme.onPrimary)
        }
    }
    val dark = scheme.background.luminance() < 0.5f
    return when (state) {
        PassCardState.UPCOMING, PassCardState.OVER -> grey
        PassCardState.SOON -> if (dark) {
            CardColours(Color(0xFF3D2E12), Color(0xFFFFE0B2), Color(0xFFFFB74D), Color(0xFF1A1A1A))
        } else {
            CardColours(Color(0xFFFFF4E0), Color(0xFF3A2A0A), Color(0xFFF59E0B), Color.White)
        }
        PassCardState.PASSING -> if (dark) {
            CardColours(Color(0xFF1E3A22), Color(0xFFC8E6C9), Color(0xFF66BB6A), Color(0xFF0A1F0C))
        } else {
            CardColours(Color(0xFFE3F5E1), Color(0xFF14361A), Color(0xFF1B6E1F), Color.White)
        }
    }
}

/**
 * One pass in the list: "21:59 to 22:41", duration and maximum elevation, the AOS and LOS azimuths, a pill for the
 * card's state, and the bell when an alert is set. No satellite name (one satellite is shown at a time), no element
 * age and no alert button (alerts stay on long-press). A selected card on a wide screen gets a border.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun PassCardView(
    pass: PassSummary,
    formats: DisplayFormats,
    now: Instant,
    selected: Boolean,
    hasAlert: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    belowMinimum: Int? = null,
) {
    val state = PassCardStates.of(pass, now)
    val colours = cardColours(state)
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = colours.container, contentColor = colours.content),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.pass_card_times, formats.time(pass.start), formats.time(pass.end)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (hasAlert) {
                    Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.alert_bell))
                }
                Pill(state, pass, now, colours)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.pass_duration, formats.oneDecimal(pass.duration.seconds / 60.0)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.pass_max_elevation, formats.oneDecimal(pass.maxElevationDeg)),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.pass_aos_azimuth, pass.aosAzimuthDeg), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.pass_los_azimuth, pass.losAzimuthDeg), style = MaterialTheme.typography.bodyMedium)
            }
            if (belowMinimum != null) {
                Text(stringResource(R.string.pass_below_minimum, belowMinimum), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Pill(state: PassCardState, pass: PassSummary, now: Instant, colours: CardColours) {
    val pill = colours.pill ?: return
    val onPill = colours.onPill ?: return
    val (text, spoken) = when (state) {
        PassCardState.SOON -> {
            val left = Countdown.format(Duration.between(now, pass.start))
            left to stringResource(R.string.pass_card_aos_in, left)
        }
        PassCardState.PASSING -> stringResource(R.string.pass_card_passing).let { it to it }
        else -> return
    }
    Text(
        text,
        color = onPill,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(pill, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .semantics { contentDescription = spoken },
    )
}
