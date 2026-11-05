// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.domain.GeoPoint
import uk.me.g4dpz.HamSatDroid.domain.MapProjection
import uk.me.g4dpz.HamSatDroid.ui.components.KeepScreenOn
import uk.me.g4dpz.HamSatDroid.ui.components.rememberTicker
import uk.me.g4dpz.HamSatDroid.utils.IaruLocator
import uk.me.g4dpz.satellite.SatelliteFactory
import java.time.Instant
import java.util.Date
import kotlin.math.abs

/** How often the satellite marker and its footprint move. */
private const val POSITION_REFRESH_MILLIS = 1_000L

/** The ground track is recalculated this often; it changes slowly, so there is no need to do it every second. */
private const val TRACK_REFRESH_MILLIS = 15_000L

/** What the map needs about the satellite right now. */
private data class MapReading(
    val subPoint: GeoPoint,
    val footprint: List<GeoPoint>,
    val rangeKm: Double,
)

/**
 * The map view as a piece that can be placed on its own screen or in a pane: the satellite's ground track for about
 * three orbits, its footprint and position, and the home location, with the latitude, longitude and range as text. The
 * position updates every second while it is visible. It does not scroll by itself.
 */
@Composable
fun MapContent(viewModel: AppViewModel, state: AppUiState, keepScreenOn: Boolean, modifier: Modifier = Modifier) {
    val formats = rememberDisplayFormats()
    val tle = remember(state.selectedCatnum, state.satellites) { state.selectedCatnum?.let(viewModel::tleFor) }
    val satellite = remember(tle) { tle?.let { SatelliteFactory.createSatellite(it) } }
    val home = remember(state.selectedCatnum) { viewModel.newGroundStation() }

    val tick by rememberTicker(POSITION_REFRESH_MILLIS)

    val reading by produceState<MapReading?>(null, satellite, tick) {
        val s = satellite
        value = if (s == null) null else withContext(Dispatchers.Default) {
            val station = viewModel.newGroundStation() ?: return@withContext null
            val pos = s.getPosition(station, Date())
            MapReading(
                subPoint = GeoPoint(Math.toDegrees(pos.latitude), Math.toDegrees(pos.longitude)),
                footprint = MapProjection.footprint(pos.rangeCircle),
                rangeKm = pos.range,
            )
        }
    }

    // About three orbits from now, recalculated every few seconds rather than every second.
    val trackSlot = tick / TRACK_REFRESH_MILLIS
    val track by produceState<List<GeoPoint>?>(null, satellite, trackSlot) {
        val s = satellite
        val t = tle
        if (s != null && t != null) {
            value = withContext(Dispatchers.Default) {
                val station = viewModel.newGroundStation() ?: return@withContext null
                val orbitMinutes = (24 * 60 / t.meanmo).toInt()
                MapProjection.groundTrack(s, station, Instant.now(), orbitMinutes * 3)
            }
        }
    }

    KeepScreenOn(keepScreenOn)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (tle == null) {
            Text(stringResource(R.string.map_no_satellite), style = MaterialTheme.typography.bodyMedium)
        }

        WorldMap(
            track = track,
            footprint = reading?.footprint,
            satellite = reading?.subPoint,
            home = home?.let { GeoPoint(it.latitude, it.longitude) },
            modifier = Modifier.fillMaxWidth(),
        )

        home?.let { h ->
            Column {
                Text(geoLine(R.string.map_home_latitude, h.latitude, true, formats))
                Text(geoLine(R.string.map_home_longitude, h.longitude, false, formats))
                Text(stringResource(R.string.sky_home_gridsquare, IaruLocator(h.latitude, h.longitude).toMaidenhead()))
            }
        }
        reading?.let { r ->
            // The library reports longitude 0..360; show it as -180..180 with E or W.
            val lon = if (r.subPoint.longitudeDeg > 180.0) r.subPoint.longitudeDeg - 360.0 else r.subPoint.longitudeDeg
            Column {
                Text(geoLine(R.string.map_satellite_latitude, r.subPoint.latitudeDeg, true, formats))
                Text(geoLine(R.string.map_satellite_longitude, lon, false, formats))
                Text(stringResource(R.string.sky_range, formats.integer(r.rangeKm)))
            }
        }
    }
}

/** The map as a screen of its own, with a back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: AppViewModel, keepScreenOn: Boolean, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selectedSatellite?.name ?: stringResource(R.string.nav_map)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        MapContent(
            viewModel, state, keepScreenOn,
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        )
    }
}

/** For example "Home latitude: 51.5° N". */
@Composable
private fun geoLine(labelRes: Int, value: Double, isLatitude: Boolean, formats: DisplayFormats): String {
    val direction = stringResource(
        when {
            isLatitude && value >= 0 -> R.string.compass_north
            isLatitude -> R.string.compass_south
            value >= 0 -> R.string.compass_east
            else -> R.string.compass_west
        }
    )
    return stringResource(R.string.map_geo_value, stringResource(labelRes), formats.oneDecimal(abs(value)), direction)
}
