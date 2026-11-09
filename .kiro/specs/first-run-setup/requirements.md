# Requirements: First-run Setup

## Introduction

Feedback from tester Peter Goodhall (MM9SQL), 9 November 2026: the app "needs a wizard at first start to setup location etc as it wasn't obvious".

Checked in the code (version 2.0.0): a fresh install opens on the passes screen with the home location at its stored defaults (latitude `0`, longitude `0`, locator `JJ00aa`, a point in the Gulf of Guinea). The user can press Calculate pass straight away and gets plausible-looking passes for that wrong place, with nothing saying so. The way to set the location is Settings, behind the overflow menu, and nothing points there.

This spec adds:

1. A short **setup guide** on first start: location, orbital data (keps), pass alerts, first satellite.
2. A **guard** so the app never silently calculates for the default location.
3. A **banner** on the passes screen that stays until the location is set, even if the guide was skipped.

Related specs: `pass-card-layout` and `multi-satellite-passes` (also from this feedback). This spec is independent of both and should be done first.

## Decisions already made

- D1. The guide is shown **once**, only when the home location is not set. Existing users, whose location is already set, never see it.
- D2. The guide can always be **skipped**. A skipped guide leaves the banner (requirement 6) and a menu item to reopen it.
- D3. The guide **reuses** the Settings location logic (`LocatorSync`, `LocationFetcher`), so both places behave the same. Nothing is duplicated.
- D4. **No new permissions.** Location is requested only when the user presses Use GPS or Use network location, as in Settings. Notifications are requested only when the user presses the button on the alerts step (Android 13 and later).
- D5. The guide is **English only**, like the rest of the app (all text in `strings.xml` so translations can follow).

## Assumptions (please confirm or change)

- A1. "Location not set" means the stored latitude **and** longitude both parse to zero. A user whose home really is 0.0, 0.0 (open ocean) would see the banner; they can enter 0.0001. An explicit "set" flag is a possible alternative but needs a migration for existing installs, so it is not used.
- A2. The guide has four steps: Location, Orbital data, Alerts, First satellite. Each step after Location can be skipped with its defaults left alone.
- A3. The first download of keps already happens automatically at start-up when only the bundled elements are loaded (`AppViewModel.initialLoad`). The guide shows its progress and does not start a second one.
- A4. The overflow menu gets one new item, "Setup guide", that reopens the guide at any time.

## Glossary

- **Keps**: Keplerian elements (TLEs), the orbital data used to predict passes.
- **Home location**: the observer position, stored as `homeLat`, `homeLon` (decimal degrees as text) and `homeLocator`.
- **Unset location**: both `homeLat` and `homeLon` parse to zero (the stored defaults).

## Requirements

### Requirement 1: Show the guide on first start

**User story:** As a new user, I want to be taken through the setup I need, so that I do not have to find it myself.

#### Acceptance criteria

1. WHEN the app starts AND the home location is unset AND the guide has not been finished or skipped THEN the app SHALL open on the setup guide instead of the passes screen.
2. WHEN the home location is already set at start-up (an existing user, or an upgrade from an earlier version) THEN the app SHALL NOT show the guide.
3. WHEN the user finishes or skips the guide THEN the app SHALL remember this and SHALL NOT open it again at start-up.
4. WHEN the app is opened from a notification or the widget THEN it SHALL open the requested screen, not the guide. (Both need a set location to exist.)
5. The overflow menu SHALL have a "Setup guide" item that opens the guide at any time, whether or not the location is set.
6. WHEN the guide is opened from the menu THEN it SHALL show the current values and SHALL NOT change anything until the user edits it.

### Requirement 2: Location step

**User story:** As an operator, I want to give my location in the way that suits me, so that passes are right for my station.

#### Acceptance criteria

1. The step SHALL explain in one or two sentences what the location is for and that it stays on the device.
2. The step SHALL offer the same inputs as Settings: IARU locator, latitude and longitude, Use GPS (hidden when the device has no GPS) and Use network location, with the same validation and the same keeping-in-step of the three fields.
3. WHEN the user presses Use GPS or Use network location THEN the app SHALL request location permission if needed, fetch one position, fill the three fields, and show the accuracy, as Settings does.
4. WHEN permission is denied or no fix is found THEN the step SHALL say so and SHALL leave typing a locator available.
5. WHILE the location is unset THEN Next SHALL be disabled with a short reason shown; Skip SHALL stay available.
6. WHEN a valid location has been entered THEN Next SHALL be enabled and the step SHALL show the locator and the place coordinates it will use.

