# Implementation Plan: Choose satellites

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task. The golden regression test must stay green. Every task must leave the app building and runnable, and a user who updates must keep the satellite they use at every step.
Branch per task group, one PR at a time; merge only when CI is green and Copilot has approved (merge commit). Order agreed by David on 20 Nov 2026: this feature first, then the pass card.

- [ ] 1. The list
  - [ ] 1.1 `CatnumList` (parse, format, junk and duplicates, `ISS`) with `CatnumListTest`
    - _Requirements: 1.3, 1.4_
  - [ ] 1.2 `mySatellites` in `SettingsRepository`: absent key on a new install gives `{ISS}`, on an update `{ISS, selectedCatnum}`, written once and never again; a present empty value stays empty; junk ignored; tests for each case
    - _Requirements: 1.1, 1.2, 1.3_
  - [ ] 1.3 `SatelliteChoice.offered` and `forSearch` with `SatelliteChoiceTest` (empty, ISS only, some missing, none present, selected outside the list); a new install selects the ISS in `AppViewModel.initialLoad` (A8) with a test
    - _Requirements: 3.1, 3.5, 4, 5_
- [ ] 2. The chooser
  - [ ] 2.1 `ChooseSatellitesScreen` and `Routes.CHOOSE_SATELLITES`: search, count, Select all, Clear all, chosen first, not-in-current-elements group, Done and Cancel; draft kept with `rememberSaveable`; Done with nothing ticked saves an empty list
    - _Requirements: 2_
  - [ ] 2.2 The Settings row ("N chosen" or "None chosen")
    - _Requirements: 2_
- [ ] 3. The picker
  - [ ] 3.1 `DropdownField` footer entry; the passes screen offers `SatelliteChoice.offered`; "Choose satellites…" opens the chooser
    - _Requirements: 3_
- [ ] 4. The overview
  - [ ] 4.1 `OverviewViewModel` uses the chosen satellites; the "Showing your N satellites" line with Change; the "No satellites chosen" and none-present messages; tests
    - _Requirements: 4_
- [ ] 5. The Contact planner
  - [ ] 5.1 The all-satellites search uses the chosen satellites, restarts on a change, counts the left-out ones, says "No satellites chosen" for an empty list; the scope button label; tests
    - _Requirements: 5_
- [ ] 6. Quality
  - [ ] 6.1 Layouts on the A6, the SM-T225 and the emulator, portrait and landscape, red night; timing of the chooser with 99 satellites
    - _Requirements: 6, 7_
  - [ ] 6.2 Upgrade check: install over a build with a saved satellite and no list; the list is the ISS and that satellite
    - _Requirements: 1.2_
  - [ ] 6.3 Manual checklist
- [ ] 7. Documentation and release
  - [ ] 7.1 CHANGELOG, version bump, wiki Help (Settings and Passes sections) and README feature line; reply to Peter
