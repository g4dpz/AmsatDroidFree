# Implementation Plan: AMSAT satellite status reports

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task, and `:app:assembleRelease` for the tasks that add classes (R8 is on in release builds). Every task must leave the app building and runnable.

Branch per task group, one PR at a time; review with the two-model semantic review while Copilot is unavailable; merge on green CI and David's "merge N". Order: after the pass-card layout (S8), unless David says otherwise. S1 is decided (D1: off by default); S2 to S8 are confirmed before task 2 starts.

No build with the feature goes to a Play track before task 4.1 (privacy policy and data safety) is published.

- [ ] 1. Parsing and matching
  - [ ] 1.1 Capture the summary, the catalogue and the 99-satellite Amateur elements into `app/src/test/resources/`; `AmsatStatusParsing` with `ParseResult` (four report kinds, mode from the display name, no-mode entries, `Ok(empty)` against `Unreadable`) and tests
    - _Requirements: 1.6, 2.6, 5.1_
  - [ ] 1.2 `AmsatStatusMatcher` (join by board name, NORAD number used alone, OSCAR designation then name with a single candidate only, entries with no reports kept when the catalogue is known, no catalogue) and `StatusView`, with tests including the 24 November 2026 counts and a two-candidate name
    - _Requirements: 2, 3.2_
- [ ] 2. Fetching
  - [ ] 2.1 `AmsatStatusRepository` and its factory: single-flight lock, per-endpoint 5-minute rule counting every attempt, 15-minute summary, daily catalogue, 24-hour stop after a permanent refusal, `Ok(empty)` stored and `Unreadable` not, the User-Agent with the project URL; tests with a fake downloader and clock, including two concurrent calls making one request
    - _Requirements: 1.2 to 1.6, 1.8_
  - [ ] 2.2 `showAmsatStatus` setting (off by default) and its Settings row; `HamSatDroidApp.amsatStatus` and `requestAmsatStatus()`; tests that off makes no request
    - _Requirements: 1.7, 5.3_
- [ ] 3. Showing
  - [ ] 3.1 `AppViewModel.statusBySatellite` (setting, age and satellites as inputs) and the request on screen start and on switching the setting on, with tests; `RequestAmsatStatusEffect` in the three screens
    - _Requirements: 1.1, 1.7, 3.4_
  - [ ] 3.2 The passes-screen block (strings with plurals, `DisplayFormats.utcTime`, "as of", tap opens the board)
    - _Requirements: 3.1, 3.2, 3.4 to 3.6, 6_
  - [ ] 3.3 The "Heard in 24 h" tag: `OverviewScreen` gets the status from `AppNavHost`, `UpNowCard`, `UpcomingCard` and `ChooseRow` take `heard`
    - _Requirements: 3.3_
  - [ ] 3.4 The About credit
    - _Requirements: 4.1_
- [ ] 4. Privacy, quality and release
  - [ ] 4.1 Privacy policy (wiki) and Play data safety answers name status.amsat.org; published before any Play upload with the feature
    - _Requirements: 4.2_
  - [ ] 4.2 Layouts on the A6 and the emulator, portrait, landscape and red night; the release build (R8) on a device; airplane mode; the switch off with no request (network log); Weather elements show nothing
    - _Requirements: 1, 2.4, 5, 6_
  - [ ] 4.3 Manual checklist, including TalkBack on the A6 by David
  - [ ] 4.4 Wiki Help, README feature line, CHANGELOG, version bump
    - _Requirements: 4.1_
