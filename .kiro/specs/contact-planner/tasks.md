# Implementation Plan: Contact Planner

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task. The golden regression test must stay green. Every task must leave the app building and runnable.

Order: the pure calculation first (it can be checked against the RS-44 example before any screen exists), then storage, then the view model, then the screens, then quality, documentation and release.

Branch per task group, one PR at a time, wait for CI and Copilot before merging (merge commit).

Status 14 November 2026: spec written; P1 to P8 and O1 to O5 decided and confirmed by David the same day. No open questions remain.

- [x] 1. Calculation
  - [x] 1.1 Add `PlannerStation`, `LookAngle`, `MutualMoment`, `MutualWindow`, `PlannerItem` and `ContactPlanner.windows` in `domain/ContactPlanner.kt`, with `ContactPlannerTest`
    - The pass-intersection search of the design, 5-second steps, the best-moment rule with the path tie-break
    - Recover a pass already in progress beyond the quarter-orbit wind-back; clip candidates to both ends of the search; `checkpoint` calls, including in the in-progress recovery; rethrow `CancellationException`
    - Tests: the RS-44 IO82xk / EM29mm case; a pass begun more than a quarter orbit before the start; a pass running past the search end; brute-force 1-second comparison for ISS, AO-7 and RS-44 (edges within 5 s, no 60-second window missed); a 10-second window inside a candidate; minimum elevation splitting a window; window open at the start; too far apart; same station twice; stationary satellite; tie rule; look angles equal `getPosition`
    - Put the fixed elements (RS-44 as in the design, ISS, AO-7) in `app/src/test/resources`
    - _Requirements: 3.4, 4.3, 4.4, 4.5, 4.8, 7.1, 7.2, 10.1, 10.2, 10.4_
    - Done 14 November 2026: `domain/ContactPlanner.kt`, `ContactPlannerTest` (22 tests with 1.2). The one-second comparison covers ISS, AO-7 and RS-44 (UK to Paris, Kansas City and Moscow); the slow-orbit test uses a Molniya-type orbit and fails with the recovery switched off. The first window of that orbit is asserted; its next pass is missed by the predictor, the second limit in the design
  - [x] 1.2 Add `ContactPlanner.timeline` and `greatCircleKm`, with tests
    - _Requirements: 2.8, 6.3_
  - [x] 1.3 Add `ContactPlannerCalculator` (all satellites, one coroutine each), with `ContactPlannerCalculatorTest` (parallel equals sequential; cancelling work already started)
    - _Requirements: 4.1, 4.6, 9.3_

- [x] 2. Saved stations and settings
  - [x] 2.1 Add `SavedStation` and `SavedStationsJson`, with `SavedStationsJsonTest`
    - _Requirements: 2.1, 2.4_
    - Done 14 November 2026: `data/SavedStation.kt`, `SavedStationsJsonTest` (13 tests)
  - [x] 2.2 Add the five planner settings and the choice lists to `SettingsRepository`, with tests of the defaults
    - _Requirements: 1.3, 2.6, 3.1, 3.3_
    - Done 14 November 2026: in `SettingsRepository`, with the defaults, persistence, bad-stored-value and rejected-value cases added to `SettingsRepositoryTest`

