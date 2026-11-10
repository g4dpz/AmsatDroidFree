// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.me.g4dpz.HamSatDroid.HamSatDroidApp
import uk.me.g4dpz.HamSatDroid.R
import uk.me.g4dpz.HamSatDroid.aim.AimState
import uk.me.g4dpz.HamSatDroid.aim.AimStatus
import uk.me.g4dpz.HamSatDroid.aim.AimTarget
import uk.me.g4dpz.HamSatDroid.aim.Orientation
import uk.me.g4dpz.HamSatDroid.aim.PointingAxis
import uk.me.g4dpz.HamSatDroid.aim.PointingMath
import uk.me.g4dpz.HamSatDroid.data.DopplerDirection
import uk.me.g4dpz.HamSatDroid.data.TransponderMode
import uk.me.g4dpz.HamSatDroid.domain.AzEl
import uk.me.g4dpz.HamSatDroid.domain.DopplerMath
import uk.me.g4dpz.HamSatDroid.ui.components.DropdownField
import uk.me.g4dpz.HamSatDroid.domain.PassSummary
import uk.me.g4dpz.HamSatDroid.domain.SkyGeometry
import uk.me.g4dpz.HamSatDroid.domain.SkyReading
import uk.me.g4dpz.HamSatDroid.ui.components.KeepScreenOn
import uk.me.g4dpz.HamSatDroid.ui.components.rememberTicker
import uk.me.g4dpz.HamSatDroid.utils.IaruLocator
import uk.me.g4dpz.satellite.SatelliteFactory
import java.util.Date
import kotlin.math.roundToInt

/**
 * How often the live position is refreshed while the screen is visible. One second so the Doppler-corrected
 * frequencies and the current-position readout stay current; it is cheap (one prediction step).
 */
private const val POSITION_REFRESH_MILLIS = 1_000L

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
    val settings = (LocalContext.current.applicationContext as HamSatDroidApp).settings
    val compassProvider = rememberCompassProvider()
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

    // Compass and aim both need the sensors; aim implies the compass is read even if chart rotation is off.
    val compassEnabled by settings.compassEnabled.state.collectAsStateWithLifecycle()
    val pointingAxis by settings.pointingAxis.state.collectAsStateWithLifecycle()
    var aimOn by rememberSaveable { mutableStateOf(false) }
    val sensorsNeeded = compassProvider.isAvailable && (compassEnabled || aimOn)
    // The State itself is passed down, not its value: reading `.value` here would make this whole function run again
    // for every compass reading. Each reader below reads it in the smallest place that needs it (the chart's draw
    // step, the calibration hint, the aim panel).
    val orientation = rememberCompassOrientation(
        compassProvider, enabled = sensorsNeeded, latitude = home?.latitude, longitude = home?.longitude,
    )
    // Rotate the chart only when the compass is on; aim alone leaves the chart north-up. Read at draw time.
    val chartRotation: () -> Float = { if (compassEnabled) orientation.value?.headingDeg?.toFloat() ?: 0f else 0f }
    // Changes only when the accuracy crosses the "needs calibration" line, not with every reading.
    val needsCalibration by remember(orientation) { derivedStateOf { orientation.value?.needsCalibration == true } }
    val declination = remember(home) {
        if (home == null) 0.0 else android.hardware.GeomagneticField(
            home.latitude.toFloat(), home.longitude.toFloat(), 0f, System.currentTimeMillis(),
        ).declination.toDouble()
    }

    KeepScreenOn(keepScreenOn)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (compassProvider.isAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = compassEnabled,
                    onClick = { settings.compassEnabled.value = !compassEnabled },
                    label = { Text(stringResource(R.string.compass_toggle)) },
                    leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                )
                FilterChip(
                    selected = aimOn,
                    onClick = { aimOn = !aimOn },
                    label = { Text(stringResource(R.string.aim_toggle)) },
                    leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
                )
            }
            if ((compassEnabled || aimOn) && needsCalibration) {
                Text(
                    stringResource(R.string.compass_calibrate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

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
            SkyChart(
                track = track, current = reading?.azEl,
                modifier = Modifier.widthIn(max = MAX_CHART_WIDTH),
                rotationDegrees = chartRotation,
            )
        }

        if (aimOn) {
            AimPanel(
                orientationState = orientation,
                pointingAxis = pointingAxis,
                declination = declination,
                target = reading?.azEl,
                pass = pass,
                satelliteName = pass?.satName ?: state.selectedSatellite?.name,
                formats = formats,
                compassAvailable = compassProvider.isAvailable,
            )
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

        val catnum = tle?.catnum
        if (catnum != null) {
            // Observed, not read once: the database arrives after start-up and changes after a background sync.
            val database by (LocalContext.current.applicationContext as HamSatDroidApp).transponderDatabase
                .collectAsStateWithLifecycle()
            DopplerCard(
                modes = remember(database, catnum) { database.modesForNoradId(catnum) },
                reading = reading,
            )
        }
    }
}

/**
 * The Doppler card: a picker of the satellite's transponder modes from the OscarWatch database, and the nominal and
 * Doppler-corrected downlink and uplink with the shift, refreshed each second from the live range rate. A beacon or
 * receive-only mode shows only the downlink. Below the horizon the values are shown with a note. The database is
 * read-only; when the satellite has no modes the card says so. Frequencies are not entered by the user.
 */
@Composable
private fun DopplerCard(modes: List<TransponderMode>, reading: SkyReading?) {
    val formats = rememberDisplayFormats()
    var selectedType by rememberSaveable(modes) { mutableStateOf<String?>(null) }
    val selected = modes.firstOrNull { it.type == selectedType } ?: modes.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.doppler_title), style = MaterialTheme.typography.titleSmall)

        if (modes.isEmpty()) {
            Text(stringResource(R.string.doppler_none), style = MaterialTheme.typography.bodyMedium)
        } else {
            DropdownField(
                label = stringResource(R.string.doppler_mode),
                options = modes,
                selected = selected,
                optionLabel = { it.type },
                onSelected = { selectedType = it.type },
                modifier = Modifier.fillMaxWidth(),
            )
            selected?.let { mode ->
                if (reading != null && reading.elevationDeg < 0) {
                    Text(stringResource(R.string.doppler_not_visible), style = MaterialTheme.typography.bodySmall)
                }
                FrequencyLines(mode, reading?.rangeRateKmS, formats)
            }
        }
    }
}

