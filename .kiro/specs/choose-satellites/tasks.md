# Implementation Plan: Choose satellites

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task. The golden regression test must stay green. Every task must leave the app building and runnable, and an empty list must behave exactly as now at every step.
Branch per task group, one PR at a time; merge only when CI is green and Copilot has approved (merge commit). Order agreed by David on 20 Nov 2026: this feature first, then the pass card.

- [ ] 1. The list
  - [ ] 1.1 `CatnumList` (parse, format, junk and duplicates) with `CatnumListTest`
    - _Requirements: 1.2, 1.3_
  - [ ] 1.2 `mySatellites` in `SettingsRepository` (default empty, round trip, junk stored value) with tests
    - _Requirements: 1.1, 1.2_
  - [ ] 1.3 `SatelliteChoice.offered` and `forSearch` with `SatelliteChoiceTest`
    - _Requirements: 3.1, 3.2, 4.1, 4.3, 5.1, 5.2, 5.3_
- [ ] 2. The chooser
  - [ ] 2.1 `ChooseSatellitesScreen` and `Routes.CHOOSE_SATELLITES`: search, count, Select all, Clear all, chosen first, not-in-current-elements group, Done and Cancel; draft kept with `rememberSaveable`
    - _Requirements: 2_
  - [ ] 2.2 The Settings row ("All satellites" or "N chosen")
    - _Requirements: 2.6_
- [ ] 3. The picker
  - [ ] 3.1 `DropdownField` footer entry; the passes screen offers `SatelliteChoice.offered`; "Choose satellites…" opens the chooser
    - _Requirements: 3_
- [ ] 4. The overview
  - [ ] 4.1 `OverviewViewModel` uses the chosen satellites; the "Showing your N satellites" line with Change; the nothing-chosen-is-present message; tests
    - _Requirements: 4_
- [ ] 5. The Contact planner
  - [ ] 5.1 The all-satellites search uses the chosen satellites, restarts on a change, counts the left-out ones; the scope button label; tests
    - _Requirements: 5_
- [ ] 6. Quality
  - [ ] 6.1 Layouts on the A6, the SM-T225 and the emulator, portrait and landscape, red night; timing of the chooser with 99 satellites
    - _Requirements: 6, 7_
  - [ ] 6.2 Manual checklist
- [ ] 7. Documentation and release
  - [ ] 7.1 CHANGELOG, version bump, wiki Help (Settings and Passes sections) and README feature line; reply to Peter
