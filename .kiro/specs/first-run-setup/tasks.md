# Implementation Plan: First-run Setup

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task. The golden regression test must stay green. Every task must leave the app building and runnable.

Order: pure logic and settings first, then the guard (which fixes the silent wrong-location problem on its own), then the shared editor, then the banner, then the guide. Tasks 1 and 2 can ship as a fix even if the guide is delayed.

Branch per task group, one PR at a time, wait for CI and Copilot before merging (merge commit).

- [ ] 1. Logic and settings
  - [ ] 1.1 Add `HomeLocation.isSet(latText, lonText)` in `domain`, with `HomeLocationTest`
    - Cover zero, `"0.0"`, `"0,0"`, `"-0.0"`, one zero one not, whitespace, blank, non-numeric, comma decimals
    - _Requirements: 6.1, 6.2_
  - [ ] 1.2 Add the `setupFinished` setting (default false) and `SetupPolicy.shouldShowAtStart`, with tests
    - _Requirements: 1.1, 1.2, 1.3_

- [ ] 2. Never calculate for an unset location
  - [ ] 2.1 Add `PassHeader.LocationNotSet`; check `HomeLocation.isSet` first in `AppViewModel.calculate()`; keep `InvalidLocation` for non-numeric text; add the heading string; extend `AppViewModelTest`
    - _Requirements: 6.2_
  - [ ] 2.2 Return `WidgetState.NoLocation` from `WidgetUpdater.compute` when unset; extend the widget test
    - _Requirements: 6.4_
  - [ ] 2.3 Show the not-set panel in `OverviewScreen` when unset; extend `OverviewViewModelTest`
    - _Requirements: 6.3_
  - [ ] 2.4 Checkpoint: build, test, lint; on the emulator after `pm clear`, Calculate pass shows the message and no passes
    - _Requirements: 6.2_

- [ ] 3. Extract the location editor
  - [ ] 3.1 Move the location section of `SettingsScreen` into `ui/components/LocationEditor.kt` with no behaviour change; Settings calls it
    - Check on a device before and after: typing a locator, lat/lon, GPS, network, permission denied, provider disabled
    - _Requirements: 2.2, 2.3, 2.4, 9.3_

- [ ] 4. Banner and menu item
  - [ ] 4.1 Add `LocationNotSetBanner` to the passes controls, driven by the live `homeLat` and `homeLon`
    - _Requirements: 6.1, 6.5_
  - [ ] 4.2 Add the "Setup guide" overflow item and `Routes.SETUP` (placeholder screen until task 5)
    - _Requirements: 1.5_

- [ ] 5. The guide
  - [ ] 5.1 `SetupFrame`: step indicator, heading, scrolling content with a 600 dp maximum width, Back, Skip and Next/Done in fixed positions; step in `rememberSaveable`; System Back goes to the previous step
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 8.1, 8.3_
  - [ ] 5.2 Step 1, location: `LocationEditor`, why-text, summary, Next enabled only when set
    - _Requirements: 2.1, 2.5, 2.6_
  - [ ] 5.3 Step 2, orbital data: status line, auto-update switch, source dropdown, Update now with the "already up to date" message
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_
  - [ ] 5.4 Step 3, alerts: explanation, Allow notifications (API 33+), lead-time dropdown, denial message
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_
  - [ ] 5.5 Step 4, first satellite and Done: choose satellite, save, set `setupFinished`, go to the passes screen, calculate once when the location is set
    - _Requirements: 5.1, 5.2, 5.3_
  - [ ] 5.6 Start destination in `AppNavHost` (requested route wins, then the guide, then the passes screen); Skip behaviour
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6_
  - [ ] 5.7 Strings for everything above in `strings.xml`
    - _Requirements: 9.4_

- [ ] 6. Quality
  - [ ] 6.1 Layouts: phone portrait and landscape, tablet portrait and landscape, split-screen; red night mode; 200 percent font; TalkBack headings and labels
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  - [ ] 6.2 Upgrade check: install over an existing configured build; the guide does not appear and nothing changes
    - _Requirements: 1.2_
  - [ ] 6.3 Fresh install check on the A16 emulator and the SM-T225 (`pm clear`), including denying location and notification permissions
    - _Requirements: 2.4, 4.3_

- [ ] 7. Documentation
  - [ ] 7.1 Update the wiki Home page ("Setting your location") and README; recapture screenshots that show the new screens; confirm the privacy policy still holds
    - _Requirements: 9.1, 9.2_
  - [ ] 7.2 Run `manual-test-checklist.md` and record results

- [ ] 8. Release
  - [ ] 8.1 Bump `versionCode`, build the signed bundle from the merged commit, check the built version, upload to the test track
