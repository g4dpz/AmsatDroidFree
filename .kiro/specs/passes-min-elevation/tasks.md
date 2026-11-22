# Implementation Plan: Minimum elevation filter on the passes screen

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task. The golden regression test must stay green. Branch per task group, one PR at a time, CI and Copilot before merging (merge commit).

- [x] 1. Logic
  - [x] 1.1 `PassFilter.visible` in `domain` with `PassFilterTest` (zero, boundary at exactly the minimum, kept id, empty list)
    - _Requirements: 1.1, 2.1, 2.4, 4.1_
  - [x] 1.2 Expose the shared `overviewMinElevation` setting to the passes screen as `AppViewModel.minElevation` (read and write; no new setting), with `AppViewModelTest` cases showing that a change on either screen is seen on the other, and that a change made while "Next pass only" is shown or being calculated calculates again
    - _Requirements: 1.1, 1.2_
  - [x] 1.3 `PassCalculator.nextPassReaching` with tests: first pass already high enough, second pass is the first to qualify, none within the limit, cancellation between steps
    - _Requirements: 3.1, 3.2_
- [x] 2. View model
  - [x] 2.1 Derived `visiblePasses` from the setting and the selected pass; changing the setting updates it without a calculation; "Next pass only" recalculates
    - _Requirements: 1.4, 3.3, 4.1_
  - [x] 2.2 `PassHeader` states for the filter and the "no pass reaches" message; `AppViewModelTest` cases
    - _Requirements: 2.2, 2.3, 3.2_
- [ ] 3. Screen
  - [x] 3.1 The picker in `PassesControls` (stacked below a pane width of 700 dp), the note on a selected pass below the minimum, the new strings
    - _Requirements: 1.3, 2.2, 5_
    - Done 20 Nov 2026 in PR #74 (merged), with the changes Copilot asked for: the observer on the shared setting in AppViewModel (so a change from either screen recalculates Next pass only), a pass that ended before the start time is skipped, the filter line and the none-reach message also for restored passes. Tests: PassFilterTest, PassCalculatorTest, AppViewModelTest, PassesPaneLayoutTest.
  - [ ] 3.2 Checkpoint: the manual checklist on the SM-T225, the A6 and the emulators, portrait and landscape, and red night mode
    - _Requirements: all_
    - 20 Nov 2026: checked on the SM-T225 (Next pass only and 24 hours, 0 and 30 degrees, shared with the All satellites screen, red night mode) and a 411 dp window. Also checked on the Galaxy A6 (Android 10, 360 dp) later the same day: three stacked pickers, Sky view button, 24 hours at 30 degrees showing only the 57 degree pass, Next pass only at 30 degrees, and the shared value seen on the All satellites screen; no crash in logcat. The same run confirmed the Android 10 location fallback with a real fix (Use network location set the home location in under 4 seconds with 12 m accuracy). Still to do: a hand-run of the manual checklist by David.
- [ ] 4. Release
  - [ ] 4.1 CHANGELOG, version bump, wiki Help (Passes section) and README line; reply to the tester
    - _Requirements: 1.1_
