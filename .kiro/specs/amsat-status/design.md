# Design: AMSAT satellite status reports

## Where things are today

- Downloads go through `KepsDownloader` / `HttpsKepsDownloader` (`data/KepsDownloader.kt`): https only, no redirects, a 1 MB limit (`MAX_RESPONSE_BYTES`), timeouts and the app's User-Agent (`HttpsKepsDownloader.userAgent`).
- `TransponderDatabaseRepository` (`data/TransponderDatabaseRepository.kt`) is the closest pattern: a stored copy in `FileStore`, a status file with the last answer, success and failure, a minimum interval between fetches, and a `sync()` that never throws. Its factory `createTransponderDatabaseRepository` builds it with the app's directory and User-Agent.
- Satellites are listed as `SatelliteItem(catnum, name, epochMillis)` in `AppUiState.satellites`; `SatelliteOrder.oscarTag` reads an OSCAR designation from a name.
- Settings are in `SettingsRepository` over a `KeyValueStore`.
- The passes screen controls are in `PassesControls` (`ui/PassesScreen.kt`); the overview rows are `UpNowCard` and `UpcomingCard` (`ui/OverviewScreen.kt`); the chooser rows are `ChooseRow` (`ui/ChooseSatellitesScreen.kt`); credits are in `AboutDialog`.

## The change

### Parsing and matching (pure, `domain`)

`AmsatStatus` holds the parsed summary:

    data class StatusReport(val mode: String, val kind: Kind, val count: Int, val latest: Instant)
    enum class Kind { HEARD, NOT_HEARD, TELEMETRY_ONLY }
    data class StatusEntry(val boardName: String, val noradId: Int?, val reports: List<StatusReport>)

- `AmsatStatusParsing.summary(json): List<...>` reads `summary.php` rows (`name`, `report`, `report_count`, `latest_observation_time`); `AmsatStatusParsing.catalog(json): Map<String, Int?>` reads `catalog.php` (`name` to `norad_cat_id`). The mode is the text in the square brackets of `name` (`ISS_[SSTV]` gives `SSTV`), with underscores as spaces. Unknown report kinds and unparseable rows are skipped. Both return empty for malformed input and never throw.
- `AmsatStatusMatcher.match(entries, satellites: List<SatelliteItem>): Map<Int, List<StatusReport>>` gives each catalogue number its reports, trying, in order: the entry's NORAD number when it is one of the satellites; its OSCAR designation (`SatelliteOrder.oscarTag` on the base name) matched against the satellites' designations; its base name normalised (upper case, letters and digits only) against each satellite's name, the name without the part in brackets, and each part in brackets. The first rule that matches wins; an entry that matches nothing is dropped.
- `AmsatStatusText` (pure) builds the words for one satellite: per mode, Heard count and latest time, or the other kinds by name when no Heard reports exist, and the "No AMSAT reports in the last 24 hours" case.

Test fixtures: the summary and catalogue captured on 24 November 2026 go into `app/src/test/resources/` (`amsat_status_summary_sample.json`, `amsat_status_catalog_sample.json`) with a satellite list taken from `celestrak_amateur_sample.json`.

### Fetching and storing (`data`)

`AmsatStatusRepository(files, downloader, clock)`, modelled on `TransponderDatabaseRepository`:

- Files: `amsat_status_summary.json`, `amsat_status_catalog.json` (the raw responses, written atomically after they parse) and `amsat_status_fetch.json` (last attempt and last success times for each).
- `load(): LoadedStatus?` reads and parses the stored copies, with the summary's fetch time.
- `refreshIfDue(): RefreshOutcome` fetches the catalogue if none is stored or it is more than 24 hours old, then the summary if the stored copy is more than 15 minutes old and the last attempt is more than 5 minutes ago (S4). A failed or malformed response keeps the stored copy and records the attempt time, so a failing server is tried at most every 5 minutes. Never throws.
- URLs: `https://status.amsat.org/api/v1/summary.php?hours=24` and `https://status.amsat.org/api/v1/catalog.php`.
- `createAmsatStatusRepository(context)` builds it like the transponder factory.

The response size limit is the existing 1 MB; both responses were under 50 KB when measured.

### Setting

`SettingsRepository.showAmsatStatus` (`Setting<Boolean>`, key `showAmsatStatus`, default on per S1). Off means no fetch and nothing shown. A Settings row in the Display section: "Show AMSAT status reports", with the line "From AMSAT's Live OSCAR Satellite Status board".

### Wiring

`AppViewModel` gains `amsatStatus: StateFlow<Map<Int, List<StatusReport>>>` plus the fetch time, built from the repository's stored copy and the current satellites, recomputed when either changes. `onStart()` (already called when the passes screen becomes visible) and the overview's start ask for `refreshIfDue()` on the IO dispatcher when the setting is on. The overview and the chooser read the same flow.

### Screens

- **Passes screen:** `PassesControls` shows one line for the selected satellite below the pickers (above the choose-satellites banner when that shows), from `AmsatStatusText`, tappable (opens `https://status.amsat.org/` with `ACTION_VIEW`), with "as of HH:MM" when the copy is more than 30 minutes old.
- **All satellites:** `UpNowCard` and `UpcomingCard` show "Heard in 24 h" for satellites with Heard reports.
- **Chooser:** `ChooseRow` shows the same tag under the catalogue number.
- **About:** a credit line beside the OscarWatch one (Requirement 4.1).

## What does not change

Prediction, keps, the transponder database, alerts, the widget, the sky screen, the map, the Contact planner and its search, `PassStore`.

## Risks

- **The API changes or goes away.** Parsing is defensive and the feature disappears quietly; the rest of the app is unaffected.
- **Wrong matches.** Matching by plain name could attach a report to the wrong satellite if two satellites share a base name; the NORAD and OSCAR rules come first, and the tests cover the measured case.
- **Reading too much into the numbers.** The words describe reports, not satellite health (S5).
- **Privacy wording.** A new server is contacted; the privacy policy and data safety answers must change in the same release (Requirement 4.2).
