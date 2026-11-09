# Requirements: My Satellites (passes for several satellites)

## Introduction

Feedback from tester Peter Goodhall (MM9SQL), 9 November 2026: "option to select more than one satellite would be nice too but I know this never supported it".

Today the passes screen works for **one** satellite at a time: pick it, press Calculate pass, read its passes. The All satellites overview shows everything, but it is a different screen with a different purpose (what is up now and what comes next across the whole catalogue) and cannot be narrowed to the satellites an operator actually uses.

This spec adds **My satellites**: a personal list of satellites (for example AO-7, AO-91, RS-44, SO-50), and a passes view that merges the passes of all of them into one list in time order, each pass showing which satellite it belongs to.

What already supports this (checked in the code, version 2.0.0): `PassSummary` carries `catnum` and `satName`; `PassAlert` is stored per pass with its satellite; the sky and map screens take the satellite from the selected pass; the overview calculates many satellites in parallel with `OverviewCalculator`. What does not: the passes list state and its stored copy (`PassStore`) hold one satellite, and the picker is single-choice.

Related specs: `pass-card-layout` (the card needs a place for the satellite name, so do that first) and `first-run-setup`.

## Open decisions (please answer before building)

- O1. **Minimum elevation filter.** With several LEO satellites the list gets long and includes passes that peak at 1 or 2 degrees. Recommendation: reuse the existing overview setting (Minimum elevation 0, 10, 20, 30 degrees) so the two lists agree, and show it as a dropdown in My satellites mode. Alternative: no filter in this version.
- O2. **Widget.** Should the widget show the earliest next pass across My satellites when that mode is on? Recommendation: yes, but as a final optional task, so the main feature can ship without it.
- O3. **Where the mode lives.** Recommendation: a two-choice segmented control above the satellite field ("One satellite" / "My satellites") on the passes screen. The chosen mode is remembered.
- O4. **Starting list.** Recommendation: start empty; the first time the mode is chosen, open the editor straight away. Alternative: preselect the currently selected satellite.

## Decisions already made

- D1. The existing single-satellite flow is unchanged and stays the default.
- D2. The list is stored on the device (settings), by catalogue (NORAD) number, in the order the user added them.
- D3. Passes are calculated on the device with the existing prediction code. No new network use. The privacy policy needs no change.
- D4. Alerts, the sky view, Aim mode, the map, Doppler and the pass card all work for a pass from any satellite in the merged list, because they already follow the selected pass.
- D5. All new text is in `strings.xml`; English only.

## Assumptions (please confirm or change)

- A1. A list of up to about 100 satellites is supported (the full catalogue is 99 today); no hard cap is enforced, but the editor and calculation are tested at 99.
- A2. Geostationary satellites (no passes) that are above the horizon appear in a short "Always up" section at the top of the merged list; those below the horizon are left out, as in the overview.
- A3. A satellite in the list that is missing from the current keps (for example after switching source) stays in the list, is skipped in the calculation, and is shown as "not in current elements" in the editor, so choices are not lost.
- A4. Mike Hartley's and other contributors' work on the passes screen may overlap this change; check open pull requests before starting.

## Glossary

- **My satellites**: the user's chosen list of satellites for the merged view.
- **Merged list**: the passes of all of them, sorted by start time.
- **Catnum**: NORAD catalogue number, the app's identity for a satellite.
- **Always up**: a geostationary or similar satellite that is above the horizon and has no passes.

## Requirements

### Requirement 1: Choose the mode

**User story:** As an operator, I want to switch between one satellite and my satellites, so that I can use either way of working.

#### Acceptance criteria

1. The passes screen SHALL offer "One satellite" and "My satellites" as a two-choice control above the satellite field.
2. WHEN the user chooses a mode THEN the app SHALL remember it across restarts.
3. In One satellite mode the screen SHALL behave exactly as in version 2.0.0.
4. In My satellites mode the single satellite dropdown SHALL be replaced by a summary of the list (for example "AO-7, AO-91 and 2 more") and an Edit button.
5. WHEN My satellites mode is chosen AND the list is empty THEN the editor SHALL open straight away, with a short explanation.

### Requirement 2: Edit the list

**User story:** As an operator, I want to pick my satellites quickly from a long list.

#### Acceptance criteria

1. The editor SHALL show every satellite in the current keps with a checkbox, sorted as the single picker is (`SatelliteOrder`).
2. The editor SHALL have a search box that filters the list as the user types, matching the name or the catalogue number, ignoring case.
3. The editor SHALL show the number selected and offer "Clear all".
4. Selected satellites SHALL be shown first, or marked, so the user can review the choice.
5. Satellites in the saved list that are not in the current keps SHALL be shown in a separate group, marked "not in current elements", and SHALL be removable.
6. WHEN the user confirms (Done) THEN the list SHALL be saved; WHEN the user cancels THEN nothing SHALL change.
7. The editor SHALL work on phones (full screen) and tablets (dialog or side sheet), with the search field and list both usable with the keyboard open.

