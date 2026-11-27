# Requirements: AMSAT satellite status reports

## Introduction

Operators report on AMSAT's Live OSCAR Satellite Status board whether they heard a satellite on a pass. AmsatDroid shows when a satellite passes but not whether anyone has heard it lately, so an operator can plan a pass on a satellite that is switched off or silent. OrbitDeck and Ham Satting already show these reports (see `docs/feature-matrix.md`); AmsatDroid does not.

This spec adds the board's recent reports to the app. It follows the approach of Paul Stoetzer's (N8HM) OrbitDeck for iOS (github.com/prstoetzer/OrbitDeckiOS, MIT licence), rewritten for AmsatDroid: a status board screen, "heard" marks beside satellites, and a line for the selected satellite. It covers both sides, as OrbitDeck does: **reading** the board (stage 1) and **posting your own reports** (stage 2). Stage 1 is built and released first; stage 2 follows it.

The source is AMSAT's public status API, `https://status.amsat.org/api/v1/` (version 1.5 when checked on 24 November 2026; no key):

- `summary.php?hours=N` lists, for the last N hours (1 to 720, default 24), one row per board entry and report kind, with a count and the latest time. The kinds are Heard, Not Heard, Telemetry Only and Crew Active. On 24 November 2026 the 24-hour summary had 32 rows (about 6 KB) covering 23 entries.
- `catalog.php` lists every entry on the board: 97 on 24 November 2026, one per satellite and mode (such as `ISS_[FM]`, `ISS_[SSTV]` and `ISS_[Crew]`), with a display name (`ISS [SSTV]`) and, for about half of them, the satellite's NORAD number.
- `reports.php?hours=N&limit=500` lists the individual reports, newest first, each with the reporter's callsign and grid square. The 24-hour page on 24 November 2026 held 170 reports (about 80 KB); the 72-hour window had more than 500, so a second page existed.
- `POST reports.php` accepts a report as JSON: `name` (the board name), `report` (Heard, Telemetry Only, Not Heard or Crew Active), `callsign` (required), `grid_square` (optional, 4 or 6 characters) and `observation_time` (when the satellite was heard, ISO 8601 with an explicit offset, not in the future). It answers 201 with the stored report and `correction: true` when the same callsign already reported that entry in the same 15-minute UTC period (the earlier one is kept but no longer counted), or 422 with a code (`unknown_satellite`, `invalid_callsign`, `invalid_grid_square`, `future_observation_time` and others).
- The API's usage guidelines (`https://status.amsat.org/api/v1/overview.php`) ask clients to send a User-Agent naming the application, its version and a way to reach the developer, to poll no more often than every 5 minutes, and to fetch in one request rather than one per satellite; clients that ignore them may be rate-limited or blocked. Report data is published under CC BY 4.0, credited as "Satellite status data: AMSAT, https://status.amsat.org". For submissions they say: each report must describe a real observation by the operator whose callsign it carries; it must be sent because the operator chose to send it, never in the background; `observation_time` is the time of the observation, in UTC; and data must not be used to track, profile or contact operators, or to locate them more precisely than the grid they chose to share.
- AMSAT offers no test server. Its source (github.com/AMSAT-NA/satellite-status, Apache-2.0) runs locally with `docker compose up`, which serves the v1 API at http://localhost:8081; that is where the app's posting is tested. A version 2 of the API is in development; v1 stays the stable one.

## Decisions made

- D1 (S1). **Off by default, with a switch** (David, 24 Nov 2026). A Settings row, "Show AMSAT status reports", off until the user turns it on. Until then the app makes no request to the status API and the status screen offers to turn it on. No build with the feature reaches a Play track before the privacy policy and data safety answers name the new server (Requirement 5.2).
- D2. **Follow OrbitDeck's functionality, read and write** (David, 24 Nov 2026: "can we copy the functionality?", then "lets spec it out with similar read and write functionality"). Stage 1 is the board screen, the heard marks and the selected-satellite line; stage 2 is posting reports. David will tell Paul.

S2 to S8 must be confirmed or changed by David before task 2 starts, and W1 to W7 before task 6 starts.

## Assumptions (please confirm or change)

