# Design: Contact Planner

## Overview

A new screen that finds the times two ground stations can both use a satellite. The calculation is pure Kotlin in `domain`, built on the existing `PassPredictor` and `Satellite.getPosition`, so it can be tested on the JVM like the pass predictor. The screen follows the overview's pattern: a view model that runs one coroutine per satellite and streams results.

New code: one domain calculator with its data types, a saved-station type with its JSON, a few settings, a view model, the planner screen, a station editor screen, and strings. Nothing in `uk.me.g4dpz.satellite` changes.

## Findings that shape the design (version 2.0.8)

| Where | What it does today |
|---|---|
| `PassPredictor.getPasses(start, hoursAhead, windBack)` | passes above 0° for one ground station; `windBack = true` includes a pass already in progress |
| `Satellite.getPosition(station, date)` | writes into the `GroundStationPosition` it is given, so each thread needs its own (see `OverviewCalculator`) |
| `OverviewCalculator` | runs every satellite in its own coroutine on a limited dispatcher and emits results as each finishes; skips `SatelliteMotion.isStationary` satellites |
| `SatelliteMotion.fixedPosition` | look angles of a stationary satellite |
| `DopplerMath.downlinkHz` / `uplinkHz` | Doppler from a range rate in km/s |
| `TransponderDatabase.modesForNoradId` | the satellite's modes from the OscarWatch database |
| `LocatorSync` | the locator and latitude/longitude rules of Settings, pure and tested |
| `LocationEditor` | bound to the home location in `SettingsRepository`, with GPS and network buttons; not reusable for another station as it stands |
| `Settings.OVERVIEW_MIN_ELEVATION_CHOICES` | 0, 10, 20, 30; the planner needs finer steps at the low end (requirement 3.3) |
| Stored data | preferences only; `allowBackup="false"` |

## Domain

```kotlin
// domain/ContactPlanner.kt

/** A ground station for the planner: position in degrees (east positive) and its lowest usable elevation. */
data class PlannerStation(val latitudeDeg: Double, val longitudeDeg: Double, val minElevationDeg: Double)

/** Where one station sees the satellite at one moment. */
data class LookAngle(val azimuthDeg: Double, val elevationDeg: Double, val rangeKm: Double, val rangeRateKmS: Double)

/** One moment as seen from both stations. */
data class MutualMoment(val time: Instant, val home: LookAngle, val other: LookAngle) {
    val lowerElevationDeg: Double get() = min(home.elevationDeg, other.elevationDeg)
    val totalPathKm: Double get() = home.rangeKm + other.rangeKm
}

/** A continuous time when both stations see the satellite at or above their minimum elevations. */
data class MutualWindow(
    val catnum: Int,
    val name: String,
    val start: Instant,
    val end: Instant,
    val best: MutualMoment,
)

sealed interface PlannerItem {
    val catnum: Int
    data class Windows(override val catnum: Int, val windows: List<MutualWindow>) : PlannerItem
    /** A stationary satellite above both minimum elevations now. */
    data class UsableNow(override val catnum: Int, val name: String, val home: LookAngle, val other: LookAngle) : PlannerItem
    data class Skipped(override val catnum: Int) : PlannerItem
}

object ContactPlanner {
    const val STEP_SECONDS = 5

    /** Every mutual window of [tle] between [start] and [start] + [hours]. */
    fun windows(tle: TLE, home: PlannerStation, other: PlannerStation, start: Instant, hours: Int): PlannerItem

    /** The moments of a window, one a minute plus the start, best moment and end (requirement 6.3). */
    fun timeline(tle: TLE, home: PlannerStation, other: PlannerStation, window: MutualWindow): List<MutualMoment>

    fun greatCircleKm(a: PlannerStation, b: PlannerStation): Double
}
```

### The search

Stepping both stations through 24 hours every 5 seconds is 17,280 steps per satellite, too slow for all satellites (requirement 9). The search instead uses the pass predictor to narrow the time down first:

1. If `SatelliteMotion.isStationary(tle)`: take `SatelliteMotion.fixedPosition` for both stations; return `UsableNow` when both are at or above their minimums, otherwise `Windows(emptyList())`.
2. Get each station's passes with its own `PassPredictor` and `GroundStationPosition`: `getPasses(start, hours, windBack = true)`.
3. Intersect the two pass lists (both are sorted by time, so a single walk through both). Each overlap of a home pass with an other-station pass is a candidate. Clip candidates to the search start.
4. Step each candidate every `STEP_SECONDS`, computing both look angles with fresh `GroundStationPosition`s for this call. A window is a run of steps where both elevations are at or above their minimums. A candidate can hold more than one window when a minimum elevation is above 0°.
5. The best moment is the step with the highest lower elevation; ties within 0.1° go to the shorter total path (requirement 7).
6. Any exception from the prediction code for one satellite gives `Skipped`, as in the overview.

Window edges are at step resolution, so within 5 seconds (requirement 4.3). Distant stations have few candidates, so most satellites end after step 3.

### All satellites

```kotlin
// domain/ContactPlannerCalculator.kt
class ContactPlannerCalculator(dispatcher: CoroutineDispatcher = /* as OverviewCalculator */) {
    fun calculate(tles: List<TLE>, home: PlannerStation, other: PlannerStation, start: Instant, hours: Int): Flow<PlannerItem>
}
```

The same `channelFlow` shape as `OverviewCalculator`: one coroutine per satellite, `ensureActive()` between satellites, cancelled with the collector.

## Data and settings

