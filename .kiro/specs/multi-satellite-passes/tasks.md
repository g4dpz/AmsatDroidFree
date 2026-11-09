# Implementation Plan: My Satellites (passes for several satellites)

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task. The golden regression test must stay green. Every task must leave the app building and runnable, and One satellite mode must keep working at every step.

Before starting: answer the open decisions O1 to O4 in `requirements.md`, check open pull requests that touch `PassesScreen.kt` or `AppViewModel.kt`, and land `pass-card-layout` (the card needs the satellite name).

One PR at a time; wait for CI and Copilot before merging (merge commit).

- [ ] 1. Settings and list helper
  - [ ] 1.1 Add `CatnumList` (parse, format, dedupe, drop junk) with `CatnumListTest`
    - _Requirements: 8.4_
  - [ ] 1.2 Add `PassMode`, `passMode` and `mySatellites` to `SettingsRepository` with tests (defaults, round trip, junk values)
    - _Requirements: 1.2, 2.6_

- [ ] 2. Merged calculation
  - [ ] 2.1 Add `MergedPassCalculator` and `MergedResult` over `OverviewCalculator`, with `MergedPassCalculatorTest`
    - Cover order and tie-break, always-up, skipped count, next-pass-only per satellite, missing catnums, elevation filter, cancellation
    - _Requirements: 3.1, 3.3, 3.5, 3.6, 3.7, 8.2_
  - [ ] 2.2 Add a performance test with 99 satellites and a generous bound
    - _Requirements: 8.1_

- [ ] 3. Storage
  - [ ] 3.1 Change `StoredPasses` to per-pass satellites; `PassStore` writes schema 2 and still reads schema 1; tests for round trip, schema 1, corrupt file
    - _Requirements: 5.1, 5.2, 5.4_

- [ ] 4. View model
  - [ ] 4.1 Add mode and list to `AppUiState`; `setMode`, `setMySatellites` (both clear results); header types `Merged` and `MergedEmpty`
    - _Requirements: 1.2, 3.8, 3.10_
  - [ ] 4.2 `calculate()` dispatches by mode; the merged path shares the generation and cancellation logic; saves and restores results with the mode
    - _Requirements: 3.1, 3.4, 5.3_
  - [ ] 4.3 `selectPass` also sets `selectedCatnum`; `showPass` switches to One satellite mode; tests for both
    - _Requirements: 4.1, 6.1_

- [ ] 5. Editor
  - [ ] 5.1 `MySatellitesEditor`: checkbox list, search, count, Clear all, selected first, "not in current elements" group, Done and Cancel; phone full screen and tablet dialog; draft kept with `rememberSaveable`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 9.1_

- [ ] 6. Passes screen
  - [ ] 6.1 Mode control, list summary and Edit button; open the editor when the list is empty; keep One satellite mode unchanged
    - _Requirements: 1.1, 1.3, 1.4, 1.5_
  - [ ] 6.2 Satellite name on the card, "Always up" section, skipped note, merged stale-elements line, optional minimum elevation picker (O1)
    - _Requirements: 3.2, 3.6, 3.7, 3.9, 4.3_
  - [ ] 6.3 Check that tap, long-press alert, sky, Aim, map and Doppler work for a pass of the second and third satellite in the list
    - _Requirements: 4.1, 4.2, 4.4, 6.3_

- [ ] 7. Quality
  - [ ] 7.1 Layouts and access: A16 and SM-T225, portrait, landscape, split-screen, 200 percent font, TalkBack, red night; timing of 99 satellites on the SM-T225
    - _Requirements: 8.1, 9.1, 9.2, 9.3_
  - [ ] 7.2 Upgrade check: install over a build with a saved single-satellite `passes.json`; it loads in One satellite mode
    - _Requirements: 5.2_

- [ ] 8. Widget (only if O2 is accepted)
  - [ ] 8.1 `WidgetUpdater` picks the earliest next pass across the list in My satellites mode; `earliest` helper and tests
    - _Requirements: 7.1, 7.2, 7.3_

- [ ] 9. Overview filter (optional)
  - [ ] 9.1 "My satellites" chip on the overview screen
    - _Requirements: 6.2_

- [ ] 10. Documentation and release
  - [ ] 10.1 Update the README, the wiki Home page and the Play listing description; recapture screenshots
  - [ ] 10.2 Run `manual-test-checklist.md` and record results
  - [ ] 10.3 Bump `versionCode`, build the signed bundle from the merged commit, check the built version, upload to the test track
