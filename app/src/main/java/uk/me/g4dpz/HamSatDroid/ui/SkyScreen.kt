package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.domain.AzEl
import uk.me.g4dpz.HamSatDroid.domain.SkyGeometry
import uk.me.g4dpz.HamSatDroid.domain.SkyReading
import uk.me.g4dpz.HamSatDroid.ui.components.KeepScreenOn
import uk.me.g4dpz.HamSatDroid.ui.components.rememberTicker
import uk.me.g4dpz.HamSatDroid.utils.IaruLocator
import uk.me.g4dpz.satellite.SatelliteFactory
import java.util.Date

/** How often the live position is refreshed while the screen is visible. */
private const val POSITION_REFRESH_MILLIS = 5_000L

/** The sky chart is never drawn wider than this, however wide the space is. */
private val MAX_CHART_WIDTH = 460.dp

/**
 * The sky view of the selected pass, as a piece that can be placed on its own screen or in a pane: the home location,
 * the pass details, a chart with the pass track and the satellite's current position, and its current azimuth,
 * elevation, sub-satellite point and range. It does not scroll by itself; whoever holds it provides the scrolling.
 */
@Composable
fun SkyContent(viewModel: AppViewModel, state: AppUiState, keepScreenOn: Boolean, modifier: Modifier = Modifier) {
    val formats = rememberDisplayFormats()
    val pass = state.selectedPass
    // The pass tells which satellite to show; with no pass the selected satellite is shown on its own.
    val tle = remember(pass, state.selectedCatnum, state.satellites) {
        viewModel.tleFor(pass?.catnum ?: state.selectedCatnum ?: -1)
    }

    // The track of the pass: computed once per pass, off the main thread.
    val track by produceState<List<AzEl>?>(null, pass, tle) {
        value = null
        val p = pass
        val t = tle
        if (p != null && t != null) {
            value = withContext(Dispatchers.Default) {
                val station = viewModel.newGroundStation() ?: return@withContext null
                SkyGeometry.computeTrack(SatelliteFactory.createSatellite(t), station, p.start, p.end)
            }
        }
    }

    // The live position: refreshed only while the screen is visible.
    val satellite = remember(tle) { tle?.let { SatelliteFactory.createSatellite(it) } }
    val tick by rememberTicker(POSITION_REFRESH_MILLIS)
    val reading by produceState<SkyReading?>(null, satellite, tick) {
        val s = satellite
        value = if (s == null) null else withContext(Dispatchers.Default) {
            viewModel.newGroundStation()?.let { SkyReading.from(s.getPosition(it, Date())) }
        }
    }

    val home = remember(state.selectedCatnum) { viewModel.newGroundStation() }

    KeepScreenOn(keepScreenOn)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (home != null) {
            Text(
                stringResource(R.string.sky_home_position, formats.coordinate(home.latitude), formats.coordinate(home.longitude)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.sky_home_gridsquare, IaruLocator(home.latitude, home.longitude).toMaidenhead()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (pass != null) {
            Text(stringResource(R.string.sky_pass_information), style = MaterialTheme.typography.titleSmall)
            Text(pass.lines(formats).joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                stringResource(if (tle == null) R.string.sky_no_satellite else R.string.sky_no_pass),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        // Keep the chart a sensible size on wide panes, centred
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SkyChart(track = track, current = reading?.azEl, modifier = Modifier.widthIn(max = MAX_CHART_WIDTH))
        }

        reading?.let { r ->
            Text(stringResource(R.string.sky_satellite_position), style = MaterialTheme.typography.titleSmall)
            Column {
                Text(stringResource(R.string.sky_elevation, formats.integer(r.elevationDeg)))
                Text(stringResource(R.string.sky_azimuth, formats.integer(r.azimuthDeg)))
                Text(stringResource(R.string.sky_latitude, formats.twoDecimals(r.latitudeDeg)))
                Text(stringResource(R.string.sky_longitude, formats.twoDecimals(r.longitudeDeg)))
                Text(stringResource(R.string.sky_range, formats.integer(r.rangeKm)))
            }
        }
    }
}

/** The sky view as a screen of its own, with a back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyScreen(viewModel: AppViewModel, keepScreenOn: Boolean, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selectedPass?.satName ?: state.selectedSatellite?.name ?: stringResource(R.string.nav_sky)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        SkyContent(
            viewModel, state, keepScreenOn,
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        )
    }
}