@Composable
private fun FrequencyLines(mode: TransponderMode, rangeRateKmS: Double?, formats: DisplayFormats) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // Downlink (always present).
        Text(
            if (mode.downlinkMode.isBlank()) stringResource(R.string.doppler_downlink)
            else stringResource(R.string.doppler_downlink_mode, mode.downlinkMode),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(stringResource(R.string.doppler_nominal, formats.groupedInteger(mode.downlinkHz)), style = MaterialTheme.typography.bodyMedium)
        if (rangeRateKmS != null) {
            val tuned = DopplerMath.downlinkHz(mode.downlinkHz, rangeRateKmS)
            Text(stringResource(R.string.doppler_corrected, formats.groupedInteger(tuned)), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.doppler_shift, formats.groupedInteger(tuned - mode.downlinkHz)),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        // Uplink (absent for a beacon or receive-only mode).
        mode.uplinkHz?.let { up ->
            Text(
                if (mode.uplinkMode.isBlank()) stringResource(R.string.doppler_uplink)
                else stringResource(R.string.doppler_uplink_mode, mode.uplinkMode),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(stringResource(R.string.doppler_nominal, formats.groupedInteger(up)), style = MaterialTheme.typography.bodyMedium)
            if (rangeRateKmS != null) {
                // The uplink correction is the same for normal and inverting transponders (see DopplerMath).
                val tx = DopplerMath.uplinkHz(up, rangeRateKmS)
                Text(stringResource(R.string.doppler_corrected_tx, formats.groupedInteger(tx)), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.doppler_shift, formats.groupedInteger(tx - up)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (mode.doppler == DopplerDirection.REV) {
                Text(stringResource(R.string.doppler_inverting), style = MaterialTheme.typography.bodySmall)
            }
            mode.ctcssHz?.let { tone ->
                Text(stringResource(R.string.doppler_ctcss, formats.oneDecimal(tone)), style = MaterialTheme.typography.bodySmall)
            }
        } ?: run {
            Text(stringResource(R.string.doppler_beacon_only), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Aim mode: the device's pointing direction, the satellite's direction, and how to move. Instructions are plain text
 * with arrows; the on-target state uses an icon and text as well as colour. Below the horizon it says when the
 * satellite rises instead. It also shows the compass accuracy and the magnet/metal warning.
 */
@Composable
private fun AimPanel(
    orientationState: State<Orientation?>,
    pointingAxis: PointingAxis,
    declination: Double,
    target: AzEl?,
    pass: PassSummary?,
    satelliteName: String?,
    formats: DisplayFormats,
    compassAvailable: Boolean,
) {
    // Read here, inside the panel, so a compass reading redraws only this panel and not the whole sky screen.
    val orientation = orientationState.value
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!compassAvailable) {
            Text(stringResource(R.string.aim_no_compass), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        val pointing = orientation?.let { PointingMath.pointing(it.rotationMatrix, pointingAxis, declination) }
        val targetAzEl = target?.let {
            AimTarget(Math.toDegrees(it.azimuth), Math.toDegrees(it.elevation))
        }

        if (pointing != null) {
            Text(
                stringResource(
                    R.string.aim_pointing,
                    formats.integer(pointing.azimuthDeg), formats.integer(pointing.elevationDeg),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (targetAzEl != null) {
            Text(
                stringResource(R.string.aim_target, formats.integer(targetAzEl.azimuthDeg), formats.integer(targetAzEl.elevationDeg)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (pointing != null && targetAzEl != null) {
            when (val status = AimState.compute(pointing, targetAzEl)) {
                AimStatus.OnTarget -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.aim_on_target),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is AimStatus.Aiming -> {
                    val az = status.deltaAzDeg.roundToInt()
                    val el = status.deltaElDeg.roundToInt()
                    val azText = if (az >= 0) stringResource(R.string.aim_turn_right, az) else stringResource(R.string.aim_turn_left, -az)
                    val elText = if (el >= 0) stringResource(R.string.aim_raise, el) else stringResource(R.string.aim_lower, -el)
                    Text(stringResource(R.string.aim_move, azText, elText), style = MaterialTheme.typography.titleMedium)
                }
                AimStatus.BelowHorizon -> Text(
                    belowHorizonText(pass, satelliteName, formats),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        orientation?.let { Text(stringResource(R.string.aim_accuracy, accuracyText(it.accuracy)), style = MaterialTheme.typography.bodySmall) }
        Text(stringResource(R.string.aim_magnet_warning), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun belowHorizonText(pass: PassSummary?, satelliteName: String?, formats: DisplayFormats): String =
    if (pass != null) {
        stringResource(R.string.aim_below_horizon, pass.satName, formats.dateTime(pass.start), pass.aosAzimuthDeg)
    } else {
        stringResource(R.string.aim_below_horizon_unknown, satelliteName ?: "")
    }

@Composable
private fun accuracyText(accuracy: Int): String = stringResource(
    when (accuracy) {
        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> R.string.accuracy_high
        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> R.string.accuracy_medium
        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW -> R.string.accuracy_low
        else -> R.string.accuracy_unreliable
    }
)

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