- S2. **Where it shows.** A new "AMSAT status" screen from the menu (Requirement 4); on the passes screen, a short block under the pickers for the selected satellite; on the All satellites overview and in the chooser, a short "heard" mark beside a satellite. Not on the pass cards, the sky screen, the map, the widget or in alerts.
- S3. **Windows.** The status screen offers 3, 6, 12, 24, 48 and 72 hours, as OrbitDeck does, remembered between visits, default 24. The passes-screen block and the marks always use the last 24 hours.
- S4. **Fresh enough, never too often.** Data is fetched when a screen that shows it comes into view and the stored copy is older than 15 minutes, never more often than every 5 minutes for the same request. The catalogue is fetched at most once a day.
- S5. **Words, not verdicts.** The app reports what operators said ("Heard 3 h ago", "12 heard of 14 reports"), never that a satellite is working or dead.
- S6. **Only AMSAT's board.** SatNOGS DB (whether each transmitter is active) is a possible later addition, as its own spec.
- S7. **Not in the Contact planner, for now.** Ranking the planner's My satellites search by recent reports is a possible later step.
- S8. **Order.** This is built after the pass-card layout, unless David says otherwise.

Reporting (stage 2):

- W1. **A callsign in Settings.** A new "Your callsign" field, empty by default, stored on the device only, checked as a plausible amateur callsign (letters and digits, with an optional `/` suffix, at most 14 characters, as AMSAT truncates). Reporting is offered only when it is set and the status switch (D1) is on.
- W2. **Grid square from the home location, 4 characters.** The report carries the first four characters of the home locator (IO82, about 100 by 200 km), not six (IO82xl, about 5 km), to share as little as needed. A Settings choice "Send 6-character grid in reports", off by default, allows six.
- W3. **Where to report.** A "Report" button on the passes-screen block for the selected satellite and on each matched row of the status screen. Not from notifications or the widget, and never automatically.
- W4. **What you choose.** The satellite's board entry (its mode, such as ISS FM or ISS SSTV, from the catalogue), the kind (Heard, Telemetry only, Not heard, Crew active; Crew active only for entries whose mode is Crew), and the observation time.
- W5. **Observation time.** Defaults to the time of the satellite's most recent pass that has ended within the last hour (its time of greatest elevation), else to one minute ago (so a phone clock that runs a little fast does not make it a future time); it can be changed, but not to the future or more than 24 hours back. Shown and sent in UTC.
- W6. **Confirm every time.** Before sending, a confirmation shows exactly what will be published under the callsign: satellite and mode, kind, time (UTC), callsign and grid, and says it is public. No "don't ask again".
- W7. **No offline queue.** If sending fails, the form stays filled in with the reason and a Try again button; nothing is sent later on its own.

## Later (not in this spec)

- SatNOGS transmitter status, ranking in the Contact planner (S6, S7), an offline queue for reports (W7), and showing the individual reports for an entry.


## Requirement 1: Getting the reports

**User story:** As an operator, I want the app to know what other operators have reported about each satellite recently.

