# Implementation Plan: Minimum elevation filter on the passes screen

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task. The golden regression test must stay green. Branch per task group, one PR at a time, CI and Copilot before merging (merge commit).

- [ ] 1. Logic
  - [ ] 1.1 `PassFilter.visible` in `domain` with `PassFilterTest` (zero, boundary at exactly the minimum, kept id, empty list)
    - _Requirements: 1.1, 2.1, 2.4, 4.1_
  - [ ] 1.2 Expose the shared `overviewMinElevation` setting to the passes screen as `AppViewModel.minElevation` (read and write; no new setting), with `AppViewModelTest` cases showing that a change on either screen is seen on the other, and that a change made while "Next pass only" is shown or being calculated calculates again
    - _Requirements: 1.1, 1.2_
  - [ ] 1.3 `PassCalculator.nextPassReaching` with tests: first pass already high enough, second pass is the first to qualify, none within the limit, cancellation between steps
    - _Requirements: 3.1, 3.2_
- [ ] 2. View model
  - [ ] 2.1 Derived `visiblePasses` from the setting and the selected pass; changing the setting updates it without a calculation; "Next pass only" recalculates
    - _Requirements: 1.4, 3.3, 4.1_
  - [ ] 2.2 `PassHeader` states for the filter and the "no pass reaches" message; `AppViewModelTest` cases
    - _Requirements: 2.2, 2.3, 3.2_
- [ ] 3. Screen
  - [ ] 3.1 The picker in `PassesControls` (stacked below a pane width of 700 dp), the note on a selected pass below the minimum, the new strings
    - _Requirements: 1.3, 2.2, 5_
  - [ ] 3.2 Checkpoint: the manual checklist on the SM-T225, the A6 and the emulators, portrait and landscape, and red night mode
    - _Requirements: all_
- [ ] 4. Release
  - [ ] 4.1 CHANGELOG, version bump, wiki Help (Passes section) and README line; reply to the tester
    - _Requirements: 1.1_
