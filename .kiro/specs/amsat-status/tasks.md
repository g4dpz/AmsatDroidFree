# Implementation Plan: AMSAT satellite status reports

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task, and `:app:assembleRelease` for the tasks that add classes (R8 is on in release builds). Every task must leave the app building and runnable.

Branch per task group, one PR at a time; review with the two-model semantic review while Copilot is unavailable; merge on green CI and David's "merge N". Order: after the pass-card layout (S8), unless David says otherwise. S1 is decided (D1: off by default) and D2 sets the scope (read and write); S2 to S8 are confirmed before task 2 starts, and W1 to W7 before task 6 starts.

Stage 1 (tasks 1 to 5) is released before stage 2 (tasks 6 to 8) is started.

No build with stage 1 goes to a Play track before task 5.1 is published, and none with stage 2 before task 8.1.

- [ ] 1. Parsing and matching
  - [ ] 1.1 Capture the summary, the catalogue, a reports page (callsigns replaced) and the 99-satellite Amateur elements into `app/src/test/resources/`; `AmsatStatusParsing` with `ParseResult` (four report kinds, mode from the display name, no-mode entries, grid counts with "more", `Ok(empty)` against `Unreadable`) and tests
    - _Requirements: 1.6, 1.7, 2.6, 4.7, 6.1_
  - [ ] 1.2 `AmsatStatusMatcher` (join by board name, NORAD number used alone, alias table, OSCAR designation, then name with a single candidate only, unmatched entries kept for the board, no catalogue), `StatusView`, `lastHeard`, `BoardRows` and `AgoText`, with tests including the 24 November 2026 counts, CAS-3H matching LILACSAT-2 and a two-candidate name
    - _Requirements: 2, 3.2, 4.3_
- [ ] 2. Fetching
  - [ ] 2.1 `AmsatStatusRepository` and its factory: addresses per window, single-flight lock, per-address 5-minute rule counting every attempt, 15-minute copies, daily catalogue, 24-hour stop after a permanent refusal, `Ok(empty)` stored and `Unreadable` not, `refreshNow` with the next allowed time, the User-Agent with the project URL; tests with a fake downloader and clock, including two concurrent calls making one request
    - _Requirements: 1.2 to 1.7, 1.9_
  - [ ] 2.2 `showAmsatStatus` (off by default) and `amsatStatusHours` settings and the Settings row; `HamSatDroidApp.amsatStatus` and `requestAmsatStatus()`; tests that off makes no request
    - _Requirements: 1.8, 6.3_
- [ ] 3. The selected satellite and the marks
  - [ ] 3.1 `AppViewModel.statusBySatellite` (setting, age and satellites as inputs) and the request on screen start and on switching the setting on, with tests; `RequestAmsatStatusEffect`
    - _Requirements: 1.1, 1.8, 3.4_
  - [ ] 3.2 The passes-screen block (strings with plurals, `AgoText`, "as of", tap opens the status screen)
    - _Requirements: 3.1, 3.2, 3.4 to 3.6, 7_
  - [ ] 3.3 The "Heard 3 h ago" mark: `OverviewScreen` gets the status from `AppNavHost`, `UpNowCard`, `UpcomingCard` and `ChooseRow` take `lastHeard`
    - _Requirements: 3.3_
- [ ] 4. The status screen
  - [ ] 4.1 `AmsatStatusScreen`, `Routes.AMSAT_STATUS` and the menu item: off-state explanation with Turn on, window picker, Refresh with the 5-minute message, rows, select-and-return, link and credit
    - _Requirements: 4, 5.1_
  - [ ] 4.2 The About credit
    - _Requirements: 5.1_
- [ ] 5. Privacy, quality and release
  - [ ] 5.1 Privacy policy (wiki) and Play data safety answers name status.amsat.org; published before any Play upload with the feature
    - _Requirements: 5.2_
  - [ ] 5.2 Layouts on the A6 and the emulator, portrait, landscape and red night; the release build (R8) on a device; airplane mode; the switch off with no request (network log); Weather elements show nothing outside the status screen
    - _Requirements: 1, 2.4, 6, 7_
  - [ ] 5.3 Manual checklist, including TalkBack on the A6 by David
  - [ ] 5.4 Wiki Help, README feature line, CHANGELOG, version bump
    - _Requirements: 5.1_

## Stage 2: posting reports

- [ ] 6. Building a report
  - [ ] 6.1 `CallsignCheck`, `ReportSubmission.from` (kinds, 4- and 6-character grids, UTC with `Z`), `ReportTime` (default from the last ended pass, allowed range), `ReportKinds.offered` (Crew active only for Crew entries) and `AmsatPostParsing` (201 with and without `correction`, each 422 code, other answers), with tests
    - _Requirements: 8.1, 8.2, 9.2, 9.4 to 9.6_
  - [ ] 6.2 `HttpsJsonPoster`, `AmsatStatusRepository.submit` (one request per call, summary marked due after a post, own last report stored, nothing logged), the debug-only base address and network security config; tests with a fake poster
    - _Requirements: 8.3, 9.4, 9.5, 9.7, 10.1, 10.2_
- [ ] 7. Screens
  - [ ] 7.1 Settings: "AMSAT status reports" section with the callsign field and the 6-character choice
    - _Requirements: 8.1, 8.2_
  - [ ] 7.2 The Report button (enabled or disabled with the reason) on the passes-screen block and the status-screen rows; `AmsatReportScreen`, `Routes.AMSAT_REPORT`, the confirmation dialog, the result messages, the own last report line
    - _Requirements: 9.1 to 9.7_
- [ ] 8. Privacy, quality and release
  - [ ] 8.1 Privacy policy (wiki) and Play data safety answers for stage 2 (callsign, grid, report sent to AMSAT on the user's action); published before any Play upload with stage 2
    - _Requirements: 5.3_
  - [ ] 8.2 End-to-end against AMSAT's local stack (`docker compose up` from github.com/AMSAT-NA/satellite-status) from the emulator with the debug base address: each kind, a correction within 15 minutes, each 422 case, the network off. Never against status.amsat.org
    - _Requirements: 10.2_
  - [ ] 8.3 Layouts of the form and confirmation on the A6 and the emulator, portrait, landscape and red night; the release build (R8) on a device, up to the confirmation and Cancel
    - _Requirements: 7_
  - [ ] 8.4 Manual checklist (stage 2), and David's first real report from his callsign after a real pass
    - _Requirements: 10.3_
  - [ ] 8.5 Wiki Help (reporting), README, CHANGELOG, version bump