1. WHEN the switch (D1) is on and the status screen, the passes screen, the All satellites overview or the chooser comes into view, or the switch is turned on while one of them is in view, THEN the app SHALL fetch what that screen needs if the stored copy is older than 15 minutes: the 24-hour summary for all of them, and for the status screen also the summary and the reports for its chosen window.
2. The app SHALL make no more than one request to each address (endpoint and window) in any 5 minutes, counting every attempt (successful or not), however many screens ask at once. A Refresh on the status screen SHALL obey the same rule and say when the next refresh is possible.
3. The app SHALL fetch the catalogue when none is stored or the stored one is more than 24 hours old. WHEN the catalogue cannot be fetched and none is stored THEN the app SHALL still show the reports, matched by the rules in Requirement 2 that do not need the catalogue, and SHALL NOT show the "no reports" line (Requirement 3.2), since it cannot tell which satellites are on the board.
4. Requests SHALL use https only, no redirects, a size limit and timeouts, as the keps and transponder downloads do, and a User-Agent that names the app, its version and the project URL: `AmsatDroid/<version> (+https://github.com/g4dpz/AmsatDroidFree; Android <release>; amateur radio satellite tracker)`.
5. WHEN a request fails or the response cannot be read THEN the app SHALL keep the stored copy and SHALL NOT show an error dialog or block any screen; the status screen SHALL say the reports could not be updated. Each answer SHALL cost a pause for that address, decided for this API rather than taken from the keps rules: 429 waits for the server's `Retry-After`, or 15 minutes without one; 5xx and network failures follow the 5-minute rule; 400 or 422 to a request the app makes waits 1 hour; a redirect, 403 or 404 waits 24 hours. When the pause ends, the address SHALL be asked again as normal.
6. A response whose `data` is an empty list SHALL replace the stored copy (a quiet day is real data). A response that cannot be read SHALL NOT, and neither SHALL one whose `data` has rows of which none can be read (the API has probably changed). A response with some readable rows SHALL be used, keeping those rows.
7. The reports SHALL be fetched in one request per window (`limit=500`). WHEN more reports exist than one page holds THEN the app SHALL use the first page only and show every row's grid count as at least the number found ("40+ grids"). This is a deliberate simplification: the summary covers the whole window, the page only the newest 500 reports, and the app cannot tell which rows the missing reports belong to.
8. WHEN the switch is off THEN the app SHALL make no request to the status API and SHALL show no reports and offer no reporting, on every screen, at once. A refresh in progress SHALL stop before its next request (one already sent cannot be recalled), and a report form that is open SHALL NOT send. Turning the switch off SHALL delete the stored reports, grid counts, fetch records and the operator's own report history from the device; the callsign SHALL stay in Settings until the operator clears it.
9. The app SHALL send nothing to the API except these requests and the reports the operator submits under Requirement 9.

## Requirement 2: Matching reports to satellites

**User story:** As an operator, I want each report attached to the right satellite in my list.

