# Design: My Satellites (passes for several satellites)

## Overview

A second mode on the passes screen. In **My satellites** mode the app calculates the passes of every satellite in a saved list, merges them by start time and shows them in the existing list, each card naming its satellite. Selecting a pass, alerts, the sky view, Aim, the map and Doppler already follow the selected pass, so they need little or no change.

The work sits in four places: settings (the list and the mode), the domain (a merged calculation), the view model (a second calculation path and state), and the UI (mode control, editor, summary).

## What exists and what it means (version 2.0.0)

| Piece | Today | Consequence |
|---|---|---|
| `PassSummary` | has `catnum`, `satName`, `id = "catnum@startSecond"` | a mixed-satellite list needs no new identity |
| `AppUiState` | `selectedCatnum` plus `passes: List<PassSummary>` | the list already can hold several satellites (`showPass` adds a pass of another satellite) |
| `AppViewModel.calculate()` | one TLE, `PassCalculator`, `rawPasses` for storage, `savePasses(tle)` | needs a sibling path for many; storage needs a schema change |
| `PassStore` / `StoredPasses` | one `catnum`, one `satName`, `List<SatPassTime>`, schema 1 | change to per-pass satellite; keep reading schema 1 |
| `OverviewCalculator` | parallel, one predictor and ground station per satellite, `calculateOne` | reuse it; this is exactly the machinery needed |
| `SkyContent`, `MapContent` | `pass?.catnum ?: selectedCatnum` | work for any pass; map uses `selectedCatnum` so tapping a pass must also set it |
| `PassAlert` | per pass, with satellite | alerts work as they are |
| `WidgetUpdater.compute` | `selectedCatnum` only | needs a list path for O2 |
| `SettingsRepository` | typed `Setting`s, string/int/boolean store | add a list and a mode |

## Settings

```kotlin
enum class PassMode { ONE, MINE }

val passMode = Setting(enumOrDefault(store.getString(PASS_MODE, "ONE"), PassMode.ONE)) { store.putString(PASS_MODE, it.name) }

/** Catalogue numbers, in the order added. Stored as a comma-separated string; unparsable entries are dropped. */
val mySatellites = Setting(CatnumList.parse(store.getString(MY_SATELLITES, ""))) { store.putString(MY_SATELLITES, CatnumList.format(it)) }
```

`CatnumList` is a small pure object (parse, format, dedupe, drop non-positive), unit tested. Keys: `passMode`, `mySatellites`. If O1 is accepted, `overviewMinElevation` is reused (no new key).

## Domain: merged calculation

```kotlin
// domain/MergedPasses.kt
data class MergedResult(
    val passes: List<PassSummary>,     // sorted by start, then name
    val alwaysUp: List<UpNow>,         // stationary satellites above the horizon
    val skipped: Int,                  // bad elements, never rises, missing from the keps
)

class MergedPassCalculator(private val overview: OverviewCalculator = OverviewCalculator()) {
    suspend fun calculate(
        tles: List<TLE>, catnums: List<Int>, lat: Double, lon: Double,
        start: Instant, hoursAhead: Int, minElevation: Int,
    ): MergedResult
}
```

- `hoursAhead == 0` ("next pass only") is mapped to a search window of 24 hours with `take(1)` per satellite, because `OverviewCalculator.calculateOne` takes whole hours. Passes beyond the first of each satellite are dropped.
- It runs `overview.calculate(...)` over only the chosen TLEs and collects the flow, so parallelism, per-satellite predictors and cancellation are inherited. `Result.passes` are merged, filtered by `minElevation` (if adopted), sorted; `Result` with `now != null` and no passes and a stationary motion goes to `alwaysUp`; `Skipped` and catnums absent from the keps count in `skipped`.
- Pure and testable: it takes TLEs and returns data.

## View model

`AppUiState` gains `mode: PassMode`, `mySatellites: List<Int>`, `alwaysUp: List<UpNow>`, `skippedCount: Int`. `AppViewModel`:

