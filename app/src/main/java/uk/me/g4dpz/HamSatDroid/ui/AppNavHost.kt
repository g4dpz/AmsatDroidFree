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
import androidx.compose.runtime.saveable.rememberSaveable
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
    /** The screen a notification or widget tap asked for, or null for an ordinary launch (see [StartRoute]). */
    requestedRoute: String? = null,
    /** Routes requested while the host is showing (a notification or widget tap into the running app). */
    routeRequests: Flow<String> = emptyFlow(),
) {
    val navController = rememberNavController()
    // Decided once and kept across rotation and process recreation, so the graph's start does not change under a
    // restored back stack once the guide has been finished.
    val startRoute = rememberSaveable {
        StartRoute.choose(
            requestedRoute,
            locationSet = HomeLocation.isSet(settings.homeLat.value, settings.homeLon.value),
            finished = settings.setupFinished.value,
        )
    }
    val layout = WindowLayout.forWidth(LocalConfiguration.current.screenWidthDp)
    LaunchedEffect(navController, routeRequests) {
        routeRequests.collect { route ->
            // On a fresh install the setup guide can be the only screen, with no passes screen below it to return to.
            // Replace that whole stack with the passes screen first, so Back afterwards cannot reopen the abandoned
            // guide and Set up cannot stack a second one.
            val hasPasses = runCatching { navController.getBackStackEntry(Routes.PASSES) }.isSuccess
            if (!hasPasses) {
                navController.navigate(Routes.PASSES) { popUpTo(navController.graph.id) { inclusive = true } }
            }
            if (route == Routes.PASSES && !hasPasses) return@collect
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
            // Leaving the guide (requirement 7.5): opened from another screen it returns there; as the first screen of
            // a fresh install the passes screen replaces it, so nothing is left behind to come back to.
            SetupScreen(
                viewModel, settings, locationFetcher,
                onLeave = {
                    settings.setupFinished.value = true
                    navController.popOrGoHome(Routes.SETUP)
                },
                onDone = { catnum ->
                    catnum?.let(viewModel::selectSatellite)
                    settings.setupFinished.value = true
                    if (!navController.popBackStack(Routes.PASSES, inclusive = false)) {
                        navController.navigate(Routes.PASSES) { popUpTo(Routes.SETUP) { inclusive = true } }
                    }
                    // Show passes straight away (requirement 5.2); calculate() itself reports an unset location (5.3).
                    if (HomeLocation.isSet(settings.homeLat.value, settings.homeLon.value)) viewModel.calculate()
                },
            )
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
