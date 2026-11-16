# Implementation Plan: Map centred on the user's location

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task. The golden regression test and the existing `MapProjectionTest` must stay green. Every task must leave the app building and runnable.
Order: pure logic first, then the setting, then the drawing. One branch, one PR; wait for CI and Copilot before merging (merge commit). Targets 2.2.0.

- [x] 1. Projection
  - [x] 1.1 Add `centreDeg` (default 0.0) to `MapProjection.x` and `polylines`; tests: centre 0 equals today's results, home 151.2 puts 151.2 in the middle, -122, 180 and -180, a longitude one degree either side of the seam, a track crossing the seam is broken there
    - _Requirements: 2.1, 3.2, 3.3, 5.1, 5.2_
  - [x] 1.2 A pure function for the picture's seam: `MapProjection.seamFraction(centreDeg)` and the two source/destination rectangles for a map width and a picture width, with tests (they add up exactly, no gap, centre 0 gives one piece)
    - _Requirements: 2.2, 5.1_
- [x] 2. Setting
  - [x] 2.1 `centreMapOnHome` in `SettingsRepository` (default false), a switch under Display with its string, and `SettingsRepositoryTest` cases
    - _Requirements: 1.1, 1.2_
- [x] 3. Drawing
  - [x] 3.1 `WorldMap`: new `centreLongitudeDeg` parameter, picture drawn in two pieces, home, satellite, track and footprint through the shifted `x`; the caller passes the home longitude when the setting is on and the location is set
    - _Requirements: 1.3, 1.4, 2, 3, 4.1, 5.3_
  - [x] 3.2 Checkpoint: on the tablet and the emulator with home set to Sydney (about 151.2 east), then San Francisco and 179.9 east: the home icon is in the middle, no seam or gap, the track and footprint line up with the coast, a pass over Australia is no longer split, red night looks right, the switch is off by default and the map is unchanged when off; keep a screenshot for David to share with the requester
    - _Requirements: all_
    - Done 16 Nov 2026 at David's request: checked on the emulator in portrait with home in Sydney (centred and off; home in the middle, Australia whole, track and footprint on the coasts, no seam) and by David on the SM-T225, who found it good for a New Zealand station as well. Not checked separately: red night mode, the large-tablet dashboard map, San Francisco and 179.9 east (covered by unit tests only).
- [x] 4. Release
  - [x] 4.1 CHANGELOG entry and version bump for 2.2.0 (next versionCode after the last upload), wiki Help (Map view section) and README feature line; check the privacy policy still holds (it does: nothing new is sent)
    - _Requirements: 1.1_
    - Done 16 Nov 2026: 2.2.0, versionCode 2102 (2.1.0 / 2101 was never uploaded, so testers go from 2.0.8 straight to 2.2.0, as they did past 2.0.1). README line added; wiki Help Map view section updated.
