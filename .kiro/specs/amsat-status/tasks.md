# Implementation Plan: AMSAT satellite status reports

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task, and `:app:assembleRelease` for the tasks that add classes (R8 is on in release builds). Every task must leave the app building and runnable.

Branch per task group, one PR at a time; review with the two-model semantic review while Copilot is unavailable; merge on green CI and David's "merge N". Order: after the pass-card layout (S8), unless David says otherwise.

- [ ] 1. Parsing and matching
  - [ ] 1.1 Capture the summary and catalogue into `app/src/test/resources/`; `AmsatStatusParsing` (summary and catalogue, mode from the brackets, malformed input) with tests
    - _Requirements: 2.4, 5.1_
  - [ ] 1.2 `AmsatStatusMatcher` (NORAD, then OSCAR designation, then plain name; unmatched entries dropped; one satellite with several modes) with tests, including the 24 November 2026 counts
    - _Requirements: 2_
  - [ ] 1.3 `AmsatStatusText` (Heard counts and latest time per mode, the other kinds, no reports, UTC) with tests
    - _Requirements: 3.1, 3.2, 3.6_
- [ ] 2. Fetching
  - [ ] 2.1 `AmsatStatusRepository` and its factory: stored copies, 15-minute and 5-minute rules for the summary, daily catalogue, failures keep the stored copy; tests with a fake downloader and clock
    - _Requirements: 1_
  - [ ] 2.2 `showAmsatStatus` setting and its Settings row; off means no request
    - _Requirements: 1.5_
- [ ] 3. Showing
  - [ ] 3.1 `AppViewModel.amsatStatus`, refresh on start; the passes-screen line (tap opens the board, "as of" when old)
    - _Requirements: 1.1, 3.1, 3.2, 3.4, 3.5_
  - [ ] 3.2 The "Heard in 24 h" tag on the overview and the chooser
    - _Requirements: 3.3_
  - [ ] 3.3 The About credit
    - _Requirements: 4.1_
- [ ] 4. Quality
  - [ ] 4.1 Layouts on the A6 and the emulator, portrait, landscape and red night; the release build (R8) on a device; airplane mode (stored copy, then nothing); the switch off (no request, checked with the network log)
    - _Requirements: 1.4, 1.5, 5, 6_
  - [ ] 4.2 Manual checklist
- [ ] 5. Documentation and release
  - [ ] 5.1 Privacy policy and Play data safety answers (status.amsat.org), wiki Help, README feature line, CHANGELOG, version bump
    - _Requirements: 4_
