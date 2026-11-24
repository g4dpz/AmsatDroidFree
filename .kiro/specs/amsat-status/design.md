# Design: AMSAT satellite status reports

## Where things are today

- Downloads go through `KepsDownloader` / `HttpsKepsDownloader` (`data/KepsDownloader.kt`): https only, no redirects, a 1 MB limit (`MAX_RESPONSE_BYTES`), timeouts and a User-Agent from `HttpsKepsDownloader.userAgent`. `KepsDownloadPolicy.classify` (`data/KepsDownloadPolicy.kt`) turns an HTTP status into `DownloadOutcome.Success`, `Transient` or `Permanent` (3xx, 403, 404 and 429 are permanent).
- `TransponderDatabaseRepository` (`data/TransponderDatabaseRepository.kt`) is the closest pattern: a stored copy in `FileStore`, a status file, a minimum interval, a lock that serialises the activity and the worker, and a `sync()` that never throws. `HamSatDroidApp` holds the loaded database as a `StateFlow` and reloads it after a successful sync.
- Satellites are `SatelliteItem(catnum, name, epochMillis)` in `AppUiState.satellites`; `SatelliteOrder.oscarTag` reads an OSCAR designation from a name.
- `AppViewModel.onStart()` is called from `MainActivity.onStart()`, once per activity start, whatever screen shows. Screens that need to know when they are in view use `LifecycleStartEffect` (as `ContactPlannerScreen` does).
- `AppNavHost` builds `OverviewViewModel(viewModel.tles, settings)` for the overview, and `ChooseSatellitesScreen(viewModel, settings, ...)` for the chooser.
- Times go through `DisplayFormats` (`ui/PassFormatting.kt`), which has `utcTime(instant)`; counts use `pluralStringResource`.

## The change

### Model and parsing (pure, `domain`)

    enum class ReportKind { HEARD, NOT_HEARD, TELEMETRY_ONLY, CREW_ACTIVE }
    data class StatusReport(val kind: ReportKind, val count: Int, val latest: Instant)
    data class BoardEntry(val boardName: String, val displayName: String, val mode: String?, val noradId: Int?)
    data class EntryReports(val entry: BoardEntry, val reports: List<StatusReport>)

- `AmsatStatusParsing.catalog(json): ParseResult<List<BoardEntry>>` reads `catalog.php` (`name`, `display_name`, `norad_cat_id`). The mode is the text in square brackets of `display_name` (`ISS [SSTV]` gives `SSTV`); no brackets gives `null`.
- `AmsatStatusParsing.summary(json): ParseResult<Map<String, List<StatusReport>>>` reads `summary.php` rows (`name`, `report`, `report_count`, `latest_observation_time`) keyed by board name. The four kinds are `Heard`, `Not Heard`, `Telemetry Only` and `Crew Active`; a row with another kind or bad fields is skipped.
- `ParseResult` is `Ok(value)` or `Unreadable`: missing or wrong-shaped top-level `data`, invalid JSON or an empty body is `Unreadable`; `{"data": []}` is `Ok(empty)`. The repository stores only `Ok` (Requirement 1.6).
- `AmsatStatusMatcher.match(catalog: List<BoardEntry>?, summary, satellites): StatusBySatellite` joins the summary to the catalogue by board name (Requirement 2.1; summary names missing from the catalogue become entries with no NORAD number, display name from the board name with underscores as spaces) and matches each entry (Requirement 2.2 and 2.3): a NORAD number is used alone; otherwise the OSCAR designation, then the normalised name, each accepted only when exactly one satellite fits. The result has, per catalogue number, the list of `EntryReports` for that satellite, including entries with no reports when the catalogue is known; a satellite absent from the result is not on the board (or the catalogue is unknown).
- `StatusBySatellite.lines(catnum): StatusView` is what the screens draw: `NotShown` (not on the board, or no catalogue), `NoReports`, or `Modes(list of mode, kind, count, latest)`. Words are not built here (Requirement 6.1).

Test fixtures in `app/src/test/resources/`: `amsat_status_summary_20261124.json`, `amsat_status_catalog_20261124.json` (captured 24 November 2026) and `celestrak_amateur_20261120.json` (the 99-satellite Amateur group of 20 November). The existing 8-satellite `celestrak_amateur_sample.json` is too small for the measured counts.

### Fetching and storing (`data`)

`AmsatStatusRepository(files, downloader, clock)`, modelled on `TransponderDatabaseRepository`:

- Files: `amsat_status_summary.json` and `amsat_status_catalog.json` (the raw responses, written atomically after an `Ok` parse) and `amsat_status_fetch.json` (per endpoint: last attempt, last success, and "refused until" after a permanent refusal).
- `refreshIfDue(): RefreshOutcome` runs under one lock (single flight, like the transponder sync): it decides, fetches and records for each endpoint in turn. The catalogue is fetched when none is stored or it is over 24 hours old; the summary when it is over 15 minutes old. Each endpoint is skipped if it was attempted less than 5 minutes ago or is refused until later. Every attempt is recorded before the lock is released. Outcomes come from `KepsDownloadPolicy.classify`; `Permanent` sets "refused until" to 24 hours later.
- `load(): StoredStatus` reads and parses both stored copies with their fetch times. Called off the main thread.
- URLs: `https://status.amsat.org/api/v1/summary.php?hours=24` and `https://status.amsat.org/api/v1/catalog.php`.
- `createAmsatStatusRepository(context)` builds it with a downloader whose User-Agent includes the project URL (Requirement 1.4): `HttpsKepsDownloader.userAgent` gains an optional contact part, used here. A test checks the factory's User-Agent.

### App-level state

`HamSatDroidApp` holds one `AmsatStatusRepository` and an `amsatStatus: StateFlow<StoredStatus?>` (parsed copies in memory, Requirement 5.3), loaded at start on the IO dispatcher and set again whenever `refreshIfDue()` stores something new. `requestAmsatStatus()` launches `refreshIfDue()` on the IO dispatcher when `showAmsatStatus` is on; overlapping calls are harmless because of the lock and the 5-minute rule.

### Setting

`SettingsRepository.showAmsatStatus` (`Setting<Boolean>`, key `showAmsatStatus`, default off per D1). A Settings row in the Display section: "Show AMSAT status reports", with the line "From AMSAT's Live OSCAR Satellite Status board".

### Wiring

- `AppViewModel` gains `statusBySatellite: StateFlow<StatusBySatellite?>`, combining the app's `amsatStatus`, the current satellites, `showAmsatStatus.state` and a one-minute clock tick: `null` when the setting is off or the summary is over 24 hours old (Requirement 1.7, 3.4). The matching runs on the default dispatcher.
- A shared `RequestAmsatStatusEffect(requestStatus)` (a `LifecycleStartEffect`) is placed in `HomeScreen`, `OverviewScreen` and `ChooseSatellitesScreen`, so each asks for a refresh when it comes into view. `AppViewModel` also asks when `showAmsatStatus` changes from off to on. `AppViewModel.onStart()` does not ask.
- The overview gets the map through `OverviewScreen(..., status: StatusBySatellite?)`, passed from `AppNavHost` (`viewModel.statusBySatellite`); `OverviewViewModel` is unchanged. `UpNowCard` and `UpcomingCard` gain a `heard: Boolean` argument. `ChooseSatellitesScreen` already has `viewModel`; `ChooseRow` gains `heard: Boolean`.

### Screens

- **Passes screen:** `PassesControls` shows the block for the selected satellite below the pickers (above the choose-satellites banner when that shows), from `StatusView`, composed in `ui` with `strings.xml` plurals and `DisplayFormats.utcTime`: "AMSAT reports, last 24 h" then one line per mode, or "No AMSAT reports in the last 24 hours". "as of HH:MM UTC" when the copy is over 30 minutes old. The block is one clickable element that opens `https://status.amsat.org/` with `ACTION_VIEW`.
- **All satellites and chooser:** "Heard in 24 h" on rows whose satellite has a Heard report.
- **About:** "Satellite status data: AMSAT, https://status.amsat.org" beside the OscarWatch credit.

## What does not change

Prediction, keps, the transponder database, alerts, the widget, the sky screen, the map, the Contact planner and its search, `PassStore`.

## Risks

- **The API changes or goes away.** Unreadable responses keep the stored copy, and after 24 hours the feature shows nothing; the rest of the app is unaffected.
- **Wrong matches.** A NORAD number is never overridden, and an ambiguous designation or name matches nothing; tests cover a two-candidate name.
- **Reading too much into the numbers.** The words describe reports, not satellite health (S5); a satellite AMSAT does not track shows nothing rather than "no reports".
- **Privacy.** A new server is contacted; the release is gated on the privacy policy and data safety answers (Requirement 4.2).