### Requirement 3: Orbital data step

**User story:** As an operator, I want current orbital data, so that predictions are accurate.

#### Acceptance criteria

1. The step SHALL explain that satellite orbits change and that the app downloads fresh elements.
2. The step SHALL show the keps status: downloading, updated with the satellite count, "bundled with the app, may be out of date", or the last failure in plain words.
3. The step SHALL offer the automatic-update switch (default on) and the keps source (default Celestrak amateur), writing to the same settings as Settings.
4. The step SHALL offer "Update now", which follows the existing two-hour download rule and reports "already up to date" when it applies.
5. A failed or absent download SHALL NOT block Next.

### Requirement 4: Alerts step

**User story:** As an operator, I want to be told about passes I care about, if I choose to.

#### Acceptance criteria

1. The step SHALL explain pass alerts in one or two sentences and say they are optional.
2. WHEN the device runs Android 13 or later AND notifications are not allowed THEN the step SHALL offer "Allow notifications", which shows the system permission prompt.
3. WHEN permission is denied THEN the step SHALL say alerts can be enabled later and SHALL still allow Next.
4. The step SHALL offer the alert lead time (default 5 minutes), writing to the same setting as Settings.
5. The step SHALL NOT request exact-alarm access; the existing offer when the first alert is set remains.

### Requirement 5: Finish with a first satellite

**User story:** As a new user, I want to see a pass at the end of the setup, so that I know it works.

#### Acceptance criteria

1. The last step SHALL let the user choose a satellite from the current list, with the previously selected satellite (or the default) preselected.
2. WHEN the user presses Done THEN the app SHALL save the selection, SHALL mark the guide finished, SHALL go to the passes screen, and SHALL calculate the passes once for that satellite.
3. Done SHALL be available even if earlier steps were skipped; in that case the passes screen SHALL show the location banner and SHALL NOT calculate.

### Requirement 6: Never calculate for an unset location

**User story:** As a user, I want a clear message instead of wrong passes, if I have not set my location.

#### Acceptance criteria

1. WHILE the home location is unset THEN the passes screen SHALL show a banner at the top: what is missing, and a button that opens the guide at the Location step.
2. WHEN the user presses Calculate pass while the location is unset THEN the app SHALL NOT calculate and SHALL show the same message in the heading above the list.
3. WHILE the location is unset THEN the all-satellites overview SHALL show the same message and button instead of results.
4. WHILE the location is unset THEN the home-screen widget SHALL show its existing "set your location" state.
5. WHEN the location becomes set THEN the banner SHALL disappear without leaving the screen.
6. Passes restored from a previous session SHALL be unaffected by this requirement.

### Requirement 7: Navigation and state

**User story:** As a user, I want the guide to behave like the rest of the app.

#### Acceptance criteria

1. WHEN the screen is rotated or resized THEN the guide SHALL keep the current step and everything typed.
2. WHEN the app process is killed and restarted mid-guide THEN the guide SHALL reopen at the start of the step it was on.
3. System Back SHALL go to the previous step; on the first step it SHALL leave the guide as Skip does (and the app SHALL then show the passes screen).
4. Each step SHALL show "Step N of 4" as text, with Back, Next and Skip buttons in the same place on every step.

### Requirement 8: Accessibility and layouts

**User story:** As a user with any device or ability, I want the guide to work for me.

#### Acceptance criteria

1. The guide SHALL work on phones and tablets in portrait and landscape; the content SHALL scroll when the screen is short and SHALL be centred with a maximum width on wide screens.
2. Text SHALL stay readable and not clip at 200 percent font size.
3. Step titles SHALL be marked as headings for screen readers; every control SHALL have a spoken label; state SHALL NOT be conveyed by colour alone.
4. The guide SHALL follow the theme, including red night mode.

### Requirement 9: Privacy and consistency

**User story:** As a privacy-conscious user, I want nothing new collected.

#### Acceptance criteria

1. The guide SHALL NOT add permissions, network requests or data collection beyond those in version 2.0.0.
2. The wiki privacy policy SHALL need no change; if that stops being true, it SHALL be updated in the same release.
3. Everything that can be set in the guide SHALL also be changeable in Settings, with the same stored values.
4. All new text SHALL be in `strings.xml`.
