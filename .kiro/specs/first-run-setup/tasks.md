# Implementation Plan: First-run Setup

Run `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` after each task. The golden regression test must stay green. Every task must leave the app building and runnable.

Order: pure logic and settings first, then the guard (which fixes the silent wrong-location problem on its own), then the shared editor, then the banner, then the guide. Tasks 1 and 2 can ship as a fix even if the guide is delayed.

Branch per task group, one PR at a time, wait for CI and Copilot before merging (merge commit).

Status 13 November 2026: tasks 1.1 and 2.1 to 2.4 shipped in 2.0.3 (PR #21, branch `fix/unset-location-guard`); task 2.5 was added after the spec review. O1 is decided (option a, requirement 10). O2 is decided: two steps, Location and First satellite (tasks 5.3 and 5.4 withdrawn).

- [x] 1. Logic and settings
  - [x] 1.1 Add `HomeLocation.isSet(latText, lonText)` in `domain`, with `HomeLocationTest`
    - Cover zero, `"0.0"`, `"0,0"`, `"-0.0"`, one zero one not, whitespace, blank, non-numeric, comma decimals
    - _Requirements: 6.1, 6.2_
  - [x] 1.2 Add the `setupFinished` setting (default false) and `SetupPolicy.shouldShowAtStart`, with tests
    - _Requirements: 1.1, 1.2, 1.3_

- [x] 2. Never calculate for an unset location (shipped in 2.0.3)
  - [x] 2.1 Add `PassHeader.LocationNotSet`; in `AppViewModel.calculate()`, after both values parse as numbers, check `HomeLocation.isSet`; keep `InvalidLocation` for non-numeric text; add the heading string; extend `AppViewModelTest`
    - _Requirements: 6.2_
  - [x] 2.2 Return `WidgetState.NoLocation` from `WidgetUpdater.compute` when unset; extend the widget test
    - _Requirements: 6.4_
  - [x] 2.3 Show the not-set panel in `OverviewScreen` when unset; extend `OverviewViewModelTest`
    - Shipped as a message only; its button to the guide is part of task 4.1
    - _Requirements: 6.3_
  - [x] 2.4 Checkpoint: build, test, lint; on the emulator after `pm clear`, Calculate pass shows the message and no passes
    - _Requirements: 6.2_
  - [x] 2.5 At start-up, do not restore stored passes while the location is unset (or not a number), and clear them from storage; extend `AppViewModelTest` (unset: nothing restored and the store is cleared; set: restored as before)
    - Added after the spec review: passes saved for 0, 0 by version 2.0.2 or earlier would otherwise show under the banner
    - _Requirements: 6.6_

- [x] 3. Extract the location editor
  - [x] 3.1 Move the location section of `SettingsScreen` into `ui/components/LocationEditor.kt` with no behaviour change; Settings calls it
    - Check on a device before and after: typing a locator, lat/lon, GPS, network, permission denied, provider disabled
    - _Requirements: 2.2, 2.3, 2.4, 9.3_

- [x] 4. Banner and menu item
  - [x] 4.1 Add `LocationNotSetBanner` to the passes controls, driven by the live `homeLat` and `homeLon`; add the same button to the overview's not-set message
    - _Requirements: 6.1, 6.3, 6.5_
  - [x] 4.2 Add the "Setup guide" overflow item and `Routes.SETUP` (placeholder screen until task 5)
    - Built as a usable location-only screen (why-text and the shared `LocationEditor`); task 5 turns it into the two-step guide
    - _Requirements: 1.5_
  - [x] 4.3 Rename the menu item "Set home location" to "Settings" (`menu_settings`); reword `header_location_not_set` and `overview_no_location` to point at the banner and Setup guide; update `docs/tester-test-script.txt` and the README to the new names
    - The README names no menu item, so it needed no change; `docs/tester-invitation.txt` was updated too
    - _Requirements: 10.1, 10.2, 10.3, 10.4_

- [x] 5. The guide
  - [x] 5.1 `SetupFrame` driven by a list of steps (count from the list, so a step can be added later): step indicator, heading, scrolling content with a 600 dp maximum width, Back, Skip and Next/Done in fixed positions; step in `rememberSaveable`; System Back goes to the previous step
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 8.1, 8.3_
  - [x] 5.2 Step 1, location: `LocationEditor`, why-text, summary, Next enabled only when set
    - _Requirements: 2.1, 2.5, 2.6_
  - [-] 5.3 Withdrawn (decision O2): orbital data step
  - [-] 5.4 Withdrawn (decision O2): alerts step
  - [x] 5.5 Step 2, first satellite and Done: choose satellite, save, set `setupFinished`, go to the passes screen, calculate once when the location is set; leave the guide as requirement 7.5 says
    - _Requirements: 5.1, 5.2, 5.3, 7.5_
  - [x] 5.6 Start destination: `MainActivity` passes a nullable `requestedRoute` (no longer defaulting a missing `EXTRA_ROUTE` to `Routes.PASSES`, which the widget also requests); `AppNavHost` uses it if not null, then the guide, then the passes screen; Skip and Back leave the guide with `popOrGoHome` (requirement 7.5). Test that a normal launch on a fresh install opens the guide and a widget tap does not
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6_
  - [x] 5.7 Strings for everything above in `strings.xml`
    - _Requirements: 9.4_

- [ ] 6. Quality
  - [ ] 6.1 Layouts: phone portrait and landscape, tablet portrait and landscape, split-screen; red night mode; 200 percent font; TalkBack headings and labels
    - _Requirements: 8.1, 8.2, 8.3, 8.4_
  - [ ] 6.2 Upgrade check: install over an existing configured build; the guide does not appear and nothing changes
    - _Requirements: 1.2_
  - [ ] 6.3 Fresh install check on the A16 emulator and the SM-T225 (`pm clear`), including denying the location permission
    - _Requirements: 2.4_

- [ ] 7. Documentation
  - [x] 7.1 Update the wiki Home page ("Setting your location") and README; recapture screenshots that show the new screens; confirm the privacy policy still holds
    - Done 14 November 2026: README and wiki Home feature lists and "Setting your location"; wiki Test Script 2.1 says Settings. No README screenshot shows a changed screen (the Settings screen is unchanged and no screenshot shows the menu), so none was recaptured. The privacy policy still holds: the guide uses the same location editor and stores one more on-device setting
    - _Requirements: 9.1, 9.2_
  - [ ] 7.2 Run `manual-test-checklist.md` and record results

- [ ] 8. Release
  - [ ] 8.1 Bump `versionCode`, build the signed bundle from the merged commit, check the built version, upload to the test track