- [x] 3. View model
  - [x] 3.1 Add `ContactPlannerViewModel` and `PlannerUiState`, with Robolectric tests
    - Search key and restart; no search while the location is unset; progress; stale warning; one-minute tick dropping ended windows; detail worked out on demand
    - Search on opening when a station is chosen; `refresh()` for Calculate again; the search key includes the selected satellite and the keps generation; the job starts and stops with the planner entry's STARTED state
    - Tests as in the design: restart on each key part, lifecycle cancel and restart, old results never shown as current
    - _Requirements: 1.2, 1.3, 1.5, 4.2, 4.6, 4.7, 4.9, 5.3, 5.5_
    - Done 14 November 2026: `ui/ContactPlannerViewModel.kt` and `ContactPlannerViewModelTest` (22 tests, plain JVM with the unconfined test dispatcher, as the overview's). The screen calls `onStart()` and `onStop()` from its STARTED lifecycle (task 4.1); the stale warning is data (`StaleElements`), worded by the screen

- [x] 4. Screens
  - [x] 4.1 Add `Routes.PLANNER`, the "Contact planner" menu item below All satellites, and the planner screen with the stations, search and results sections, and the Calculate again button
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 2.7, 2.8, 3.1, 4.1, 4.2, 5.1, 5.2, 5.4_
    - Done 14 November 2026: `ui/ContactPlannerScreen.kt`, the menu item and `Routes.PLANNER`. Checked on the A16 emulator and the SM-T225: 42 behaviour checks and 35 layout checks each (portrait and landscape, 100 and 200 percent font, red night)
  - [x] 4.2 Add the station editor (`Routes.PLANNER_STATION`; name, locator, latitude and longitude, no elevation field): add, edit, delete with confirmation, `LocatorSync` rules and 4-character squares (O5), the 10-station limit; the name is limited with `SavedStation.limitName` (a character is a code point, so an emoji is never split)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_
    - Done 14 November 2026: `ui/PlannerStationScreen.kt` and the pure rules in `domain/StationEditing.kt` (`StationEditingTest`, 9 tests); a station just added becomes the chosen one
  - [x] 4.3 Add the window detail: both stations' look angles and total path, the timeline (no Doppler, D5)
    - _Requirements: 6.1, 6.2, 6.3, 6.6_
    - Done 14 November 2026: `ui/ContactPlannerDetail.kt`; tapping a window's summary shows or hides it in place. The first version also showed each station's Doppler-corrected frequencies and a mode picker; David took them out the same day (D5). Checked on the A16 emulator and the SM-T225, in every layout
  - [x] 4.4 Strings for all of the above
    - Done: the strings came in with 4.1, 4.2 and 4.3, with plurals where a count is followed by a word (lint is clean)
    - _Requirements: D4_

- [x] 5. Quality
  - [x] 5.1 Layouts: phone portrait and landscape, tablet portrait and landscape, split-screen; red night mode; 200 percent font; TalkBack headings, labels and reading order
    - Done 15 Nov 2026: layout script 45/45 on the emulator and 45/45 on the SM-T225 (portrait and landscape, 100 and 200 percent font). Not run: TalkBack listening and split-screen, which adb cannot drive; reading order, labels and the button role on the card summary were checked by the accessibility tree only
    - _Requirements: 6.6, 8.1, 8.2, 8.3, 8.4_
  - [x] 5.2 Speed on the SM-T225: one satellite over 48 hours; all Amateur satellites over 24 hours (first results, finish); the longest single `getPasses` call (how long cancellation can lag). Record the numbers here
    - Done 15 Nov 2026, SM-T225, from the app's log line: one satellite over 48 h 1642 ms; 99 satellites over 24 h 5690 and 5611 ms; 99 satellites over 48 h 10041 ms (about 100 ms per satellite). The longest single `getPasses` call was not instrumented; it is bounded by the 48 h case for one satellite, 1642 ms at most, so cancellation lags by no more than that
    - _Requirements: 9.1, 9.2_
  - [x] 5.3 Compare a few windows with an independent tool and record the result
    - Done 15 Nov 2026: the independent tool was the reference `sgp4` Python package (2.27, Vallado), with its own sidereal-time and look-angle code, stepping every second over the same fixed RS-44 elements, UK (IO82xk) to Kansas City (EM29mm), 72 h from 2026-11-14 14:55:24Z, minimum 0 degrees. It found three windows, 18:01:24 to 18:02:33, 16:25:31 to 16:30:19 and 16:51:36 to 16:55:27, with best moments 18:02:13, 16:27:33 and 16:53:56 and lower elevations 1.0, 3.7 and 3.5 degrees. The planner's expected values agree within 5 s and 0.1 degree. Not compared with Gpredict or N2YO
    - _Requirements: 10.1_

- [x] 6. Documentation
  - [x] 6.1 Wiki Help (a Contact planner section; update "written for version"), wiki Home and README feature lists; check the privacy policy still holds
    - 15 Nov 2026: the README line was already in. The Contact planner section is published on the wiki Help page (after "All satellites") and the Home feature list (wiki commit 5881e7f). "Written for version" on the Help page now says 2.1.0 (wiki commit 73f8f38). Privacy policy: the planner's saved stations (name and locator, up to 10) stay on the device, which the policy's "operational settings remain on your device" already covers, so no change proposed.
    - _Requirements: 11.1, 11.2, 11.4_
  - [x] 6.2 Run `manual-test-checklist.md` and record the results
    - Done 15 Nov 2026: David confirmed every box in the checklist and asked for them to be ticked (29 were ticked at his request, not individually run by the agent). The device scripts had already covered layouts (45/45 on both devices); TalkBack and split-screen were not run by script.

- [x] 7. Release
  - [x] 7.1 CHANGELOG entry, bump `versionName` and `versionCode`, build the signed bundle from the merged commit, check the built version, upload to the test track
    - 15 to 19 Nov 2026: PR #56 merged the 2.1.0 CHANGELOG entry and bump with versionCode 29 (master `217a50d`); PR #57 then set the code to 2100. 2.1.0 was never uploaded on its own: the planner shipped in 2.2.0 (2102) and 2.2.1 (2103, built from master `ab5c68a`), which David published on 19 Nov 2026.
    - _Requirements: 11.3_
