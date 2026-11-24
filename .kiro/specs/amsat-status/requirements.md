# Requirements: AMSAT satellite status reports

## Introduction

Operators report on AMSAT's Live OSCAR Satellite Status board whether they heard a satellite on a pass: Heard, Not Heard, or Telemetry Only. AmsatDroid shows when a satellite passes but not whether anyone has heard it lately, so an operator can plan a pass on a satellite that is switched off or silent. OrbitDeck and Ham Satting already show these reports (see `docs/feature-matrix.md`); AmsatDroid does not.

This spec adds the board's recent reports to the app, read-only. It does not submit reports.

The source is AMSAT's public status API, `https://status.amsat.org/api/v1/` (version 1.5 when checked on 24 November 2026; no key):

- `summary.php?hours=N` lists, for the last N hours (1 to 720, default 24), one row per satellite, mode and report type, with a count and the latest time. On 24 November 2026 the 24-hour summary had 32 rows and was about 6 KB.
- `catalog.php` lists the board's 97 entries (one per satellite and mode, such as `ISS_[FM]` and `ISS_[SSTV]`). About half carry the satellite's NORAD number (`norad_cat_id`); the rest do not.
- The API's terms ask clients to send a descriptive User-Agent and to poll no more often than every 5 minutes. Report data is published under CC BY 4.0, which requires attribution.

## Decisions made

None yet. The assumptions below are proposals for David to confirm or change.

## Assumptions (please confirm or change)

- S1. **On by default, with a switch.** A Settings row, "Show AMSAT status reports", on by default. Like the keps download it fetches public data and sends nothing about the user beyond the IP address and the app's user agent, but it is a new server, so the user can turn it off. (Alternative: off until switched on.)
- S2. **Where it shows.** On the passes screen, one line under the Satellite picker for the selected satellite; on the All satellites overview, a short tag on each satellite's rows; in the chooser, a short tag on each row. Not on the pass cards, the sky screen, the map, the widget or in alerts.
- S3. **The last 24 hours.** One window, 24 hours, everywhere. No setting.
- S4. **Fresh enough, never too often.** The summary is fetched when a screen that shows it opens and the stored copy is older than 15 minutes, and never more often than every 5 minutes (the API's limit). The catalogue changes rarely and is fetched at most once a day.
- S5. **Words, not verdicts.** The app reports what operators said ("Heard 12 times, last 20:22 UTC"), never that a satellite is working or dead: few reports does not mean a satellite is off, and popular satellites get far more reports than quiet ones.
- S6. **Only AMSAT's board.** SatNOGS DB (which says whether each transmitter is active) is a possible later addition, as its own spec.
- S7. **Not in the Contact planner, for now.** Ranking the planner's My satellites search by recent reports is a possible later step.
- S8. **Order.** This is built after the pass-card layout, unless David says otherwise.

## Requirement 1: Getting the reports

**User story:** As an operator, I want the app to know what other operators have reported about each satellite recently.

1. WHEN the switch (S1) is on and a screen that shows reports opens THEN the app SHALL fetch the 24-hour summary if its stored copy is older than 15 minutes, and SHALL NOT fetch it more often than every 5 minutes, whatever screens open.
2. The app SHALL fetch the catalogue at most once a day, and before the first summary is shown if no catalogue is stored.
3. Requests SHALL use https only, no redirects, a size limit, timeouts and the app's existing User-Agent (`AmsatDroid/<version> (Android <release>; amateur radio satellite tracker)`), as the keps and transponder downloads do.
4. WHEN a fetch fails THEN the app SHALL keep showing the stored reports with their time (Requirement 3.4), or nothing if there are none, and SHALL NOT show an error dialog or block any screen. The next attempt follows the timings in 1.1.
5. WHEN the switch is off THEN the app SHALL make no request to the status API and SHALL show no reports.
6. The app SHALL NOT submit reports, and SHALL send nothing to the API except the request itself.

## Requirement 2: Matching reports to satellites

**User story:** As an operator, I want each report attached to the right satellite in my list.

1. A board entry SHALL be matched to an app satellite by its NORAD number when the catalogue gives one; otherwise by its OSCAR designation (such as AO-7 or SO-50, read the same way as `SatelliteOrder.oscarTag`); otherwise by its name with the mode removed, ignoring case, spaces and punctuation, compared with the satellite's name and the parts of it in brackets.
2. An entry that matches no satellite in the current elements SHALL be ignored.
3. A satellite with several entries (one per mode, such as ISS FM and ISS SSTV) SHALL keep each mode separate.
4. Matching SHALL be pure and unit tested with captured API responses, including the measured case of 24 November 2026 (25 of the 32 summary rows matched satellites in the Amateur (celestrak.org) elements; the other 7 were satellites those elements do not list).

## Requirement 3: Showing the reports

**User story:** As an operator, I want to see at a glance whether a satellite has been heard lately.

1. On the passes screen, under the Satellite picker, the app SHALL show one line for the selected satellite, per mode: the mode, how many Heard reports, and the time of the latest one, for example "AMSAT reports, last 24 h: FM heard 12 times, last 20:22 UTC". Not Heard and Telemetry Only reports SHALL be shown by name when there are no Heard reports for that mode.
2. WHEN a matched satellite has no reports in the window THEN the line SHALL say "No AMSAT reports in the last 24 hours", and SHALL NOT suggest the satellite is not working.
3. On the All satellites overview and in the chooser the app SHALL show a short tag for satellites with Heard reports (for example "Heard in 24 h"), and nothing for the others.
4. The reports SHALL show when they were fetched when that is more than 30 minutes ago ("as of 20:05").
5. The passes-screen line SHALL open AMSAT's status board, `https://status.amsat.org/`, in the browser when tapped, so the operator can see the reports themselves and add their own.
6. Times SHALL be shown in UTC, as AMSAT reports them, and labelled UTC.

## Requirement 4: Attribution and privacy

1. The About dialog and the wiki Help SHALL credit the reports: "Satellite status reports: AMSAT Live OSCAR Satellite Status (status.amsat.org), CC BY 4.0".
2. The privacy policy and the Play data safety answers SHALL be updated before release to name `status.amsat.org` as a server the app contacts and what it sends (the IP address and the user agent, nothing else).

## Requirement 5: Robustness

1. Malformed, empty, oversized or unexpected responses SHALL be ignored without a crash, keeping the stored copy.
2. The feature SHALL add no new permission.
3. Fetching SHALL happen off the main thread and SHALL NOT slow the passes screen or the overview.

## Requirement 6: Accessibility and layout

1. The line and the tags SHALL be text, readable by a screen reader, in every theme including red night.
2. The passes-screen line SHALL fit without clipping on a 360 dp phone (the Galaxy A6), wrapping onto a second line if needed.
