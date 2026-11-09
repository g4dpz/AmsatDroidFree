# Implementation Plan: Pass Information Layout

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task. The golden regression test must stay green. Every task must leave the app building and runnable.

Before task 2: get Peter's answer to O1 and an OscarWatch screenshot. Task 1 (pure code) can start without it.

One PR at a time; wait for CI and Copilot before merging (merge commit).

- [ ] 1. Pure logic
  - [ ] 1.1 Add `PassCardLayout.columns(widthDp, fontScale)` to `LayoutThresholds.kt` with `PassCardLayoutTest`
    - Cover the boundary widths and font scales 1.0, 1.3, 2.0
    - _Requirements: 4.1, 4.2, 4.4, 7.2_
  - [ ] 1.2 Add `PassSummary.header`, `cells` and `spokenDescription` (and the new strings) with `PassCellsTest`
    - Cover weekday, in-progress text, comma-decimal locale, 12-hour clock, a pass across midnight
    - _Requirements: 1.1, 1.2, 1.3, 1.5, 1.7, 6.1, 7.1_

- [ ] 2. Reusable layout pieces
  - [ ] 2.1 `Cell`, `CellRow` and `LabelValueTable` in `ui/components/Cells.kt`, with tabular figures
    - Check with `@Preview`s at 320, 360, 411 and 600 dp, font scale 1.0 and 2.0
    - _Requirements: 3.1, 3.2, 4.3_

- [ ] 3. Pass list card
  - [ ] 3.1 Rebuild `PassCard` from the header and `CellRow`; keep tap, long-press, highlight and bell; merged accessibility description
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 6.1, 6.3, 6.4_
  - [ ] 3.2 Check the passes pane in the two-pane and dashboard layouts, and the short-pane scrolling layout from phone landscape
    - _Requirements: 4.1, 4.2, 4.3_

- [ ] 4. Sky screen blocks
  - [ ] 4.1 Pass information: use the same cells and header as the card
    - _Requirements: 2.1_
  - [ ] 4.2 Satellite position as an aligned `LabelValueTable`
    - _Requirements: 2.2_
  - [ ] 4.3 Frequencies as a table (Nominal, Tune or Transmit, Shift) with dashes when there is no live reading; messages stay as text below
    - _Requirements: 2.3, 2.4, 2.5, 6.2_

- [ ] 5. Optional compass names (only if O2 is accepted)
  - [ ] 5.1 Add the 16-point names as translatable strings and show them beside azimuths, dropping them before any number is cut off
    - _Requirements: 5.1, 5.2_

- [ ] 6. Quality
  - [ ] 6.1 Check light, dark and red night; A16 and SM-T225; portrait, landscape and split-screen; 200 percent font; TalkBack
    - _Requirements: 3.3, 4.3, 6.1, 6.2_
  - [ ] 6.2 Compare against Peter's OscarWatch screenshot and adjust the arrangement if it differs in a way that matters (O1)

- [ ] 7. Documentation and release
  - [ ] 7.1 Recapture the README and wiki screenshots that show pass cards or the Frequencies block
    - _Requirements: 7.3_
  - [ ] 7.2 Run `manual-test-checklist.md` and record results
  - [ ] 7.3 Bump `versionCode`, build the signed bundle from the merged commit, check the built version, upload to the test track