- `setMode(mode)` writes the setting and clears results (requirement 3.10).
- `setMySatellites(list)` writes the setting and clears results.
- `calculate()` dispatches: ONE keeps the current code path unchanged; MINE calls `MergedPassCalculator`, using the same generation counter and cancellation as the single path so a newer request wins.
- `selectPass(id)` also sets `selectedCatnum` from the pass, so the map follows the pass (in ONE mode the pass is always the selected satellite's, so nothing changes).
- `showPass(pass)` (from the overview) sets `mode = ONE` first, then behaves as today.
- Header: `PassHeader.Merged(satelliteCount, passCount, hoursAhead, start, skipped)` and `PassHeader.MergedEmpty`.

### Storing results

`StoredPasses` becomes:

```kotlin
data class StoredPasses(
    val computedAtMillis: Long,
    val hoursAhead: Int,
    val mode: PassMode,
    val entries: List<StoredPass>,        // each: catnum, satName, SatPassTime
)
```

`PassStore` schema 2 writes this. `decode` accepts schema 1 (one `catnum`, one `satName`, list of passes) and converts it to entries with `mode = ONE`. Unknown schemas return null as today. Round-trip, schema-1 and corrupt-file tests are added.

## UI

### Mode control and summary (`PassesControls`)

- `SingleChoiceSegmentedButtonRow` with "One satellite" and "My satellites".
- ONE: the existing `DropdownField`.
- MINE: a read-only summary (`AO-7, AO-91 and 2 more`, or "None chosen") and an `OutlinedButton` "Edit". The stale-elements line (`passes_elements_stale`) becomes a merged line for the list.
- The two pickers' stacking logic (`PassesPaneLayout`) is reused for the range picker and the optional minimum elevation picker.

### Editor (`MySatellitesEditor`)

- Phone: a full-screen dialog (`Dialog` with `usePlatformDefaultWidth = false`) with a top bar (Cancel, "My satellites (n)", Done), a search `OutlinedTextField`, and a `LazyColumn` of rows (checkbox, name, catalogue number).
- Tablet and wide windows: the same content in a centred dialog 560 dp wide.
- State: a draft `Set<Int>` held with `rememberSaveable`; nothing is saved until Done. Filtering is by `name.contains(query, ignoreCase)` or `catnum.toString().contains(query)`.
- Rows: selected first (group header "Selected"), then the rest; saved catnums not in the keps in a third group marked "not in current elements" with a remove action.
- Keyboard: `imePadding()`, list scrolls above it.

### Passes list

`PassCard` gains `satelliteName: String?` shown at the start of the header in MINE mode (the layout from `pass-card-layout`). An "Always up" section (`item`s above the passes) lists stationary satellites with current az/el. The skipped note and the stale line sit in the heading.

### Alerts, sky, map

No structural change. `onPassClick` calls `selectPass`, which now also sets `selectedCatnum`. Verified in the manual checklist rather than assumed.

## Widget (O2, last task)

`WidgetUpdater.compute` reads `passMode` and `mySatellites`. In MINE mode it calls `PassPredictor.nextSatPass` per satellite (as it does for one today), picks the earliest, and returns `WidgetState.Ready(satName, pass)`. `decide` stays pure; a small `earliest(passes)` helper is added and tested. WorkManager refresh and the Glance layout are unchanged.

## Overview option (optional task)

A filter chip "My satellites" on the overview screen (when the list is not empty) limits `OverviewViewModel`'s TLEs to the saved catnums. No other change.

## Risks

- **Overlap with other work on `PassesScreen.kt`** (A4): keep the diff focused; land the card spec first.
- **`passes.json` migration**: covered by schema-1 tests and a manual upgrade check.
- **Long merged lists** are heavier to scroll and to remember: mitigated by the elevation filter and range choices.
- **"Next pass only" semantics** differ from the single mode; the heading says "next pass of each satellite".
- **Selection by `selectedCatnum` in the map**: a missed spot would show the wrong satellite; covered by a test that selects a pass of a second satellite and reads `selectedCatnum`.

## Testing

- Unit: `CatnumListTest`; `MergedPassCalculatorTest` (order, tie-break, always-up, skipped count, next-pass-only per satellite, missing catnums, filter, cancellation); `AppViewModelTest` additions (mode switch clears results, `selectPass` sets the satellite, `showPass` switches to ONE, newer calculation wins); `PassStoreTest` additions (schema 2 round trip, schema 1 read, corrupt); `SettingsRepositoryTest` (defaults, round trip, junk values); a performance test with 99 satellites that fails above a generous bound (not the two-second device target).
- Manual: `manual-test-checklist.md` in this folder.
