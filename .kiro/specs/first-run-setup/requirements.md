# Requirements: First-run Setup

## Introduction

Feedback from tester Peter Goodhall (MM9SQL), 9 November 2026: the app "needs a wizard at first start to setup location etc as it wasn't obvious".

Checked in the code (version 2.0.0): a fresh install opens on the passes screen with the home location at its stored defaults (latitude `0`, longitude `0`, locator `JJ00aa`, a point in the Gulf of Guinea). The user can press Calculate pass straight away and gets plausible-looking passes for that wrong place, with nothing saying so. The way to set the location is Settings, behind the overflow menu, and nothing points there.

This spec adds:

1. A short **setup guide** on first start, in two steps: location, then a first satellite.
2. A **guard** so the app never silently calculates for the default location.
3. A **banner** on the passes screen that stays until the location is set, even if the guide was skipped.

Related specs: `pass-card-layout` and `multi-satellite-passes` (also from this feedback). This spec is independent of both and should be done first.

## Status (13 November 2026)

- **Shipped in 2.0.3 (PR #21):** the guard. `HomeLocation.isSet` treats 0, 0 as unset; Calculate pass then shows a message and no passes (and clears any passes from an earlier location, also from storage); the all-satellites overview shows a message; the widget shows its "set your location" state. That covers requirements 6.2, 6.4 and 6.6, and 6.3 except its button. A tester confirmed the behaviour on 13 November.
- **Still to build:** the `setupFinished` setting and start-up rule, the shared location editor, the banner and menu item, and the guide itself (requirements 1, 2, 5, 6.1, 6.3's button, 6.5, 7 to 9).
- **Found since the spec was written:** the same tester could not find "Settings": the overflow menu has no item of that name, because Settings opens from "Set home location". The 2.0.3 messages ("Set it in Settings to see passes ...", "Set your home location in Settings to see the overview.") point at a name the menu does not show. See open decision O1.
- **Analytics:** the usage-analytics consent prompt was planned as an extra step of this guide. Analytics is on hold (13 November), so this guide has no consent step. The guide's frame takes its steps as a list (design, "The guide"), so a step can be added later without reworking it.

## Decisions made on 13 November 2026

- **O1. Menu naming and where the messages point.** DECIDED 13 November 2026: option (a). The menu item "Set home location" is renamed **Settings** (it already opens the Settings screen), a **Setup guide** item is added, and the banner and the 2.0.3 messages point at the guide's Location step instead of at Settings (requirement 10). Rejected: (b) keep "Set home location" and only reword the messages; (c) make the banner the only way in.
- **O2. Number of steps.** DECIDED 13 November 2026: two steps, Location and First satellite. The Orbital data and Alerts steps of the first draft are dropped (requirements 3 and 4 are withdrawn): keps already download by themselves on first start (A3), and the notification permission is already asked for when the first alert is set. Both stay in Settings.

## Decisions already made

- D1. The guide is shown **once**, only when the home location is not set. Existing users, whose location is already set, never see it.
- D2. The guide can always be **skipped**. A skipped guide leaves the banner (requirement 6) and a menu item to reopen it.
- D3. The guide **reuses** the Settings location logic (`LocatorSync`, `LocationFetcher`), so both places behave the same. Nothing is duplicated.
- D4. **No new permissions.** Location is requested only when the user presses Use GPS or Use network location, as in Settings. The guide does not ask for notifications; that stays where it is, when the first alert is set (Android 13 and later).
- D5. The guide is **English only**, like the rest of the app (all text in `strings.xml` so translations can follow).

## Assumptions (please confirm or change)

- A1. "Location not set" means the stored latitude **and** longitude both parse to zero. A user whose home really is 0.0, 0.0 (open ocean) would see the banner; they can enter 0.0001. An explicit "set" flag is a possible alternative but needs a migration for existing installs, so it is not used.
- A2. The guide has two steps: Location, then First satellite (decision O2). Skip is available on both.
- A3. The first download of keps already happens automatically at start-up when only the bundled elements are loaded (`AppViewModel.initialLoad`). The guide does not start a second one. The satellite list on the second step may still be the bundled one if that download has not finished; it updates in place when it does.
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

### Requirement 3: Orbital data step (withdrawn, decision O2)

Withdrawn. Keps keep downloading by themselves at first start, and their source and automatic update stay in Settings.

### Requirement 4: Alerts step (withdrawn, decision O2)

Withdrawn. The notification permission is still asked for when the first alert is set, and the lead time stays in Settings.

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
3. WHILE the location is unset THEN the all-satellites overview SHALL show the same message and button instead of results. (The message shipped in 2.0.3; the button comes with the banner.)
4. WHILE the location is unset THEN the home-screen widget SHALL show its existing "set your location" state.
5. WHEN the location becomes set THEN the banner SHALL disappear without leaving the screen.
6. Passes restored from a previous session SHALL be unaffected by this requirement.

### Requirement 7: Navigation and state

**User story:** As a user, I want the guide to behave like the rest of the app.

#### Acceptance criteria

1. WHEN the screen is rotated or resized THEN the guide SHALL keep the current step and everything typed.
2. WHEN the app process is killed and restarted mid-guide THEN the guide SHALL reopen at the start of the step it was on.
3. System Back SHALL go to the previous step; on the first step it SHALL leave the guide as Skip does (and the app SHALL then show the passes screen).
4. Each step SHALL show "Step N of M" as text (M is the number of steps, two in this version, see O2), with Back, Next and Skip buttons in the same place on every step.

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

### Requirement 10: Menu names and messages (decision O1)

**User story:** As a user, I want every message that tells me where to go to name something I can find.

#### Acceptance criteria

1. The overflow menu item that opens the Settings screen SHALL be labelled "Settings" (it was "Set home location").
2. The overflow menu SHALL have the "Setup guide" item (requirement 1.5) as a separate entry.
3. The passes-screen heading shown when Calculate pass is pressed with no location (`header_location_not_set`) and the overview's message (`overview_no_location`) SHALL point the user at setting up their location through the banner or Setup guide, and SHALL NOT name a menu item that does not exist.
4. The tester script (`docs/tester-test-script.txt`) and the README SHALL use the new menu names in the same release.
