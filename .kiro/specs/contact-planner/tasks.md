# Implementation Plan: Contact Planner

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task. The golden regression test must stay green. Every task must leave the app building and runnable.

Order: the pure calculation first (it can be checked against the RS-44 example before any screen exists), then storage, then the view model, then the screens, then quality, documentation and release.

Branch per task group, one PR at a time, wait for CI and Copilot before merging (merge commit).

Status 14 November 2026: spec written; P1 to P8 and O1 to O5 decided and confirmed by David the same day. No open questions remain.

- [ ] 1. Calculation
  - [ ] 1.1 Add `PlannerStation`, `LookAngle`, `MutualMoment`, `MutualWindow`, `PlannerItem` and `ContactPlanner.windows` in `domain/ContactPlanner.kt`, with `ContactPlannerTest`
    - The pass-intersection search of the design, 5-second steps, the best-moment rule with the path tie-break
    - Recover a pass already in progress beyond the quarter-orbit wind-back; clip candidates to both ends of the search; `checkpoint` calls, including in the in-progress recovery; rethrow `CancellationException`
    - Tests: the RS-44 IO82xk / EM29mm case; a pass begun more than a quarter orbit before the start; a pass running past the search end; brute-force 1-second comparison for ISS, AO-7 and RS-44 (edges within 5 s, no 60-second window missed); a 10-second window inside a candidate; minimum elevation splitting a window; window open at the start; too far apart; same station twice; stationary satellite; tie rule; look angles equal `getPosition`
    - Put the fixed elements (RS-44 as in the design, ISS, AO-7) in `app/src/test/resources`
    - _Requirements: 3.4, 4.3, 4.4, 4.5, 4.8, 7.1, 7.2, 10.1, 10.2, 10.4_
  - [ ] 1.2 Add `ContactPlanner.timeline` and `greatCircleKm`, with tests
    - _Requirements: 2.8, 6.3_
  - [ ] 1.3 Add `ContactPlannerCalculator` (all satellites, one coroutine each), with `ContactPlannerCalculatorTest` (parallel equals sequential; cancelling work already started)
    - _Requirements: 4.1, 4.6, 9.3_

- [ ] 2. Saved stations and settings
  - [ ] 2.1 Add `SavedStation` and `SavedStationsJson`, with `SavedStationsJsonTest`
    - _Requirements: 2.1, 2.4_
  - [ ] 2.2 Add the five planner settings and the choice lists to `SettingsRepository`, with tests of the defaults
    - _Requirements: 1.3, 2.6, 3.1, 3.3_

- [ ] 3. View model
  - [ ] 3.1 Add `ContactPlannerViewModel` and `PlannerUiState`, with Robolectric tests
    - Search key and restart; no search while the location is unset; progress; stale warning; one-minute tick dropping ended windows; detail worked out on demand
    - Search on opening when a station is chosen; `refresh()` for Calculate again; the search key includes the selected satellite and the keps generation; the job starts and stops with the planner entry's STARTED state
    - Tests as in the design: restart on each key part, lifecycle cancel and restart, old results never shown as current
    - _Requirements: 1.2, 1.3, 1.5, 4.2, 4.6, 4.7, 4.9, 5.3, 5.5_

- [ ] 4. Screens
  - [ ] 4.1 Add `Routes.PLANNER`, the "Contact planner" menu item below All satellites, and the planner screen with the stations, search and results sections, and the Calculate again button
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 2.7, 2.8, 3.1, 4.1, 4.2, 5.1, 5.2, 5.4_
  - [ ] 4.2 Add the station editor (`Routes.PLANNER_STATION`; name, locator, latitude and longitude, no elevation field): add, edit, delete with confirmation, `LocatorSync` rules and 4-character squares (O5), the 10-station limit
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_
  - [ ] 4.3 Add the window detail: both stations' look angles and total path, the mode picker and each station's Doppler, the timeline
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_
  - [ ] 4.4 Strings for all of the above
    - _Requirements: D4_

- [ ] 5. Quality
  - [ ] 5.1 Layouts: phone portrait and landscape, tablet portrait and landscape, split-screen; red night mode; 200 percent font; TalkBack headings, labels and reading order
    - _Requirements: 6.6, 8.1, 8.2, 8.3, 8.4_
  - [ ] 5.2 Speed on the SM-T225: one satellite over 48 hours; all Amateur satellites over 24 hours (first results, finish); the longest single `getPasses` call (how long cancellation can lag). Record the numbers here
    - _Requirements: 9.1, 9.2_
  - [ ] 5.3 Compare a few windows with an independent tool and record the result
    - _Requirements: 10.1_

- [ ] 6. Documentation
  - [ ] 6.1 Wiki Help (a Contact planner section; update "written for version"), wiki Home and README feature lists; check the privacy policy still holds
    - _Requirements: 11.1, 11.2, 11.4_
  - [ ] 6.2 Run `manual-test-checklist.md` and record the results

- [ ] 7. Release
  - [ ] 7.1 CHANGELOG entry, bump `versionName` and `versionCode`, build the signed bundle from the merged commit, check the built version, upload to the test track
    - _Requirements: 11.3_
