// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import uk.me.g4dpz.HamSatDroid.night.AmbientLightProvider
import uk.me.g4dpz.HamSatDroid.night.ThemeMode
import uk.me.g4dpz.HamSatDroid.night.effectiveThemeMode
import uk.me.g4dpz.HamSatDroid.night.rememberAmbientNight
import uk.me.g4dpz.HamSatDroid.ui.theme.isDark
import uk.me.g4dpz.HamSatDroid.widget.WidgetRefresh
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import uk.me.g4dpz.HamSatDroid.HamSatDroidApp
import uk.me.g4dpz.HamSatDroid.data.FileStore
import uk.me.g4dpz.HamSatDroid.data.PassStore
import uk.me.g4dpz.HamSatDroid.data.createKepsRepository
import uk.me.g4dpz.HamSatDroid.location.LocationFetcher
import uk.me.g4dpz.HamSatDroid.ui.theme.HamSatDroidTheme

/**
 * The single activity of the Compose UI. It draws edge to edge (Compose's `Scaffold` handles the system bar insets),
 * follows the theme setting live, and hosts the navigation.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as HamSatDroidApp
                AppViewModel(
                    createKepsRepository(app), PassStore(FileStore(app.filesDir)), app.settings,
                    onDataChanged = { WidgetRefresh.updateAll(app) },
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val settings = (application as HamSatDroidApp).settings
        val locationFetcher = LocationFetcher(this)
        val lightProvider = AmbientLightProvider(this)
        // Null for an ordinary launch: the navigation host then decides between the setup guide and the passes screen.
        // Not defaulted to the passes screen, because the widget asks for that one explicitly (see StartRoute).
        val requestedRoute = intent.getStringExtra(EXTRA_ROUTE)
        setContent {
            val chosen by settings.themeMode.state.collectAsStateWithLifecycle()
            val auto by settings.autoRedNight.state.collectAsStateWithLifecycle()
            val ambientNight by rememberAmbientNight(lightProvider, auto)
            val mode = effectiveThemeMode(chosen, ambientNight)
            val dark = mode.isDark()
            val redNight = mode == ThemeMode.RED_NIGHT
            LaunchedEffect(dark, redNight) { applySystemBars(dark, redNight) }
            HamSatDroidTheme(mode) {
                AppNavHost(viewModel, settings, locationFetcher, lightProvider.available, requestedRoute, routeRequests)
            }
        }
    }

    /**
     * Routes asked for after the activity exists (a notification or widget tap while the app is running). With
     * `launchMode="singleTask"` such a tap arrives here rather than creating a second instance; the navigation host
     * collects these and navigates. Buffered so a request made before the host is composed is not lost.
     */
    private val routeRequests = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { routeRequests.tryEmit(it) }
    }

    /**
     * Matches the system bar icons to the theme. The system draws its icons in white or dark grey only, so in red
     * night the bars are hidden instead (a swipe from the edge shows them for a moment).
     */
    private fun applySystemBars(dark: Boolean, redNight: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (redNight) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onStart()
    }

    companion object {
        /** Optional extra naming the screen to open first (one of [Routes]). */
        const val EXTRA_ROUTE = "route"

        fun intent(context: Context, route: String): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_ROUTE, route)
    }
}
