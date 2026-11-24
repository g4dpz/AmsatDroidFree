# Requirements: AMSAT satellite status reports

## Introduction

Operators report on AMSAT's Live OSCAR Satellite Status board whether they heard a satellite on a pass. AmsatDroid shows when a satellite passes but not whether anyone has heard it lately, so an operator can plan a pass on a satellite that is switched off or silent. OrbitDeck and Ham Satting already show these reports (see `docs/feature-matrix.md`); AmsatDroid does not.

This spec adds the board's recent reports to the app. It follows the approach of Paul Stoetzer's (N8HM) OrbitDeck for iOS (github.com/prstoetzer/OrbitDeckiOS, MIT licence), rewritten for AmsatDroid: a status board screen, "heard" marks beside satellites, and a line for the selected satellite. It is **read-only (stage 1)**. Posting your own reports from the app is stage 2, a separate spec (see "Later" below).

The source is AMSAT's public status API, `https://status.amsat.org/api/v1/` (version 1.5 when checked on 24 November 2026; no key):

- `summary.php?hours=N` lists, for the last N hours (1 to 720, default 24), one row per board entry and report kind, with a count and the latest time. The kinds are Heard, Not Heard, Telemetry Only and Crew Active. On 24 November 2026 the 24-hour summary had 32 rows (about 6 KB) covering 23 entries.
- `catalog.php` lists every entry on the board: 97 on 24 November 2026, one per satellite and mode (such as `ISS_[FM]`, `ISS_[SSTV]` and `ISS_[Crew]`), with a display name (`ISS [SSTV]`) and, for about half of them, the satellite's NORAD number.
- `reports.php?hours=N&limit=500` lists the individual reports, newest first, each with the reporter's callsign and grid square. The 24-hour page on 24 November 2026 held 170 reports (about 80 KB); the 72-hour window had more than 500, so a second page existed.
- The API's usage guidelines (`https://status.amsat.org/api/v1/overview.php`) ask clients to send a User-Agent naming the application, its version and a way to reach the developer, to poll no more often than every 5 minutes, and to fetch in one request rather than one per satellite; clients that ignore them may be rate-limited or blocked. Report data is published under CC BY 4.0, credited as "Satellite status data: AMSAT, https://status.amsat.org".

## Decisions made

- D1 (S1). **Off by default, with a switch** (David, 24 Nov 2026). A Settings row, "Show AMSAT status reports", off until the user turns it on. Until then the app makes no request to the status API and the status screen offers to turn it on. No build with the feature reaches a Play track before the privacy policy and data safety answers name the new server (Requirement 5.2).
- D2. **Follow OrbitDeck's functionality, read side first** (David, 24 Nov 2026: "can we copy the functionality?"). Stage 1 is the board screen, the heard marks and the selected-satellite line; reporting is stage 2. David will tell Paul.

S2 to S8 below must be confirmed or changed by David before task 2 starts.

## Assumptions (please confirm or change)

- S2. **Where it shows.** A new "AMSAT status" screen from the menu (Requirement 4); on the passes screen, a short block under the pickers for the selected satellite; on the All satellites overview and in the chooser, a short "heard" mark beside a satellite. Not on the pass cards, the sky screen, the map, the widget or in alerts.
- S3. **Windows.** The status screen offers 3, 6, 12, 24, 48 and 72 hours, as OrbitDeck does, remembered between visits, default 24. The passes-screen block and the marks always use the last 24 hours.
- S4. **Fresh enough, never too often.** Data is fetched when a screen that shows it comes into view and the stored copy is older than 15 minutes, never more often than every 5 minutes for the same request. The catalogue is fetched at most once a day.
- S5. **Words, not verdicts.** The app reports what operators said ("Heard 3 h ago", "12 heard of 14 reports"), never that a satellite is working or dead.
- S6. **Only AMSAT's board.** SatNOGS DB (whether each transmitter is active) is a possible later addition, as its own spec.
- S7. **Not in the Contact planner, for now.** Ranking the planner's My satellites search by recent reports is a possible later step.
- S8. **Order.** This is built after the pass-card layout, unless David says otherwise.