```kotlin
// data/SavedStation.kt
data class SavedStation(
    val id: String,              // random UUID, created when the station is added
    val name: String,            // may be empty
    val latitude: String,        // as LocatorSync stores them: decimal degrees as text
    val longitude: String,
    val locator: String,
    val minElevationDeg: Int,
)

object SavedStationsJson {
    fun write(stations: List<SavedStation>): String
    /** Tolerant: a missing, corrupt or wrong-shaped value gives an empty list; a bad entry is dropped. */
    fun read(text: String?): List<SavedStation>
}
```

New settings in `SettingsRepository`, all with defaults so upgrades need no migration:

| Key | Type | Default |
|---|---|---|
| `plannerStations` | JSON string, `SavedStationsJson` | empty list |
| `plannerStationId` | string | empty (none chosen) |
| `plannerHomeMinElevation` | int | 0 |
| `plannerHours` | int | 24 |
| `plannerAllSatellites` | boolean | false |

`PLANNER_MIN_ELEVATION_CHOICES = listOf(0, 5, 10, 15, 20, 30)` and `PLANNER_HOUR_CHOICES = listOf(12, 24, 48, 72)` sit beside the overview choices. At most 20 stations (O4); Add station is disabled at the limit with a message.

## View model

`ui/ContactPlannerViewModel`, modelled on `OverviewViewModel`:

```kotlin
data class PlannerUiState(
    val locationSet: Boolean,
    val stations: List<SavedStation>,
    val chosen: SavedStation?,
    val homeMinElevation: Int,
    val hours: Int,
    val allSatellites: Boolean,
    val distanceKm: Double?,
    val searching: Boolean,
    val progress: Pair<Int, Int>?,      // satellites done, total, when searching all
    val windows: List<MutualWindow>,    // sorted by start
    val usableNow: List<PlannerItem.UsableNow>,
    val staleWarning: String?,
    val expanded: Pair<Int, Instant>?,  // the window whose detail is open: catnum and start
)
```

- A search key of (home position, chosen station, minimums, hours, scope, keps generation) restarts the search when any part changes; the old job is cancelled.
- The home position comes from the live `homeLat` and `homeLon` settings flows, checked with `HomeLocation.isSet`.
- A one-minute tick drops windows whose end has passed (requirement 5.5).
- The timeline and Doppler for an open window are worked out on demand, off the main thread, and kept while it is open.

## Screens

### Planner (`Routes.PLANNER = "planner"`)

From the top, in a scrolling column with the 600 dp maximum width the setup guide uses:

1. **Stations.** "Your station: IO82xk, minimum 0°" with a minimum-elevation picker. Then "Other station" as a dropdown of saved stations, with Edit and Add station buttons, and the distance between them.
2. **Search.** Selected satellite or All satellites (a segmented button), and the time window picker.
3. **Results.** Progress while searching. Each window is a card in the passes-list style: satellite name (when searching all), date, start to end, duration, "Best 16:27:34 (16:27:34 UTC), both above 3.7°". "Usable from both stations now" cards for stationary satellites come first.
4. **Detail.** Tapping a card expands it in place (no new route, so the list keeps its scroll position): a two-column block, home and other, with azimuth, elevation and range at the best moment, the total path, the mode picker and each station's Tune and Transmit frequencies, then the timeline as rows of "16:26 · home az 292° el 2.1° · other az 49° el 3.0°".

The unset-location message and Set up button from the overview are reused (requirement 1.2).

### Station editor (`Routes.PLANNER_STATION = "planner/station?id={id}"`)

A full screen, not a dialog. Text fields in Compose dialogs are hard to drive from adb, which the emulator checks rely on, and a screen gives room at 200 percent font. It has Name, IARU locator, Latitude, Longitude, Minimum elevation, and Save, Cancel and (when editing) Delete with a confirmation dialog.

The location fields use `LocatorSync` directly, with the same field order, labels and messages as `LocationEditor`. They hold drafts in the editor's own state, not in `SettingsRepository`. There are no GPS or network buttons: the other station is not where the device is. `LocationEditor` itself is not changed; extracting a shared stateless field group from it is possible later but not needed now.

## Strings

All new text in `strings.xml`: the menu item, screen titles, section headings, labels, the empty and no-station messages, the progress line, the card and detail formats, and the editor's labels and messages. Existing strings reused: `overview_no_location`, the Set up button, the Doppler labels (`doppler_*`), the stale-elements warning.

## Testing

- `ContactPlannerTest` (JVM):
  - the RS-44 IO82xk and EM29 case with a fixed TLE and start time: three windows with the expected edges and best moments, kept as a regression check;
  - window edges against a brute-force 1-second search for an LEO (ISS), a high LEO (AO-7) and an elliptical orbit, within 5 seconds (requirement 10.1);
  - a minimum elevation above 0° splitting one overlap into two windows, or none;
  - a window open at the start time;
  - stations too far apart: no windows;
  - the same station twice: windows equal that station's passes;
  - a stationary satellite: `UsableNow` or nothing;
  - the best-moment tie rule;
  - look angles equal to `getPosition` for each station at the same instant (requirement 10.2).
- `ContactPlannerCalculatorTest`: all satellites in parallel give the same results as one at a time; cancelling stops the work.
- `SavedStationsJsonTest`: round trip, corrupt text, a bad entry dropped, unknown fields ignored.
- `ContactPlannerViewModelTest` (Robolectric, as the overview's): the search restarts on each choice, no search while unset, ended windows dropped.
- Speed (requirement 9): measured on the SM-T225 with a debug build and a log line, written into the task.
- UI: emulator scripts in the gitignored `build/` directory, as for the setup guide (there are no Compose UI tests in the project).
- The golden regression test stays green and unchanged.