1. The summary SHALL be joined to the catalogue by the entry's board name (such as `ISS_[SSTV]`). A summary row whose name is not in the stored catalogue SHALL be treated as an entry with no NORAD number.
2. An entry SHALL be matched to a satellite in the current elements as follows. WHEN the catalogue gives the entry a NORAD number THEN only that number SHALL be used: the entry matches the satellite with that catalogue number, or nothing. WHEN it gives none THEN the entry SHALL match, in this order: by a short alias table kept in the app for satellites whose board name and element name share nothing (CAS-3H is LILACSAT-2, as OrbitDeck lists); by its OSCAR designation (such as AO-7 or SO-50, read as `SatelliteOrder.oscarTag` reads it, so AO-07 and AO-7 are the same); and by its name without the mode, ignoring case, spaces, punctuation and leading zeros in the number after a hyphen, compared with each satellite's name, its name without the part in brackets, and each part in brackets.
3. A designation or name that fits more than one satellite SHALL match none of them.
4. An entry that matches no satellite SHALL be ignored, except on the status screen, which lists it without a link to a satellite (Requirement 4.3). With elements that hold no satellite from the board (for example Weather or Resources from Celestrak) nothing SHALL be shown outside the status screen.
5. A satellite with several entries (one per mode, such as the ISS's Crew, FM and SSTV) SHALL keep each mode separate.
6. Matching SHALL be pure and unit tested with captured API responses and a captured copy of the full Amateur (celestrak.org) elements, including the measured case of 24 November 2026: with the 99 amateur satellites of 20 November, 25 of the 32 summary rows matched; the other 7 were satellites those elements do not list.

## Requirement 3: The selected satellite and the heard marks

**User story:** As an operator, I want to see at a glance whether a satellite has been heard lately.

1. On the passes screen, under the pickers, the app SHALL show the selected satellite's reports in the last 24 hours, one line per mode, using AMSAT's display name for the mode (as `ISS [SSTV]` gives `SSTV`): the mode, how many of its reports were Heard, and how long ago it was last heard. A mode with no Heard reports SHALL show its other kinds by name instead (Not heard, Telemetry only, Crew active) with the time of the latest. For example: "AMSAT reports, last 24 h · SSTV: heard 88 times, last 3 h ago · Crew: crew active, 1 h ago". An entry with no mode in brackets SHALL show the kinds without a mode.
2. WHEN the selected satellite is on the board but no entry of it has reports in the last 24 hours THEN the screen SHALL say "No AMSAT reports in the last 24 hours". WHEN the satellite is not on the board, or no catalogue is stored, THEN nothing SHALL be shown. Neither SHALL suggest the satellite is not working.
3. On the All satellites overview and in the chooser the app SHALL show a mark, "Heard 3 h ago" (minutes under an hour, hours under a day), beside satellites with a Heard report in the last 24 hours, and nothing for the others.
4. WHEN the stored 24-hour summary is more than 30 minutes old THEN the passes-screen block SHALL say when it was fetched, in UTC ("as of 20:05 UTC"), and "ago" times SHALL be counted from now. WHEN it is more than 24 hours old THEN no block and no marks SHALL be shown.
5. Tapping the passes-screen block SHALL open the status screen (Requirement 4).
6. Times SHALL be labelled UTC when shown as clock times. Counts SHALL use plurals ("heard once", "heard 12 times").

## Requirement 4: The AMSAT status screen

**User story:** As an operator, I want to see what has been heard across the board, as AMSAT's own status page shows it, without leaving the app.

1. The menu SHALL have "AMSAT status", opening a full screen. WHEN the switch is off THEN the screen SHALL explain what it shows and where the data comes from, and offer to turn the switch on; it SHALL make no request until then.
2. The screen SHALL offer a window of 3, 6, 12, 24, 48 or 72 hours (S3) and a Refresh button.
3. It SHALL list one row per board entry with reports in the window, sorted by number of reports, then by name: AMSAT's display name; "N heard of M reports"; "Heard 3 h ago" when it has a Heard report, otherwise the latest report kind and how long ago; and the number of different 4-character grid squares that reported it ("12 grids", or "40+ grids" per Requirement 1.7). Entries that match a satellite in the current elements SHALL show it; entries that do not SHALL say "Not in your orbital elements".
4. Tapping a row that matches a satellite SHALL select that satellite and return to the passes screen.
5. The screen SHALL show when the data was fetched, and SHALL say so plainly when it could not be updated or when the 5-minute rule holds a refresh back.
6. The screen SHALL link to `https://status.amsat.org/`, where operators can see each report and post their own.
7. The app SHALL use the reporters' callsigns and grid squares only to count distinct 4-character grids per entry. It MAY store those counts. It SHALL NOT store the report bodies, callsigns or grids, show them, send them anywhere, put them in a log or crash trace, or combine them with other data, as AMSAT's guidelines ask.

## Requirement 5: Attribution and privacy

1. The About dialog, the status screen and the wiki Help SHALL credit the reports as AMSAT asks: "Satellite status data: AMSAT, https://status.amsat.org" (CC BY 4.0).
2. For stage 1, the privacy policy and the Play data safety answers SHALL name `status.amsat.org` as a server the app contacts and what it sends (the IP address and the user agent, nothing else), say that the app reads AMSAT's public reports, which include other operators' callsigns and grids, only to count grids and keeps none of them, and SHALL be published before any build with stage 1 reaches a Play track. They SHALL say that turning the switch off deletes the stored reports, and that the app's data is not backed up (`allowBackup="false"`).
3. For stage 2, they SHALL also say that when the operator submits a report, the app sends the callsign, the grid square (4 characters unless the operator chose 6), the satellite, the kind and the observation time to AMSAT, which publishes them on its status board; that nothing is sent without the operator pressing Send; and that the callsign is kept on the device. They SHALL be published before any build with stage 2 reaches a Play track. How Play's data safety form classifies a transfer the user starts and expects SHALL be checked against Play's current guidance when the answers are written.
4. The callsign check follows AMSAT's validator (Apache-2.0, github.com/AMSAT-NA/satellite-status); the app SHALL carry its copyright and licence notice in a NOTICE file and About. No OrbitDeck code is copied: the design follows its behaviour, and the CAS-3H alias is a fact about names, so OrbitDeck is credited as the inspiration in the README and the wiki, not as copied code.

## Requirement 6: Robustness

1. Malformed, empty, oversized or unexpected responses SHALL be handled without a crash, as Requirement 1.5 and 1.6 say.
2. The feature SHALL add no new permission.
3. Fetching, reading the stored copies, parsing and matching SHALL run off the main thread, and parsed copies SHALL be kept in memory so a change of satellites or screens does not read the files again.

## Requirement 7: Accessibility and layout

1. The block, the marks and the status screen SHALL be text from the app's string resources, readable by a screen reader, in every theme including red night.
2. The passes-screen block and the status screen SHALL fit without clipping on a 360 dp phone (the Galaxy A6), wrapping as needed, and the window choice SHALL stay usable at that width.

## Requirement 8: Callsign and grid (stage 2)

**User story:** As an operator, I want my reports to carry my callsign and roughly where I am, and nothing more.

1. Settings SHALL have "Your callsign" (W1): empty by default, upper-cased, at most 14 characters, and accepted only when AMSAT's own check would accept it (the rules of `api_call_sign_is_valid` in AMSAT's `api/v1/lib/validation.php`: parts split on `/` or `-`, at least one part of 4 or more letters and digits with both a letter and a digit, and its length and prefix rules), checked as the user types with the reason shown, as the locator field is. Changing or clearing the callsign SHALL delete the operator's own report history. It SHALL be stored only on the device and SHALL be sent only in a report the operator submits.
2. The grid square sent SHALL be the first 4 characters of the home locator, or all 6 when "Send 6-character grid in reports" (W2) is on. WHEN the home location is not set THEN reporting SHALL NOT be offered, with a line saying why.
3. Neither the callsign nor the grid SHALL appear in any log, crash trace or other request.

