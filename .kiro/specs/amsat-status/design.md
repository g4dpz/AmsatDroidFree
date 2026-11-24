# Design: AMSAT satellite status reports

## Where things are today

- Downloads go through `KepsDownloader` / `HttpsKepsDownloader` (`data/KepsDownloader.kt`): https only, no redirects, a 1 MB limit (`MAX_RESPONSE_BYTES`), timeouts and a User-Agent from `HttpsKepsDownloader.userAgent`. `KepsDownloadPolicy.classify` (`data/KepsDownloadPolicy.kt`) turns an HTTP status into `DownloadOutcome.Success`, `Transient` or `Permanent` (3xx, 403, 404 and 429 are permanent).
- `TransponderDatabaseRepository` (`data/TransponderDatabaseRepository.kt`) is the closest pattern: a stored copy in `FileStore`, a status file, a minimum interval, a lock that serialises the activity and the worker, and a `sync()` that never throws. `HamSatDroidApp` holds the loaded database as a `StateFlow` and reloads it after a successful sync.
- Satellites are `SatelliteItem(catnum, name, epochMillis)` in `AppUiState.satellites`; `SatelliteOrder.oscarTag` reads an OSCAR designation from a name and returns its number as an integer, so `AO-07` and `AO-7` already compare equal.
- `AppViewModel.onStart()` is called from `MainActivity.onStart()`, once per activity start, whatever screen shows. Screens that need to know when they are in view use `LifecycleStartEffect` (as `ContactPlannerScreen` does).
- Routes are in `Routes` (`ui/AppNavHost.kt`); the overflow menu is in `HomeScreen` (`ui/PassesScreen.kt`). `AppNavHost` builds `OverviewViewModel(viewModel.tles, settings)` for the overview and `ChooseSatellitesScreen(viewModel, settings, ...)` for the chooser.
- Times go through `DisplayFormats` (`ui/PassFormatting.kt`), which has `utcTime(instant)`; counts use `pluralStringResource`.

OrbitDeck for iOS does the same job in `AmsatStatusService` and `AmsatHeardStore` (`OrbitDeckIOS/Engine/FeatureEngine.swift`) and `AmsatStatusInteractiveView` (`OrbitDeckIOS/Views/FeatureViews.swift`): a board with a 3 to 72 hour window, heard/total and grid counts, "Heard N h ago" marks in its satellite list from a 24-hour summary refreshed at most every 10 minutes, and name matching with an alias table. OrbitDeck also posts reports (`AmsatStatusService.submitReport`, with a Report Status tab and a quick report on its Home screen, each with a confirmation that the post is public). This design follows that behaviour in Kotlin, with two differences: an app-named User-Agent as AMSAT asks (OrbitDeck sends a browser one), and no automatic retries beyond the 5-minute rule.

## The change

### Model and parsing (pure, `domain`)

    enum class ReportKind { HEARD, NOT_HEARD, TELEMETRY_ONLY, CREW_ACTIVE }
    data class StatusReport(val kind: ReportKind, val count: Int, val latest: Instant)
    data class BoardEntry(val boardName: String, val displayName: String, val mode: String?, val noradId: Int?)
    data class EntryReports(val entry: BoardEntry, val reports: List<StatusReport>)

- `AmsatStatusParsing.catalog(json): ParseResult<List<BoardEntry>>` reads `catalog.php` (`name`, `display_name`, `norad_cat_id`). The mode is the text in square brackets of `display_name` (`ISS [SSTV]` gives `SSTV`); no brackets gives `null`.
- `AmsatStatusParsing.summary(json): ParseResult<Map<String, List<StatusReport>>>` reads `summary.php` rows (`name`, `report`, `report_count`, `latest_observation_time`) keyed by board name. The four kinds are `Heard`, `Not Heard`, `Telemetry Only` and `Crew Active`; a row with another kind or bad fields is skipped.
- `AmsatStatusParsing.gridCounts(json): ParseResult<GridCounts>` reads one `reports.php` page and returns, per board name, the number of distinct 4-character grids (the first four characters of `grid_square`, upper case), plus `more = meta.next_cursor != null`. Callsigns and full grids are dropped as soon as each row is counted; the result type cannot hold them, and a test checks that (Requirement 4.7). The raw `reports.php` body is never stored or logged.
- `ParseResult` is `Ok(value)` or `Unreadable`: missing or wrong-shaped top-level `data`, invalid JSON, an empty body, or a non-empty `data` list from which no row can be read is `Unreadable`; `{"data": []}` is `Ok(empty)`; a list with some readable rows is `Ok` with those rows. The repository stores only `Ok` (Requirement 1.6).
- `AmsatStatusMatcher.match(catalog: List<BoardEntry>?, summary, satellites): StatusBySatellite` joins the summary to the catalogue by board name (Requirement 2.1; summary names missing from the catalogue become entries with no NORAD number, display name from the board name with underscores as spaces) and matches each entry (Requirement 2.2 and 2.3): a NORAD number is used alone; otherwise `ALIASES` (`"CAS-3H" to "LILACSAT-2"`, extended as gaps are found), then the OSCAR designation, then the normalised name, each accepted only when exactly one satellite fits. The result has, per catalogue number, the list of `EntryReports` for that satellite, including entries with no reports when the catalogue is known, and the list of entries that matched nothing (for the status screen).
- `StatusBySatellite.view(catnum): StatusView` is what the passes screen draws: `NotShown` (not on the board, or no catalogue), `NoReports`, or `Modes(list of mode, kind, count, latest)`. `StatusBySatellite.lastHeard(catnum): Instant?` gives the marks. `BoardRows.of(summary, gridCounts, match): List<BoardRow>` gives the status screen's rows, sorted (Requirement 4.3). Words are not built here (Requirement 7.1).
- `AgoText` (pure) turns a duration into a unit (minutes, hours or days) and a count; `ui` picks the plural string.

