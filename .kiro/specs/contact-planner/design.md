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

/** A ground station for the planner: position in degrees (east positive). */
data class PlannerStation(val latitudeDeg: Double, val longitudeDeg: Double)

/** Where one station sees the satellite at one moment. */
data class LookAngle(val azimuthDeg: Double, val elevationDeg: Double, val rangeKm: Double, val rangeRateKmS: Double)

/** One moment as seen from both stations. */
data class MutualMoment(val time: Instant, val home: LookAngle, val other: LookAngle) {
    val lowerElevationDeg: Double get() = min(home.elevationDeg, other.elevationDeg)
    val totalPathKm: Double get() = home.rangeKm + other.rangeKm
}

/** A continuous time when both stations see the satellite at or above the minimum elevation. */
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
    /** A stationary satellite at or above the minimum elevation at both stations now. */
    data class UsableNow(override val catnum: Int, val name: String, val home: LookAngle, val other: LookAngle) : PlannerItem
    data class Skipped(override val catnum: Int) : PlannerItem
}

object ContactPlanner {
    const val STEP_SECONDS = 5

    /**
     * Every mutual window of [tle] between [start] and [start] + [hours], clipped to that interval.
     * [checkpoint] is called regularly so a caller can stop the work (see Cancellation).
     */
    fun windows(
        tle: TLE, home: PlannerStation, other: PlannerStation, minElevationDeg: Double, start: Instant, hours: Int,
        checkpoint: () -> Unit = {},
    ): PlannerItem

    /** The moments of a window, one a minute plus the start, best moment and end (requirement 6.3). */
    fun timeline(tle: TLE, home: PlannerStation, other: PlannerStation, window: MutualWindow): List<MutualMoment>

