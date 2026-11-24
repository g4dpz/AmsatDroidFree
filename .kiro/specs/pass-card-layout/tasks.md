# Implementation Plan: Pass card layout

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task, and `:app:assembleRelease` before release (R8 is on).

The trial branch `try/pass-card` has a first version of tasks 1.1, 1.2 and 2.1 ("tried" below). Nothing from it is on master: each task is ticked only when its code is merged, with its own review and tests.

- [ ] 1. The card
  - [ ] 1.1 `PassCardState` and `PassCardStates.of` with tests at each boundary (tried)
    - _Requirements: 2.1 to 2.3_
  - [ ] 1.2 Pill text and next state change as a pure function, with unit tests
    - _Requirements: 2.2, 2.3, 2.4, 2.7_
  - [ ] 1.3 `PassCardView`: times line, duration and maximum elevation, azimuths, bell, pill, colours from `PassCardColors` by theme mode, border for the selected card (tried, with the old colours)
    - _Requirements: 1, 2.5 to 2.7, 3.3_
- [ ] 2. The list
  - [ ] 2.1 `PassDays.group` with unit tests; day headings in `PassesPane`, keyed by satellite and day (tried, inline)
    - _Requirements: 3.1_
  - [ ] 2.2 Scroll to the top when a calculation finishes
    - _Requirements: 3.2_
  - [ ] 2.3 The tick read inside the cards, once a second only while a card is orange or green
    - _Requirements: 2.4, 4.2_
- [ ] 3. Judge and settle
  - [ ] 3.1 David tries the trial on the Galaxy A6 and the tablet, including a real pass (orange then green); answers O1 to O8; this spec is updated
  - [ ] 3.2 Tasks 1 and 2 made on a branch from master, starting from the trial's code, with the clean-up listed in the design; reviewed and merged
- [ ] 4. Quality and release
  - [ ] 4.1 Layouts on the A6 and the tablet, portrait and landscape, light, dark and red night; TalkBack reads the pill
    - _Requirements: 2.5 to 2.7, 4_
  - [ ] 4.2 Manual checklist
  - [ ] 4.3 Wiki Help (Passes section), README screenshot if wanted, CHANGELOG, version bump (2.4.0)