Test fixtures in `app/src/test/resources/`: `amsat_status_summary_20261124.json`, `amsat_status_catalog_20261124.json`, `amsat_status_reports_20261124.json` (generated from the shape of the 24 November 2026 page, not copied from it: synthetic callsigns, synthetic valid 4- and 6-character grids and shifted times, keeping the number of entries, reports per entry and distinct grids the tests need) and `celestrak_amateur_20261120.json` (the 99-satellite Amateur group of 20 November). The existing 8-satellite `celestrak_amateur_sample.json` is too small for the measured counts.

### Fetching and storing (`data`)

`AmsatStatusRepository(files, downloader, clock)`, modelled on `TransponderDatabaseRepository`:

- Addresses: `catalog.php`; `summary.php?hours=H`; `reports.php?hours=H&limit=500`, each under `https://status.amsat.org/api/v1/`. Each address has its own stored copy (`amsat_status_<address>.json`, written atomically after an `Ok` parse, raw for the catalogue and summaries; for reports only the parsed grid counts are stored) and its own fetch record (last attempt, last success, "refused until") in `amsat_status_fetch.json`.
- `refreshIfDue(windows: Set<Int>, enabled: () -> Boolean): RefreshOutcome` runs under one lock (single flight): it fetches the catalogue when none is stored or it is over 24 hours old, then the 24-hour summary, then each extra window's summary and reports, each only when its copy is over 15 minutes old. Before each request it checks `enabled()` and stops if the switch is off; the job is also cancelled when the switch goes off (Requirement 1.8). An address attempted less than 5 minutes ago, or paused, is skipped. Every attempt is recorded before the lock is released. `AmsatStatusPolicy.pauseFor(status, retryAfter)` gives each answer its pause (Requirement 1.5): 429 the `Retry-After` value or 15 minutes, 5xx and network failures the 5-minute rule, 400 or 422 one hour, 3xx, 403 and 404 24 hours. `clear()` deletes every stored status file, used when the switch is turned off. `refreshNow(window)` (the Refresh button) is the same without the 15-minute condition and returns when the next attempt is allowed.
- `load(): StoredStatus` reads and parses the stored copies with their fetch times. Called off the main thread.
- `createAmsatStatusRepository(context)` builds it with a downloader whose User-Agent includes the project URL (Requirement 1.4): `HttpsKepsDownloader.userAgent` gains an optional contact part, used here. A test checks the factory's User-Agent.

A 72-hour reports page measured 237 KB on 24 November 2026, within the 1 MB limit.

### App-level state

`HamSatDroidApp` holds one `AmsatStatusRepository` and an `amsatStatus: StateFlow<StoredStatus?>` (parsed copies in memory, Requirement 6.3), loaded at start on the IO dispatcher and set again whenever the repository stores something new. `requestAmsatStatus(windows)` launches `refreshIfDue` on the IO dispatcher when `showAmsatStatus` is on; overlapping calls are harmless because of the lock and the 5-minute rule.

### Settings

- `SettingsRepository.showAmsatStatus` (`Setting<Boolean>`, key `showAmsatStatus`, default off per D1). A Settings row in the Display section: "Show AMSAT status reports", with the line "From AMSAT's Live OSCAR Satellite Status board".
- `SettingsRepository.amsatStatusHours` (`Setting<Int>`, one of 3, 6, 12, 24, 48, 72, default 24) for the status screen's window.