## Requirement 9: Posting a report (stage 2)

**User story:** As an operator who has just worked or listened to a pass, I want to tell other operators what I heard, in a few taps.

1. WHEN the status switch is on, a callsign is set and the home location is set THEN the passes-screen block and each matched status-screen row SHALL offer "Report" (W3). Otherwise "Report" SHALL be shown disabled with the reason ("Set your callsign in Settings to report").
2. "Report" SHALL open a form for that satellite with: the board entry, chosen from the satellite's entries in the catalogue (preselected when only one, or the one tapped on the status screen); the kind (W4); and the observation time (W5), in UTC.
3. WHEN the operator presses Send THEN the app SHALL check again that the switch is on and the callsign is set, show a confirmation with everything that will be published (W6), and send only after the operator confirms, checking the switch once more just before sending. No report SHALL ever be sent without that confirmation, in the background or on a schedule.
4. The report SHALL be sent as `POST https://status.amsat.org/api/v1/reports.php` with JSON `name` (the entry's board name), `report`, `callsign`, `grid_square` and `observation_time` (UTC with `Z`), using the same safeguards and User-Agent as Requirement 1.4. One tap of Send SHALL make at most one request.
5. WHEN AMSAT answers 201 THEN the app SHALL say the report was posted; WHEN the answer has `correction: true` THEN it SHALL say it replaced the operator's earlier report for that 15-minute period. The app SHALL then refresh the summary for the next screen view, within the 5-minute rule.
6. WHEN AMSAT answers 422 THEN the app SHALL show the reason in plain words for the known codes (unknown satellite, callsign not accepted, grid square not accepted, and for a time in the future "AMSAT says the time is in the future; check the phone's clock") and the server's message otherwise, keeping the form filled in. WHEN the request fails for another reason THEN the form SHALL stay filled in with a Try again button (W7).
7. The app MAY remember the operator's own last report per entry on the device ("You reported Heard at 20:22 UTC") and SHALL NOT keep any other operator's data.

## Requirement 10: Testing the posting (stage 2)

1. Posting SHALL be unit tested with a fake transport: the request body for each kind, the 4- and 6-character grids, UTC times, 201 with and without `correction`, each 422 code, a network failure, and one request per Send.
2. No test, check or demonstration SHALL post to the production board. End-to-end checks, reading and posting, SHALL run against AMSAT's local stack (`docker compose up` from github.com/AMSAT-NA/satellite-status, API at http://localhost:8081), reached from the emulator through a base address that only debug builds can change, and only to `http://10.0.2.2:8081` or `http://localhost:8081`. Release builds SHALL contain neither the override nor the cleartext exception, and SHALL always use `https://status.amsat.org/api/v1/`.
3. The first real report from a release build SHALL be made by David, from his own callsign, after a real pass.

