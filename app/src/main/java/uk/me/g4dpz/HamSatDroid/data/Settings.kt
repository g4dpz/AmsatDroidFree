// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import uk.me.g4dpz.HamSatDroid.aim.PointingAxis
import uk.me.g4dpz.HamSatDroid.night.ThemeMode

/** The few operations settings need from a key-value store, so they can be tested without Android. */
interface KeyValueStore {
    fun getString(key: String, default: String): String
    fun putString(key: String, value: String)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
}

/**
 * [KeyValueStore] over the app's default shared preferences file (`<package>_preferences`), which is the file the
 * original app used through `PreferenceManager.getDefaultSharedPreferences`. Existing values such as the home
 * location are therefore found where they always were.
 */
class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE)
    )

    // A value stored with a different type (for example by an older version) falls back to the default.
    override fun getString(key: String, default: String): String = try {
        prefs.getString(key, default) ?: default
    } catch (e: ClassCastException) {
        default
    }

    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()

    override fun getBoolean(key: String, default: Boolean): Boolean = try {
        prefs.getBoolean(key, default)
    } catch (e: ClassCastException) {
        default
    }

    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    override fun getInt(key: String, default: Int): Int = try {
        prefs.getInt(key, default)
    } catch (e: ClassCastException) {
        default
    }

    override fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
}

/** A single setting: its current value as a [StateFlow], written through to the store when changed. */
class Setting<T>(initial: T, private val write: (T) -> Unit) {
    private val flow = MutableStateFlow(initial)
    val state: StateFlow<T> get() = flow
    var value: T
        get() = flow.value
        set(v) {
            write(v)
            flow.value = v
        }
}

/**
 * All user settings with their defaults.
 *
 * Values read from the store are checked; anything unknown or out of range (an old or hand-edited preference file)
 * falls back to the default instead of failing. Setters reject invalid values with [IllegalArgumentException].
 *
 * The home location keeps its original keys and string form: `homeLat`, `homeLon` (decimal degrees as text, possibly
 * with a comma from an old locale) and `homeLocator`. Use [LocationText] to read the numbers.
 */
class SettingsRepository(private val store: KeyValueStore) {

    // ---- home location (keys unchanged from earlier versions) ----
    val homeLat = Setting(store.getString(HOME_LAT, DEFAULT_LATLON)) { store.putString(HOME_LAT, it) }
    val homeLon = Setting(store.getString(HOME_LON, DEFAULT_LATLON)) { store.putString(HOME_LON, it) }
    val homeLocator = Setting(store.getString(HOME_LOCATOR, DEFAULT_LOCATOR)) { store.putString(HOME_LOCATOR, it) }

    // ---- keps ----
    val kepsSource = Setting(KepsSource.fromKey(store.getString(KEPS_SOURCE, KepsSource.DEFAULT.key))) {
        store.putString(KEPS_SOURCE, it.key)
    }
    val autoKepsRefresh = Setting(store.getBoolean(AUTO_KEPS_REFRESH, true)) { store.putBoolean(AUTO_KEPS_REFRESH, it) }

    // ---- pass calculation ----
    /** How far ahead to look for passes: 0 means "next pass only", otherwise 6, 12 or 24 hours. */
    val calcHoursAhead = Setting(store.getInt(CALC_HOURS_AHEAD, 0).takeIf { it in CALC_HOURS_CHOICES } ?: 0) {
        require(it in CALC_HOURS_CHOICES) { "Hours ahead must be one of $CALC_HOURS_CHOICES: $it" }
        store.putInt(CALC_HOURS_AHEAD, it)
    }

    // ---- selection (used by the app and the widget) ----
    /** Catalogue number of the satellite last selected, or 0 if none. */
    val selectedCatnum = Setting(store.getInt(SELECTED_CATNUM, 0).coerceAtLeast(0)) { store.putInt(SELECTED_CATNUM, it) }