### Wiring

- `AppViewModel` gains `statusBySatellite: StateFlow<StatusBySatellite?>`, combining the app's `amsatStatus`, the current satellites, `showAmsatStatus.state` and a one-minute clock tick: `null` when the setting is off or the 24-hour summary is over 24 hours old (Requirement 1.8, 3.4). The matching runs on the default dispatcher.
- A shared `RequestAmsatStatusEffect(windows)` (a `LifecycleStartEffect`) is placed in `HomeScreen`, `OverviewScreen`, `ChooseSatellitesScreen` and the status screen (which passes its window), so each asks for a refresh when it comes into view. `AppViewModel` also asks when `showAmsatStatus` changes from off to on. `AppViewModel.onStart()` does not ask.
- The overview gets the map through `OverviewScreen(..., status: StatusBySatellite?)`, passed from `AppNavHost` (`viewModel.statusBySatellite`); `OverviewViewModel` is unchanged. `UpNowCard` and `UpcomingCard` gain a `lastHeard: Instant?` argument. `ChooseSatellitesScreen` already has `viewModel`; `ChooseRow` gains `lastHeard: Instant?`.

### Screens

- **AMSAT status screen** (`ui/AmsatStatusScreen.kt`, `Routes.AMSAT_STATUS`, menu item "AMSAT status" below "All satellites"): a top bar with back; when the switch is off, an explanation and a "Turn on" button. Otherwise a window picker (3 to 72 h, a `DropdownField` as elsewhere), a Refresh button, the fetch time and any "could not update" or "next refresh at" message, then a `LazyColumn` of `BoardRow`s: display name, "N heard of M reports", "Heard 3 h ago" or the latest kind and age, the grid count, and either the matched satellite's name or "Not in your orbital elements". A matched row selects the satellite (`viewModel.selectSatellite`) and pops back to the passes screen. At the bottom, the link to status.amsat.org and the credit.
- **Passes screen:** `PassesControls` shows the block for the selected satellite below the pickers (above the choose-satellites banner when that shows), from `StatusView`, composed in `ui` with `strings.xml` plurals and `AgoText`: "AMSAT reports, last 24 h" then one line per mode, or "No AMSAT reports in the last 24 hours"; "as of HH:MM UTC" when the copy is over 30 minutes old. The block is one clickable element that opens the status screen.
- **All satellites and chooser:** "Heard 3 h ago" on rows whose satellite has a Heard report in the last 24 hours.
- **About:** "Satellite status data: AMSAT, https://status.amsat.org" beside the OscarWatch credit.

## Stage 2: posting reports

### Model (pure, `domain`)

    data class ReportDraft(val entry: BoardEntry, val kind: ReportKind, val observedAt: Instant)
    data class ReportSubmission(val boardName: String, val report: String, val callsign: String, val grid: String, val observationTime: String)
    sealed interface PostResult { data class Posted(val correction: Boolean); data class Rejected(val code: String, val message: String); data class Failed(val reason: String) }

- `CallsignCheck.problem(text): CallsignProblem?` (W1, Requirement 8.1): a Kotlin port of AMSAT's `api_call_sign_is_valid` and `api_call_sign_component_is_valid` (`api/v1/lib/validation.php`, Apache-2.0), plus the 14-character limit; returns the reason for the first fault, which the Settings field shows the way `locatorProblemText` (`ui/components/LocatorProblem.kt`) does for the locator. Tested with AMSAT's own cases: standard calls, prefixes and suffixes with `/`, `-` forms, all-digit and three-character input, the 14-character boundary.
- `ReportSubmission.from(draft, callsign, homeLocator, sixCharacterGrid)` builds what is sent: `report` is `Heard`, `Telemetry Only`, `Not Heard` or `Crew Active`; `grid` is the first 4 (or 6) characters of the home locator in AMSAT's form (`IO82`, `IO82xl`); `observationTime` is UTC with `Z`, whole seconds. Pure and tested.
- `ReportTime.default(passes, now)` (W5): the time of greatest elevation of the latest pass whose LOS is within the last hour, else one minute before `now`. `ReportTime.allowed(t, now)`: not after `now`, not more than 24 hours before.
- `ReportKinds.offered(entry)` (W4): the four kinds, with Crew active only when the entry's mode is `Crew`.
- `AmsatPostParsing.result(status, body): PostResult`: 201 with `data.correction`; 422 with `error.code` and `error.message`; anything else `Failed`.

### Sending (`data`)

