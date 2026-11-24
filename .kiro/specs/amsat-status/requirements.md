# Requirements: AMSAT satellite status reports

## Introduction

Operators report on AMSAT's Live OSCAR Satellite Status board whether they heard a satellite on a pass. AmsatDroid shows when a satellite passes but not whether anyone has heard it lately, so an operator can plan a pass on a satellite that is switched off or silent. OrbitDeck and Ham Satting already show these reports (see `docs/feature-matrix.md`); AmsatDroid does not.

This spec adds the board's recent reports to the app, read-only. It does not submit reports.

The source is AMSAT's public status API, `https://status.amsat.org/api/v1/` (version 1.5 when checked on 24 November 2026; no key):

- `summary.php?hours=N` lists, for the last N hours (1 to 720, default 24), one row per board entry and report kind, with a count and the latest time. The kinds are Heard, Not Heard, Telemetry Only and Crew Active. On 24 November 2026 the 24-hour summary had 32 rows (about 6 KB) covering 23 entries.
- `catalog.php` lists every entry on the board: 97 on 24 November 2026, one per satellite and mode (such as `ISS_[FM]`, `ISS_[SSTV]` and `ISS_[Crew]`), with a display name (`ISS [SSTV]`) and, for about half of them, the satellite's NORAD number.
- The API's usage guidelines (`https://status.amsat.org/api/v1/overview.php`) ask clients to send a User-Agent naming the application, its version and a way to reach the developer, and to poll no more often than every 5 minutes; clients that ignore them may be rate-limited or blocked. Report data is published under CC BY 4.0, credited as "Satellite status data: AMSAT, https://status.amsat.org".

## Decisions made

- D1 (S1). **Off by default, with a switch** (David, 24 Nov 2026). A Settings row, "Show AMSAT status reports", off until the user turns it on. Until then the app makes no request to the status API. No build with the feature reaches a Play track before the privacy policy and data safety answers name the new server (Requirement 4.2).

S2 to S8 below must be confirmed or changed by David before task 2 starts.

## Assumptions (please confirm or change)