    // ---- alerts ----
    val alertLeadMinutes = Setting(
        store.getInt(ALERT_LEAD_MINUTES, DEFAULT_ALERT_LEAD_MINUTES).takeIf { it in ALERT_LEAD_CHOICES }
            ?: DEFAULT_ALERT_LEAD_MINUTES
    ) {
        require(it in ALERT_LEAD_CHOICES) { "Alert lead time must be one of $ALERT_LEAD_CHOICES: $it" }
        store.putInt(ALERT_LEAD_MINUTES, it)
    }
    val spokenAlerts = Setting(store.getBoolean(SPOKEN_ALERTS, false)) { store.putBoolean(SPOKEN_ALERTS, it) }

    /** Whether the one-time "allow exact alarms?" offer has been shown, so it is not shown again. */
    val askedExactAlarm = Setting(store.getBoolean(ASKED_EXACT_ALARM, false)) { store.putBoolean(ASKED_EXACT_ALARM, it) }

    // ---- first-run setup ----
    /**
     * Whether the setup guide has been finished or skipped, so it does not open by itself again. False on a fresh
     * install and on an upgrade (the key is new); see `SetupPolicy` for when the guide opens.
     */
    val setupFinished = Setting(store.getBoolean(SETUP_FINISHED, false)) { store.putBoolean(SETUP_FINISHED, it) }

    // ---- sky view ----
    val compassEnabled = Setting(store.getBoolean(COMPASS_ENABLED, true)) { store.putBoolean(COMPASS_ENABLED, it) }
    val pointingAxis = Setting(enumOrDefault(store.getString(POINTING_AXIS, PointingAxis.TOP_EDGE.name), PointingAxis.TOP_EDGE)) {
        store.putString(POINTING_AXIS, it.name)
    }
    val keepScreenOn = Setting(store.getBoolean(KEEP_SCREEN_ON, true)) { store.putBoolean(KEEP_SCREEN_ON, it) }

    // ---- appearance ----
    val themeMode = Setting(enumOrDefault(store.getString(THEME_MODE, ThemeMode.SYSTEM.name), ThemeMode.SYSTEM)) {
        store.putString(THEME_MODE, it.name)
    }
    val autoRedNight = Setting(store.getBoolean(AUTO_RED_NIGHT, false)) { store.putBoolean(AUTO_RED_NIGHT, it) }

    // ---- overview ----
    val overviewHours = Setting(
        store.getInt(OVERVIEW_HOURS, DEFAULT_OVERVIEW_HOURS).takeIf { it in OVERVIEW_HOUR_CHOICES } ?: DEFAULT_OVERVIEW_HOURS
    ) {
        require(it in OVERVIEW_HOUR_CHOICES) { "Overview hours must be one of $OVERVIEW_HOUR_CHOICES: $it" }
        store.putInt(OVERVIEW_HOURS, it)
    }
    val overviewMinElevation = Setting(
        store.getInt(OVERVIEW_MIN_ELEVATION, 0).takeIf { it in OVERVIEW_MIN_ELEVATION_CHOICES } ?: 0
    ) {
        require(it in OVERVIEW_MIN_ELEVATION_CHOICES) {
            "Minimum elevation must be one of $OVERVIEW_MIN_ELEVATION_CHOICES: $it"
        }
        store.putInt(OVERVIEW_MIN_ELEVATION, it)
    }

    // ---- contact planner ----
    /** The other stations saved for the contact planner, at most [SavedStation.MAX_STATIONS]. */
    val plannerStations = Setting(SavedStationsJson.read(store.getString(PLANNER_STATIONS, ""))) {
        require(it.size <= SavedStation.MAX_STATIONS) { "At most ${SavedStation.MAX_STATIONS} stations can be saved: ${it.size}" }
        store.putString(PLANNER_STATIONS, SavedStationsJson.write(it))
    }

    /** The id of the station the planner uses, or empty when none is chosen. It may name a station that was deleted. */
    val plannerStationId = Setting(store.getString(PLANNER_STATION_ID, "")) { store.putString(PLANNER_STATION_ID, it) }