- `KepsDownloader` is GET-only. A sibling `HttpsJsonPoster` (`fun post(url, json): DownloadResponse`) gets the same safeguards as `HttpsKepsDownloader` (https only except the debug address below, which applies to the reader too, no redirects, size limit, timeouts, the User-Agent) and sends `Content-Type: application/json`. It never retries.
- `AmsatStatusRepository.submit(submission, enabled: () -> Boolean): PostResult` checks `enabled()` and posts to `reports.php`, outside the refresh lock (posting is a user action, not polling), and on `Posted` marks the 24-hour summary as due so the next screen view refreshes it within the 5-minute rule. It records the operator's own last report per board name in `amsat_status_own.json` (Requirement 9.7). The callsign and grid are never logged (Requirement 8.3).
- Base address: `https://status.amsat.org/api/v1/` in every build by default. Debug builds only (the code lives in `src/debug`, so release builds do not contain it) can switch to AMSAT's local stack from a Developer section in Settings, choosing `http://10.0.2.2:8081/api/v1/` (the emulator) or `http://localhost:8081/api/v1/`; no other value can be entered. The same override applies to reading and posting, and a debug-only `src/debug/res/xml/network_security_config.xml` allows cleartext to `10.0.2.2` and `localhost` alone (Requirement 10.2).

### Settings

- `SettingsRepository.reportCallsign` (`Setting<String>`, key `reportCallsign`, empty) and `reportSixCharacterGrid` (`Setting<Boolean>`, key `reportSixCharacterGrid`, false). Changing or clearing the callsign deletes `amsat_status_own.json`. The callsign is kept out of cloud backups by the manifest's existing `allowBackup="false"`. A Settings section "AMSAT status reports" holds the existing switch, the callsign field (a `CommitField` checked with `CallsignCheck`, as the locator field is) and the 6-character choice, with one line explaining that the callsign is sent only with a report you submit.

### Screens

- **Report button:** on the passes-screen block and on each matched status-screen row. Enabled when the switch is on, a valid callsign is set and the home location is set; otherwise disabled with the reason (Requirement 9.1).
- **Report form** (`ui/AmsatReportScreen.kt`, `Routes.AMSAT_REPORT` with the satellite's catalogue number and an optional board name): a full screen, like the station editor, so it works on a 360 dp phone. Entry picker (the satellite's catalogue entries), kind as radio buttons, observation time (UTC) with Change, using the passes screen's date and time pickers limited by `ReportTime.allowed`, the callsign and grid that will be sent (read-only, with a link to Settings), and Send.
- **Confirmation:** an `AlertDialog`, "Post a public report to AMSAT?", listing satellite and mode, kind, time UTC, callsign and grid, with "This appears on status.amsat.org under your callsign." Buttons Post and Cancel (W6).
- **Result:** posted (with the correction wording when `correction` is true) returns to the previous screen with a snackbar; a rejection or failure stays on the form with the reason and Try again.
- **Own last report:** shown under the passes-screen block and on the status-screen row ("You reported Heard at 20:22 UTC").

## What does not change

Prediction, keps, the transponder database, alerts, the widget, the sky screen, the map, the Contact planner and its search, `PassStore`.

## Risks

- **The API changes or goes away.** Unreadable responses keep the stored copy, and after 24 hours the marks and the block show nothing; the status screen says it could not update. The rest of the app is unaffected.
- **AMSAT blocks app-named User-Agents.** OrbitDeck's code says some CDNs block custom agents, so it sends a browser one. Our test with an app-named one worked on 24 November 2026. If requests start failing with 403, look here first, and ask AMSAT rather than switch to a browser agent.
- **Wrong matches.** A NORAD number is never overridden, and an ambiguous alias, designation or name matches nothing; tests cover a two-candidate name.
- **Reading too much into the numbers.** The words describe reports, not satellite health (S5); a satellite AMSAT does not track shows nothing rather than "no reports".
- **Other people's data.** Reports carry callsigns and grids; the app only counts grids and keeps the counts (Requirement 4.7), and the committed test fixture is synthetic.
- **Privacy.** A new server is contacted; stage 1 is gated on the privacy policy and data safety answers (Requirement 5.2), and stage 2 again, since it sends the operator's callsign and grid (Requirement 5.3).
- **A report sent by mistake.** Only a confirmed tap sends one, and AMSAT treats a second report from the same callsign in the same 15-minute period as a correction of the first, so a wrong report can be fixed by posting the right one straight away.
- **Testing against the live board.** Never done: tests use a fake transport, and end-to-end checks use AMSAT's local stack (Requirement 10).
