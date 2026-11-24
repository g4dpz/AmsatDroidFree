# Implementation Plan: AMSAT satellite status reports (stage 1, read-only)

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task, and `:app:assembleRelease` for the tasks that add classes (R8 is on in release builds). Every task must leave the app building and runnable.

Branch per task group, one PR at a time; review with the two-model semantic review while Copilot is unavailable; merge on green CI and David's "merge N". Order: after the pass-card layout (S8), unless David says otherwise. S1 is decided (D1: off by default) and D2 sets the scope; S2 to S8 are confirmed before task 2 starts.

No build with the feature goes to a Play track before task 5.1 (privacy policy and data safety) is published.

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