## Later (not in this spec)

- Stage 2, reporting: post Heard, Telemetry Only, Not Heard or Crew Active from the app, as OrbitDeck does. It needs a callsign setting, a confirmation that the report is public under that callsign, AMSAT's rule that only real observations by that operator are posted, and a privacy policy change (the app would then send the callsign and grid). Until then, the screen's link to status.amsat.org lets operators post there.

## Requirement 1: Getting the reports

**User story:** As an operator, I want the app to know what other operators have reported about each satellite recently.

1. WHEN the switch (D1) is on and the status screen, the passes screen, the All satellites overview or the chooser comes into view, or the switch is turned on while one of them is in view, THEN the app SHALL fetch what that screen needs if the stored copy is older than 15 minutes: the 24-hour summary for all of them, and for the status screen also the summary and the reports for its chosen window.
2. The app SHALL make no more than one request to each address (endpoint and window) in any 5 minutes, counting every attempt (successful or not), however many screens ask at once. A Refresh on the status screen SHALL obey the same rule and say when the next refresh is possible.
3. The app SHALL fetch the catalogue when none is stored or the stored one is more than 24 hours old. WHEN the catalogue cannot be fetched and none is stored THEN the app SHALL still show the reports, matched by the rules in Requirement 2 that do not need the catalogue, and SHALL NOT show the "no reports" line (Requirement 3.2), since it cannot tell which satellites are on the board.
4. Requests SHALL use https only, no redirects, a size limit and timeouts, as the keps and transponder downloads do, and a User-Agent that names the app, its version and the project URL: `AmsatDroid/<version> (+https://github.com/g4dpz/AmsatDroidFree; Android <release>; amateur radio satellite tracker)`.
5. WHEN a request fails or the response cannot be read THEN the app SHALL keep the stored copy and SHALL NOT show an error dialog or block any screen; the status screen SHALL say the reports could not be updated. WHEN the server refuses permanently (a redirect, 403, 404 or 429, as `KepsDownloadPolicy.classify` already decides) THEN the app SHALL NOT ask that address again for 24 hours.
6. A response that is valid and lists no reports SHALL replace the stored copy (a quiet day is real data); a response that cannot be read SHALL NOT.
7. The reports SHALL be fetched in one request per window (`limit=500`). WHEN more reports exist than one page holds THEN the app SHALL use the first page only and say the grid count is at least the number shown ("40+ grids").
8. WHEN the switch is off THEN the app SHALL make no request to the status API and SHALL show no reports, on every screen, at once.
9. The app SHALL NOT submit reports, and SHALL send nothing to the API except the requests themselves.

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
7. The app SHALL use the reporters' callsigns and grid squares only to count distinct grids in memory, and SHALL NOT store, show or send them.

## Requirement 5: Attribution and privacy

1. The About dialog, the status screen and the wiki Help SHALL credit the reports as AMSAT asks: "Satellite status data: AMSAT, https://status.amsat.org" (CC BY 4.0).
2. The privacy policy and the Play data safety answers SHALL name `status.amsat.org` as a server the app contacts and what it sends (the IP address and the user agent, nothing else), and SHALL be published before any build with the feature reaches a Play track.

## Requirement 6: Robustness

1. Malformed, empty, oversized or unexpected responses SHALL be handled without a crash, as Requirement 1.5 and 1.6 say.
2. The feature SHALL add no new permission.
3. Fetching, reading the stored copies, parsing and matching SHALL run off the main thread, and parsed copies SHALL be kept in memory so a change of satellites or screens does not read the files again.

## Requirement 7: Accessibility and layout

1. The block, the marks and the status screen SHALL be text from the app's string resources, readable by a screen reader, in every theme including red night.
2. The passes-screen block and the status screen SHALL fit without clipping on a 360 dp phone (the Galaxy A6), wrapping as needed, and the window choice SHALL stay usable at that width.