### Requirement 3: The merged pass list

**User story:** As an operator, I want to see what is coming across all my satellites, so that I can plan my time.

#### Acceptance criteria

1. WHEN the user presses Calculate pass in My satellites mode THEN the app SHALL calculate the passes of every satellite in the list for the chosen start time and range, and SHALL show them in one list sorted by start time.
2. Each pass card SHALL show the satellite's name (at the start of the header, from the card layout spec) in addition to its usual information.
3. The range choices (next pass only, 6, 12 or 24 hours) SHALL apply to the merged list as follows: "next pass only" SHALL mean the next pass of each satellite in the list.
4. The calculation SHALL run off the main thread and in parallel across satellites, SHALL be cancellable (a newer request replaces an older one), and SHALL show progress.
5. Satellites that cannot be calculated (bad elements, never rises, not in the keps) SHALL be left out without an error for the whole list; a one-line note SHALL say how many were skipped.
6. WHEN a satellite in the list is always up THEN it SHALL be shown in an "Always up" section above the passes with its current azimuth and elevation.
7. IF a minimum elevation filter is adopted (O1) THEN passes whose maximum elevation is lower SHALL be hidden and the filter SHALL be shown on screen.
8. The heading above the list SHALL say how many satellites and passes are shown and for what period.
9. Elements older than the stale limit (the existing `ElementAge` rule) SHALL be flagged: one line naming up to three satellites and a count of any more.
10. WHEN the list or the range changes THEN the old results SHALL be cleared rather than shown for the wrong settings, until Calculate pass is pressed again.

### Requirement 4: Working a pass from the merged list

**User story:** As an operator, I want a pass from any of my satellites to behave like any other pass.

#### Acceptance criteria

1. WHEN the user taps a pass THEN the app SHALL select that pass and its satellite, and the sky view, Aim mode, map and Doppler frequencies SHALL show that satellite.
2. WHEN the user long-presses a pass THEN an alert SHALL be set or cleared for that pass as today, with the right satellite name in the notification and in the spoken text.
3. Alert state SHALL show on the card for passes of any satellite.
4. Rotating the device or resizing the window SHALL keep the mode, the list, the results and the selected pass.

### Requirement 5: Remembering results

**User story:** As an operator, I want the last results to still be there when I reopen the app.

#### Acceptance criteria

1. The stored last results (`passes.json`) SHALL be able to hold passes of several satellites, each with its catalogue number and name.
2. A `passes.json` written by version 2.0.0 (one satellite) SHALL still load.
3. Results restored from a previous session SHALL be labelled as such, as they are today, and SHALL be shown in the mode they were calculated in.
4. A damaged or unknown file SHALL be ignored, as today.

### Requirement 6: Overview and other screens

**User story:** As a user, I want the screens to stay consistent.

#### Acceptance criteria

1. WHEN the user opens a pass from the All satellites overview THEN the app SHALL switch to One satellite mode for that satellite, as the overview does today, and SHALL leave the saved list untouched.
2. The overview SHALL offer an option to show only My satellites (when the list is not empty). (Optional; see task 9.)
3. The overflow menu actions that need a single satellite (Map view) SHALL use the selected pass's satellite in My satellites mode, and SHALL be disabled when nothing is selected.

### Requirement 7: Widget (subject to O2)

**User story:** As an operator, I want the widget to show my next pass without opening the app.

#### Acceptance criteria

1. IF O2 is accepted AND My satellites mode is chosen AND the list is not empty THEN the widget SHALL show the earliest upcoming pass across the list, with the satellite name.
2. IF the list is empty THEN the widget SHALL show its "choose a satellite" state.
3. In One satellite mode the widget SHALL be unchanged.

### Requirement 8: Performance and robustness

**User story:** As a user on a modest device, I want it to be quick.

#### Acceptance criteria

1. Calculating 99 satellites for 24 hours SHALL complete in under two seconds on the Galaxy Tab A7 Lite (the overview does 99 in about 0.4 to 2 seconds there today).
2. Each satellite SHALL use its own predictor and ground station (the prediction code writes into the ground station), as `OverviewCalculator` does.
3. The editor SHALL stay responsive with 99 entries and a search filter.
4. A saved list that refers to unknown catalogue numbers SHALL never crash the app.

### Requirement 9: Accessibility, layouts and privacy

**User story:** As any user, I want the feature to be usable and to collect nothing new.

#### Acceptance criteria

1. Controls SHALL have spoken labels; the mode control SHALL announce its selected state; the editor checkboxes SHALL read "name, checked or not checked".
2. Text SHALL not clip at 200 percent font size; the layouts SHALL work in portrait, landscape and split-screen on phones and tablets.
3. The red night theme SHALL cover every new control.
4. The feature SHALL add no permissions, no network requests and no data leaving the device.