- S2. **Where it shows.** On the passes screen, a short block under the pickers for the selected satellite; on the All satellites overview and in the chooser, a short tag on each satellite's row. Not on the pass cards, the sky screen, the map, the widget or in alerts.
- S3. **The last 24 hours.** One window, 24 hours, everywhere. No setting.
- S4. **Fresh enough, never too often.** The summary is fetched when a screen that shows reports comes into view and the stored copy is older than 15 minutes, and never more often than every 5 minutes (the API's guideline). The catalogue changes rarely and is fetched at most once a day.
- S5. **Words, not verdicts.** The app reports what operators said ("Heard 12 times, last 20:22 UTC"), never that a satellite is working or dead: few reports does not mean a satellite is off, and popular satellites get far more reports than quiet ones.
- S6. **Only AMSAT's board.** SatNOGS DB (which says whether each transmitter is active) is a possible later addition, as its own spec.
- S7. **Not in the Contact planner, for now.** Ranking the planner's My satellites search by recent reports is a possible later step.
- S8. **Order.** This is built after the pass-card layout, unless David says otherwise.

## Requirement 1: Getting the reports

**User story:** As an operator, I want the app to know what other operators have reported about each satellite recently.

1. WHEN the switch (D1) is on and the passes screen, the All satellites overview or the chooser comes into view, or the switch is turned on while one of them is in view, THEN the app SHALL fetch the 24-hour summary if its stored copy is older than 15 minutes.
2. The app SHALL make no more than one request to each endpoint in any 5 minutes, counting every attempt (successful or not), however many screens ask at once.
3. The app SHALL fetch the catalogue when none is stored or the stored one is more than 24 hours old. WHEN the catalogue cannot be fetched and none is stored THEN the app SHALL still show the summary's reports, matched by the rules in Requirement 2 that do not need the catalogue, and SHALL NOT show the "no reports" line (Requirement 3.2), since it cannot tell which satellites are on the board.
4. Requests SHALL use https only, no redirects, a size limit and timeouts, as the keps and transponder downloads do, and a User-Agent that names the app, its version and the project URL: `AmsatDroid/<version> (+https://github.com/g4dpz/AmsatDroidFree; Android <release>; amateur radio satellite tracker)`.
5. WHEN a request fails or the response cannot be read THEN the app SHALL keep the stored copy and SHALL NOT show an error dialog or block any screen. WHEN the server refuses permanently (a redirect, 403, 404 or 429, as `KepsDownloadPolicy.classify` already decides) THEN the app SHALL NOT ask that endpoint again for 24 hours.
6. A response that is valid and lists no reports SHALL replace the stored summary (a quiet day is real data); a response that cannot be read SHALL NOT.
7. WHEN the switch is off THEN the app SHALL make no request to the status API and SHALL show no reports, on every screen, at once.
8. The app SHALL NOT submit reports, and SHALL send nothing to the API except the requests themselves.

## Requirement 2: Matching reports to satellites

**User story:** As an operator, I want each report attached to the right satellite in my list.

1. The summary SHALL be joined to the catalogue by the entry's board name (such as `ISS_[SSTV]`). A summary row whose name is not in the stored catalogue SHALL be treated as an entry with no NORAD number.
2. An entry SHALL be matched to a satellite in the current elements as follows. WHEN the catalogue gives the entry a NORAD number THEN only that number SHALL be used: the entry matches the satellite with that catalogue number, or nothing. WHEN it gives none THEN the entry SHALL match by its OSCAR designation (such as AO-7 or SO-50, read as `SatelliteOrder.oscarTag` reads it), and failing that by its name without the mode, ignoring case, spaces and punctuation, compared with each satellite's name, its name without the part in brackets, and each part in brackets.
3. A designation or name that fits more than one satellite SHALL match none of them.
4. An entry that matches no satellite SHALL be ignored. With elements that hold no satellite from the board (for example Weather or Resources from Celestrak) nothing SHALL be shown.
5. A satellite with several entries (one per mode, such as the ISS's Crew, FM and SSTV) SHALL keep each mode separate.
6. Matching SHALL be pure and unit tested with captured API responses and a captured copy of the full Amateur (celestrak.org) elements, including the measured case of 24 November 2026: with the 99 amateur satellites of 20 November, 25 of the 32 summary rows matched; the other 7 were satellites those elements do not list.

## Requirement 3: Showing the reports

**User story:** As an operator, I want to see at a glance whether a satellite has been heard lately.

1. On the passes screen, under the pickers, the app SHALL show the selected satellite's reports, one line per mode, using AMSAT's display name for the mode (as `ISS [SSTV]` gives `SSTV`): the mode, then how many times it was heard and the latest time. A mode with no Heard reports SHALL show its other kinds by name instead (Not heard, Telemetry only, Crew active). For example: "AMSAT reports, last 24 h · SSTV: heard 88 times, last 20:22 UTC · Crew: crew active, last 19:40 UTC". An entry with no mode in brackets SHALL show the kinds without a mode.
2. WHEN the selected satellite is on the board but no entry of it has reports in the window THEN the screen SHALL say "No AMSAT reports in the last 24 hours". WHEN the satellite is not on the board, or no catalogue is stored, THEN nothing SHALL be shown. Neither SHALL suggest the satellite is not working.
3. On the All satellites overview and in the chooser the app SHALL show a short tag, "Heard in 24 h", for satellites with Heard reports, and nothing for the others.
4. WHEN the stored summary is more than 30 minutes old THEN the passes-screen block SHALL say when it was fetched, in UTC ("as of 20:05 UTC"). WHEN it is more than 24 hours old THEN no reports and no tags SHALL be shown anywhere, since they no longer describe the last 24 hours.
5. The passes-screen block SHALL open AMSAT's status board, `https://status.amsat.org/`, in the browser when tapped, so the operator can see the reports themselves and add their own.
6. Times SHALL be shown in UTC, as AMSAT reports them, and labelled UTC. Counts SHALL use plurals ("heard once", "heard 12 times").

## Requirement 4: Attribution and privacy

1. The About dialog and the wiki Help SHALL credit the reports as AMSAT asks: "Satellite status data: AMSAT, https://status.amsat.org" (CC BY 4.0).
2. The privacy policy and the Play data safety answers SHALL name `status.amsat.org` as a server the app contacts and what it sends (the IP address and the user agent, nothing else), and SHALL be published before any build with the feature reaches a Play track.

## Requirement 5: Robustness

1. Malformed, empty, oversized or unexpected responses SHALL be handled without a crash, as Requirement 1.5 and 1.6 say.
2. The feature SHALL add no new permission.
3. Fetching, reading the stored copies, parsing and matching SHALL run off the main thread, and parsed copies SHALL be kept in memory so a change of satellites or screens does not read the files again.

## Requirement 6: Accessibility and layout

1. The block and the tags SHALL be text from the app's string resources, readable by a screen reader, in every theme including red night.
2. The passes-screen block SHALL fit without clipping on a 360 dp phone (the Galaxy A6), wrapping as needed.
