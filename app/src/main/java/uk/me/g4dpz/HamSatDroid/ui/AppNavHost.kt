// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import android.annotation.SuppressLint
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import uk.me.g4dpz.HamSatDroid.HamSatDroidApp
import uk.me.g4dpz.HamSatDroid.alert.PassAlertScheduler
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as screenViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import uk.me.g4dpz.HamSatDroid.data.SettingsRepository
import uk.me.g4dpz.HamSatDroid.domain.HomeLocation
import uk.me.g4dpz.HamSatDroid.location.LocationFetcher

/** Leaves [route]: pops it when something is behind it, otherwise replaces it with the passes screen. */
private fun NavController.popOrGoHome(route: String) {
    if (!popBackStack()) navigate(Routes.PASSES) { popUpTo(route) { inclusive = true } }
}

/** Navigation destinations. */
object Routes {
    const val PASSES = "passes"
    const val SKY = "sky"
    const val MAP = "map"
    const val SETTINGS = "settings"
    const val OVERVIEW = "overview"
    const val SETUP = "setup"
}

/**
 * The app's navigation: the passes list, the sky view of a pass, the map, and the settings. Back (including the
 * predictive back gesture) returns to the previous screen.
 */
// Configuration.screenWidthDp is deliberate: on the Galaxy Tab A7 Lite (Android 12) LocalWindowInfo.containerSize
// kept the landscape width after rotating back to portrait, so the dashboard showed in portrait. screenWidthDp is the
// app window's width (it follows split-screen too) and was correct on that device.
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun AppNavHost(
    viewModel: AppViewModel,
    settings: SettingsRepository,
    locationFetcher: LocationFetcher,
    hasLightSensor: Boolean,
    startRoute: String = Routes.PASSES,
    /** Routes requested while the host is showing (a notification or widget tap into the running app). */
    routeRequests: Flow<String> = emptyFlow(),
) {
    val navController = rememberNavController()
    val layout = WindowLayout.forWidth(LocalConfiguration.current.screenWidthDp)
    LaunchedEffect(navController, routeRequests) {
        routeRequests.collect { route ->
            // Return to the passes screen first so the requested screen sits directly above it, as it would when
            // opened from the app itself; launchSingleTop stops a repeat request stacking the same screen twice.
            navController.navigate(route) {
                launchSingleTop = true
                popUpTo(Routes.PASSES) { inclusive = false }
            }
        }
    }
    NavHost(navController = navController, startDestination = startRoute) {
        composable(Routes.PASSES) {
            val keepScreenOn by settings.keepScreenOn.state.collectAsStateWithLifecycle()
            val homeLat by settings.homeLat.state.collectAsStateWithLifecycle()
            val homeLon by settings.homeLon.state.collectAsStateWithLifecycle()
            HomeScreen(
                viewModel = viewModel,
                layout = layout,
                keepScreenOn = keepScreenOn,
                onOpenPass = { navController.navigate(Routes.SKY) },
                onOpenMap = { navController.navigate(Routes.MAP) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenOverview = { navController.navigate(Routes.OVERVIEW) },
                locationSet = HomeLocation.isSet(homeLat, homeLon),
                onOpenSetup = { navController.navigate(Routes.SETUP) },
            )
        }
        composable(Routes.SKY) {
            val keepScreenOn by settings.keepScreenOn.state.collectAsStateWithLifecycle()
            // After rotating to a wide window the sky view is already shown beside the list. When this screen was the
            // start route (opened from a notification) there is nothing to pop, so go to the passes screen instead.
            if (layout != WindowLayout.SINGLE_PANE) LaunchedEffect(Unit) { navController.popOrGoHome(Routes.SKY) }
            SkyScreen(viewModel, keepScreenOn, onBack = { navController.popBackStack() })
        }
        composable(Routes.MAP) {
            val keepScreenOn by settings.keepScreenOn.state.collectAsStateWithLifecycle()
            if (layout == WindowLayout.DASHBOARD) LaunchedEffect(Unit) { navController.popOrGoHome(Routes.MAP) }
            MapScreen(viewModel, keepScreenOn, onBack = { navController.popBackStack() })
        }
        composable(Routes.OVERVIEW) {
            // Belongs to this screen: leaving it cancels a calculation in progress
            val overview = screenViewModel { OverviewViewModel(viewModel.tles, settings) }
            OverviewScreen(
                overview,
                onOpenPass = { pass ->
                    viewModel.showPass(pass)
                    // On a wide screen the passes screen shows the sky view beside the list
                    if (layout == WindowLayout.SINGLE_PANE) navController.navigate(Routes.SKY)
                    else if (!navController.popBackStack(Routes.PASSES, inclusive = false)) {
                        // Opened straight onto the overview, so the passes screen is not behind it
                        navController.navigate(Routes.PASSES) { popUpTo(Routes.OVERVIEW) { inclusive = true } }
                    }
                },
                onBack = { navController.popBackStack() },
                onOpenSetup = { navController.navigate(Routes.SETUP) },
            )
        }
        composable(Routes.SETUP) {
            // Opened from another screen for now, so Back returns there (task 5 adds opening it by itself at start-up,
            // when nothing is behind it and the passes screen replaces it).
            SetupScreen(settings, locationFetcher, onBack = { navController.popOrGoHome(Routes.SETUP) })
        }
        composable(Routes.SETTINGS) {
            val context = LocalContext.current
            SettingsScreen(
                settings, locationFetcher, hasLightSensor,
                onLeadTimeChanged = {
                    val app = context.applicationContext as HamSatDroidApp
                    PassAlertScheduler(context, app.passAlertStore, app.settings).rescheduleAll()
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
