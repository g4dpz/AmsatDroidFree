# Implementation Plan: Pass card layout

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task, and `:app:assembleRelease` before release (R8 is on). The trial branch `try/pass-card` holds a working version of tasks 1 and 2.

- [ ] 1. The card
  - [x] 1.1 `PassCardState` and `PassCardStates.of` with tests at each boundary (trial)
    - _Requirements: 2.1 to 2.3_
  - [x] 1.2 `PassCardView`: times line, duration and maximum elevation, azimuths, bell, pill, colours for light, dark and red night, border for the selected card (trial)
    - _Requirements: 1, 2.5 to 2.7, 3.3_
- [ ] 2. The list
  - [x] 2.1 Day headings and the once-a-second tick in `PassesPane`; heading keys that name the satellite (trial)
    - _Requirements: 2.4, 3.1, 3.2, 4.2_
- [ ] 3. Judge and settle
  - [ ] 3.1 David tries the trial on the Galaxy A6 and the tablet, including a real pass (orange then green); answers O1 to O4; this spec is updated
  - [ ] 3.2 Changes from 3.1 made on a branch from master, with the trial's code as the start, reviewed and merged
- [ ] 4. Quality and release
  - [ ] 4.1 Layouts on the A6 and the tablet, portrait and landscape, light, dark and red night; TalkBack reads the pill
    - _Requirements: 2.5 to 2.7, 4_
  - [ ] 4.2 Manual checklist
  - [ ] 4.3 Wiki Help (Passes section), README screenshot if wanted, CHANGELOG, version bump (2.4.0)