    /** The lowest elevation either station can use; one value for both stations (open decision P4). */
    val plannerMinElevation = Setting(
        store.getInt(PLANNER_MIN_ELEVATION, 0).takeIf { it in PLANNER_MIN_ELEVATION_CHOICES } ?: 0
    ) {
        require(it in PLANNER_MIN_ELEVATION_CHOICES) {
            "Planner minimum elevation must be one of $PLANNER_MIN_ELEVATION_CHOICES: $it"
        }
        store.putInt(PLANNER_MIN_ELEVATION, it)
    }

    val plannerHours = Setting(
        store.getInt(PLANNER_HOURS, DEFAULT_PLANNER_HOURS).takeIf { it in PLANNER_HOUR_CHOICES } ?: DEFAULT_PLANNER_HOURS
    ) {
        require(it in PLANNER_HOUR_CHOICES) { "Planner hours must be one of $PLANNER_HOUR_CHOICES: $it" }
        store.putInt(PLANNER_HOURS, it)
    }

    /** Whether the planner searches every satellite in the keps source, not just the selected one. */
    val plannerAllSatellites = Setting(store.getBoolean(PLANNER_ALL_SATELLITES, false)) {
        store.putBoolean(PLANNER_ALL_SATELLITES, it)
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    companion object {
        const val HOME_LAT = "homeLat"
        const val HOME_LON = "homeLon"
        const val HOME_LOCATOR = "homeLocator"
        const val DEFAULT_LATLON = "0"
        const val DEFAULT_LOCATOR = "JJ00aa"

        const val KEPS_SOURCE = "kepsSource"
        const val AUTO_KEPS_REFRESH = "autoKepsRefresh"
        const val SELECTED_CATNUM = "selectedCatnum"
        const val CALC_HOURS_AHEAD = "calcHoursAhead"
        const val ALERT_LEAD_MINUTES = "alertLeadMinutes"
        const val SPOKEN_ALERTS = "spokenAlerts"
        const val ASKED_EXACT_ALARM = "askedExactAlarm"
        const val SETUP_FINISHED = "setupFinished"
        const val COMPASS_ENABLED = "compassEnabled"
        const val POINTING_AXIS = "pointingAxis"
        const val KEEP_SCREEN_ON = "keepScreenOn"
        const val THEME_MODE = "themeMode"
        const val AUTO_RED_NIGHT = "autoRedNight"
        const val OVERVIEW_HOURS = "overviewHours"
        const val OVERVIEW_MIN_ELEVATION = "overviewMinElevation"
        const val PLANNER_STATIONS = "plannerStations"
        const val PLANNER_STATION_ID = "plannerStationId"
        const val PLANNER_MIN_ELEVATION = "plannerMinElevation"
        const val PLANNER_HOURS = "plannerHours"
        const val PLANNER_ALL_SATELLITES = "plannerAllSatellites"

        const val DEFAULT_ALERT_LEAD_MINUTES = 5
        val ALERT_LEAD_CHOICES = listOf(5, 10, 15, 30)
        val CALC_HOURS_CHOICES = listOf(0, 6, 12, 24)
        const val DEFAULT_OVERVIEW_HOURS = 12
        val OVERVIEW_HOUR_CHOICES = listOf(6, 12, 24)
        val OVERVIEW_MIN_ELEVATION_CHOICES = listOf(0, 10, 20, 30)
        val PLANNER_MIN_ELEVATION_CHOICES = listOf(0, 5, 10, 15, 20, 30)
        const val DEFAULT_PLANNER_HOURS = 24
        val PLANNER_HOUR_CHOICES = listOf(12, 24, 48)
    }
}

/**
 * Reads the home location text stored in settings. Earlier versions could store a comma as the decimal separator on
 * some devices, so a comma is accepted; values are always parsed independently of the device locale.
 */
object LocationText {
    /** The number, or null if the text is not a valid number. */
    fun toDegrees(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    /** Locale-independent text for storing [degrees]. */
    fun fromDegrees(degrees: Double): String = degrees.toString()
}