    fun greatCircleKm(a: PlannerStation, b: PlannerStation): Double
}
```

### The search

Stepping both stations through 24 hours every 5 seconds is 17,280 steps per satellite, too slow for all satellites (requirement 9). The search instead uses the pass predictor to narrow the time down first:

Let `end = start + hours`.

1. If `SatelliteMotion.isStationary(tle)`: compute both stations' full look angles at `start` with `Satellite.getPosition` (fresh `GroundStationPosition`s; `SatelliteMotion.fixedPosition` gives only azimuth and elevation, not range and range rate). Return `UsableNow` when both are at or above `minElevationDeg`, otherwise `Windows(emptyList())`.
2. Get each station's passes with its own `PassPredictor` and `GroundStationPosition`: `getPasses(start, hours, windBack = true)`.
3. **Recover a pass already in progress.** `windBack` rewinds only a quarter orbit, so a pass that began earlier than that is skipped (`PassPredictor.nextSatPass`). For each station, if the satellite is above the horizon at `start` and no returned pass covers `start`, add the interval from `start` to its LOS, found by stepping forward every 30 seconds with `getPosition` and refining to 1 second, bounded by `end`.
4. Intersect the two stations' interval lists (both sorted by time, so a single walk through both). Each overlap of a home interval with an other-station interval is a candidate. **Clip each candidate to `[start, end]`** and drop empty ones: `getPasses` returns whole passes, including one that starts before `end` but finishes after it.
5. Step each candidate every `STEP_SECONDS`, computing both look angles with fresh `GroundStationPosition`s for this call. A window is a run of steps where both elevations are at or above `minElevationDeg`; a run still open at the clipped end closes there. A candidate can hold more than one window when the minimum elevation is above 0°.
6. The best moment is the step with the highest lower elevation; ties within 0.1° go to the shorter total path (requirement 7).
7. Unusable elements (`InvalidTleException`, `IllegalArgumentException`) give `Skipped`, as in the overview. `SatNotFoundException` from the pass predictor has two causes. If the satellite can never be seen from that station (`willBeSeen` is false) that station has no spans and the result is an empty window list, not `Skipped`. Otherwise a horizon search ran out of time, which discards the passes already found, so it is rethrown and the satellite is `Skipped`, never reported as a finished search. `CancellationException` is never caught.

Window edges are at step resolution, so within 5 seconds (requirement 4.3). Distant stations have few candidates, so most satellites end after step 4.

### Deep-space satellites

The orbit model of a slow orbit (period 225 minutes or more, `TLE.isDeepspace`) refreshes its periodic terms only when the time moves 30 minutes from the last refresh, so the angles it gives for an instant depend slightly on the instants asked about before. To keep a result independent of the rest of the search, and `timeline` identical to `windows`, the planner builds a fresh model for every position of a deep-space satellite. A low-orbit satellite has no such state and one model serves every call. The sky screen has the same history dependence in the orbit library, so its values for the same instant can differ in the last decimals; the library is not changed.

### What the search guarantees

The candidates come from `PassPredictor`, which the passes screen also uses, and it has two known limits (`PassPredictor.nextSatPass` and `getPasses`):

- it looks for AOS in 60-second steps, so a pass shorter than about a minute can be missed;
- after each LOS it jumps three quarters of an orbit, so on an orbit where passes last longer than a quarter orbit (highly elliptical or very high orbits) a later pass can be missed.

The 5-second stepping of step 5 finds every window of 5 seconds or more inside a candidate, since such a window must contain a step. So the planner finds every mutual window of at least 60 seconds for satellites whose passes last less than a quarter orbit, which is every low-orbit amateur satellite. That is what requirement 4.3 promises. A window shorter than a minute is too short to make a contact, so these limits do not matter in practice.

A planner-side search that stepped both stations through the whole time range would remove the limits, but costs about 17,000 position calculations per satellite per day and would not meet requirement 9 for all satellites. If the limits ever matter, the right fix is in `PassPredictor` itself, where it would also help the passes screen; that is outside this spec.

### Cancellation

`ContactPlanner.windows` is synchronous, so cancelling the collector alone would not stop a satellite already being worked on. It takes a `checkpoint: () -> Unit` parameter (default does nothing, for tests and single calls) and calls it after each station's pass search, every 20 steps of the in-progress recovery of step 3, before each candidate, and every 100 steps of step 5. `ContactPlannerCalculator` passes a checkpoint that calls `ensureActive()` on the satellite's coroutine and then an optional `onCheckpoint` hook, which exists so a test can watch the work stop.

The planner's own loops therefore stop at the next checkpoint. One call to `getPasses` cannot be interrupted, because it runs inside the orbit library, which this spec does not change. That is the same limit the overview accepts: one satellite's pass search over the time window takes a few milliseconds (to be measured in task 5.2), so old work leaves the dispatcher within about that time.

### All satellites

```kotlin
// domain/ContactPlannerCalculator.kt
class ContactPlannerCalculator(dispatcher: CoroutineDispatcher = /* as OverviewCalculator */) {
    fun calculate(tles: List<TLE>, home: PlannerStation, other: PlannerStation, start: Instant, hours: Int): Flow<PlannerItem>
}
```

The same `channelFlow` shape as `OverviewCalculator`: one coroutine per satellite, cancelled with the collector. Each coroutine passes `{ ensureActive() }` as the checkpoint, so work already running stops too.

## Data and settings

```kotlin
// data/SavedStation.kt
data class SavedStation(
    val id: String,              // random UUID, created when the station is added
    val name: String,            // may be empty
    val latitude: String,        // as LocatorSync stores them: decimal degrees as text
    val longitude: String,
    val locator: String,
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
| `plannerMinElevation` | int, one value for both stations | 0 |
| `plannerHours` | int | 24 |
| `plannerAllSatellites` | boolean | false |

`PLANNER_MIN_ELEVATION_CHOICES = listOf(0, 5, 10, 15, 20, 30)` and `PLANNER_HOUR_CHOICES = listOf(12, 24, 48)` sit beside the overview choices. At most 10 stations (O4); Add station is disabled at the limit with a message. `ContactPlanner.windows` itself takes any number of hours; the screen offers at most 48.

## View model

`ui/ContactPlannerViewModel`, modelled on `OverviewViewModel`:

```kotlin
data class PlannerUiState(
    val locationSet: Boolean,
    val stations: List<SavedStation>,
    val chosen: SavedStation?,
    val minElevation: Int,              // one value, used for both stations
    val selectedCatnum: Int,            // the satellite chosen on the passes screen, used when not searching all
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

- **Search key.** (home position, chosen station, minimum elevation, hours, scope, the selected satellite when the scope is "selected", keps generation). A search starts when the planner is on screen with a station chosen and no results for the current key, and again whenever any part of the key changes; the old job is cancelled. The selected satellite and the keps generation are part of the key because both can change while the planner is not on screen (the selected satellite is chosen on the passes screen), and results for the old key must never be shown as current (requirement 4.9).
- **Screen lifecycle.** The view model holds the job, but the screen starts and stops it with `repeatOnLifecycle(Lifecycle.State.STARTED)` on the planner's back stack entry. In Navigation Compose an entry below the top of the stack is only CREATED, so opening the station editor, another screen or the home screen cancels the search; returning restarts a search that did not finish and keeps finished results whose key is unchanged. Popping the entry clears the view model.
- `refresh()` searches again from now with the same choices (the "Calculate again" button, as on the overview).
- The home position comes from the live `homeLat` and `homeLon` settings flows, checked with `HomeLocation.isSet`.
- A one-minute tick drops windows whose end has passed (requirement 5.5).
- The timeline and Doppler for an open window are worked out on demand, off the main thread, and kept while it is open.

## Screens

### Planner (`Routes.PLANNER = "planner"`)

From the top, in a scrolling column with the 600 dp maximum width the setup guide uses:

1. **Stations.** "Your station: IO82xk", then one "Minimum elevation (both stations)" picker. Then "Other station" as a dropdown of saved stations, with Edit and Add station buttons, and the distance between them.
2. **Search.** Selected satellite or All satellites (a segmented button), the time window picker, and a "Calculate again" button (the overview's `overview_refresh` string) that searches again from now.
3. **Results.** Progress while searching. Each window is a card in the passes-list style: satellite name (when searching all), date, start to end, duration, "Best 16:27:34 (16:27:34 UTC), both above 3.7°". "Usable from both stations now" cards for stationary satellites come first.
4. **Detail.** Tapping a card expands it in place (no new route, so the list keeps its scroll position): a two-column block, home and other, with azimuth, elevation and range at the best moment, the total path, the mode picker and each station's Tune and Transmit frequencies, then the timeline as rows of "16:26 · home az 292° el 2.1° · other az 49° el 3.0°".

The unset-location message and Set up button from the overview are reused (requirement 1.2).

### Station editor (`Routes.PLANNER_STATION = "planner/station?id={id}"`)

A full screen, not a dialog. Text fields in Compose dialogs are hard to drive from adb, which the emulator checks rely on, and a screen gives room at 200 percent font. It has Name, IARU locator, Latitude, Longitude, and Save, Cancel and (when editing) Delete with a confirmation dialog.

The location fields use `LocatorSync` directly, with the same field order, labels and messages as `LocationEditor`. A 4-character square (O5) is turned into the 6-character locator at its centre by adding `mm` before it reaches `LocatorSync` (EM29 becomes EM29mm); the field then shows EM29mm and a line under it says "EM29 is the whole square; this uses its centre". They hold drafts in the editor's own state, not in `SettingsRepository`. There are no GPS or network buttons: the other station is not where the device is. `LocationEditor` itself is not changed; extracting a shared stateless field group from it is possible later but not needed now.

## Strings

All new text in `strings.xml`: the menu item, screen titles, section headings, labels, the empty and no-station messages, the progress line, the card and detail formats, and the editor's labels and messages. Existing strings reused: `overview_no_location`, the Set up button, the Doppler labels (`doppler_*`), the stale-elements warning.

## Testing

- `ContactPlannerTest` (JVM):
  - the RS-44 IO82xk and EM29mm case, as a regression check. It uses the fixed elements below in `app/src/test/resources/rs44_20261114.tle`, never elements downloaded at test time, with home 52.4375 N 2.0417 W, other 39.5 N 95.0 W, minimum elevation 0°, start 2026-11-14T14:55:24Z and 72 hours (the function takes any number of hours; the screen offers up to 48). Expected windows, each edge within 5 seconds:

    | Window (UTC) | Best moment | Lower elevation |
    |---|---|---|
    | Sat 14 Nov 18:01:24 to 18:02:34 | 18:02:14 | 1.0° |
    | Sun 15 Nov 16:25:34 to 16:30:24 | 16:27:34 | 3.7° |
    | Mon 16 Nov 16:51:39 to 16:55:29 | 16:53:54 | 3.5° |

    ```
    RS-44 & BREEZE-KM R/B
    1 44909U 19096E   26318.18888056  .00000039  00000+0  10553-3 0  9993
    2 44909  82.5222 240.0153 0217895 153.3806 207.8682 12.79749890321535
    ```

  - window edges against a brute-force 1-second search for ISS, AO-7 and RS-44, within 5 seconds, and no brute-force window of 60 seconds or more missing (requirement 10.1), each with fixed elements in `app/src/test/resources` and a fixed start time;
  - a short mutual window, about 10 seconds, inside a longer candidate: found, because it contains a 5-second step;
  - a minimum elevation above 0° splitting one overlap into two windows, or none;
  - a window open at the start time, including a pass that began more than a quarter orbit before it (an elliptical or high orbit), which `windBack` alone misses;
  - a pass that starts before the search end and finishes after it: the window ends at the search end and the best moment is inside the search;
  - stations too far apart: no windows;
  - the same station twice: windows equal that station's passes;
  - a stationary satellite: `UsableNow` or nothing;
  - the best-moment tie rule;
  - look angles equal to `getPosition` for each station at the same instant (requirement 10.2).
- `ContactPlannerCalculatorTest`: all satellites in parallel give the same results as one at a time; cancelling after the work has started stops it (a checkpoint that counts calls stops being called), and cancellation is not reported as `Skipped`.
- `SavedStationsJsonTest`: round trip, corrupt text, a bad entry dropped, unknown fields ignored.
- `ContactPlannerViewModelTest` (Robolectric, as the overview's): the search restarts on each part of the key, including the selected satellite and the keps generation changing while the planner is not on screen; results for an old key are not shown as current; no search while unset; stopping the lifecycle cancels the job, and starting it again restarts an unfinished search but keeps finished results with an unchanged key; ended windows dropped.
- Speed (requirement 9): measured on the SM-T225 with a debug build and a log line, written into the task.
- UI: emulator scripts in the gitignored `build/` directory, as for the setup guide (there are no Compose UI tests in the project).
- The golden regression test stays green and unchanged.
