# Requirements: Contact Planner

## Introduction

Idea from David (G4DPZ), 14 November 2026: work out when two ground stations can both use a satellite, and the best azimuth, elevation and time for the contact.

A trial calculation with the app's own orbit code showed it is useful. For RS-44 between IO82xk (UK) and EM29mm (Kansas City, 39.5° N 95.0° W; 6,818 km apart), the next 72 hours from 14 November 2026 held three mutual windows of 1.2, 4.8 and 3.8 minutes. The best one, Sunday 15 November 16:25:34 to 16:30:24 UTC, has both stations at about 3.7° elevation at 16:27:34, the UK pointing at 289° and Kansas at 54°, each about 4,000 km from the satellite. Each end needs its own Doppler correction: at that moment the transponder centre is 145.9635 / 435.6446 MHz for the UK and 145.9657 / 435.6380 MHz for Kansas.

This spec adds a **Contact planner** screen that finds those windows, for the selected satellite or for all of them, and shows both stations' look angles and Doppler.

Related specs: `first-run-setup`, `pass-card-layout` and `multi-satellite-passes`. This spec is independent of them. The release number is chosen when David asks for the bump (P8).

## Decisions (David, 14 November 2026)

- **P1. Name and place.** The feature is called **Contact planner**. It opens from an overflow menu item of that name, below All satellites.
- **P2. The other station.** Entered as an IARU locator, where a 4-character square such as EM29 is accepted (O5), or as a latitude and longitude, with an optional name such as a callsign. The app keeps a short list of saved stations. David's answer was "4 character maidenhead"; he confirmed on 14 November 2026 that 4-character squares are accepted in the planner, in addition to the 6- and 8-character locators and latitude and longitude that Settings accepts.
- **P3. Which satellites.** The selected satellite by default, with a switch to search all satellites in the current keps source.
- **P4. Minimum elevation.** One minimum elevation, the same for both stations, chosen in the planner, default 0°.
- **P5. Best moment.** The time at which the lower of the two elevations is highest; the total path is shown beside it. David's answer was "P4 at same elevation", read as "both stations use the one minimum elevation of P4"; he confirmed that reading on 14 November 2026, so the rule here stands as proposed.
- **P6. Detail.** Both stations' azimuth, elevation and range, and each station's own uplink and downlink Doppler, at the best moment and through the window.
- **P7. Alerts for a mutual window.** None in this spec.
- **P8. Release.** The version number is chosen when David asks for the release bump; this spec does not name one.
- **O1. Start time.** The search always starts now.
- **O2. Time window.** Choices 12, 24 and 48 hours; the maximum is 48 hours and the default is 24. Mutual windows for distant stations are rare (three in 72 hours in the RS-44 example), so a short window often finds nothing.
- **O3. Sky chart and map in the detail.** Not in the first version, which shows numbers only.
- **O4. Number of saved stations.** Up to 10 in the first version.
- **O5. Four-character locators.** `LocatorSync` accepts only 6- or 8-character locators, but operators often give a 4-character square such as EM29. The planner's station editor also accepts a 4-character square and uses its centre (for example EM29 as 39.5° N 95.0° W, stored as EM29mm), saying so under the field. Settings is unchanged.

## Decisions already made

- D1. **No network use and no new permissions.** The calculation uses the keps and transponder database already on the device. The other station is typed in, never fetched.
- D2. **Saved stations stay on the device.** They are stored in the app's preferences, which are not backed up (`allowBackup="false"`). The privacy policy and Play data safety answers stay true.
- D3. **The home station is the home location.** The planner does not have its own copy; changing the home location in Settings changes the planner.
- D4. **English only**, like the rest of the app, with all text in `strings.xml`.

## Glossary

- **Home station**: the home location from Settings.
- **Other station**: the far end of the contact, entered in the planner.
- **Mutual window**: a continuous time during which the satellite is at or above the minimum elevation at both stations.
- **Lower elevation**: at a given moment, the smaller of the two stations' elevations.
- **Best moment**: the moment in a mutual window with the highest lower elevation (P5).
- **Total path**: the sum of the two stations' ranges to the satellite.

## Requirements

### Requirement 1: Open the planner

**User story:** As an operator, I want to find the contact planner easily, so that I can plan a contact without leaving the app.

#### Acceptance criteria

1. The overflow menu SHALL have a "Contact planner" item below "All satellites".
2. WHEN the home location is unset THEN the planner SHALL show the existing "Your home location is not set yet." message with a Set up button that opens the setup guide, and SHALL NOT search.
3. WHEN the planner is opened THEN it SHALL show the last other station used, the last scope and time window, and SHALL search straight away when a station is chosen, as the overview does.
4. Back SHALL return to the screen the planner was opened from.
5. The planner SHALL have a "Calculate again" button that searches again from now with the same choices.

### Requirement 2: The other station

**User story:** As an operator, I want to enter my contact's station once and reuse it, so that planning a regular schedule is quick.

#### Acceptance criteria

1. The user SHALL be able to add a station with a name (optional, up to 20 characters), and an IARU locator or a latitude and longitude.
2. The locator, latitude and longitude SHALL follow the Settings rules (`LocatorSync`): a valid change to one fills in the others, an invalid value is rejected with a message. A 4-character square such as EM29 SHALL also be accepted, stored as the 6-character locator at its centre (EM29mm), with a line under the field saying so (O5).
3. A station SHALL NOT be saved without a valid location.
4. The planner SHALL keep a list of saved stations (O4: up to 10), shown by name, or by locator when it has no name.
5. The user SHALL be able to choose, edit and delete a saved station. Deleting SHALL ask for confirmation.
6. The planner SHALL remember the chosen station between uses.
7. WHEN no station is saved THEN the planner SHALL show a short explanation and an "Add station" button instead of results.
8. The great-circle distance between the two stations SHALL be shown, in kilometres.

### Requirement 3: Minimum elevation

**User story:** As an operator with trees or buildings in some directions, I want to say how low the satellite can be for us to work it, so that the windows are ones we can really use.

#### Acceptance criteria

1. One minimum elevation, used for both stations, SHALL be set in the planner and remembered (P4).
2. Saved stations SHALL NOT have an elevation of their own.
3. The choices SHALL be 0°, 5°, 10°, 15°, 20° and 30°; the default SHALL be 0° (P4).
4. A moment SHALL count as mutual only when both elevations are at or above the minimum elevation.

### Requirement 4: Search

**User story:** As an operator, I want the app to find every chance to make the contact, so that I do not have to compare two pass lists by hand.

#### Acceptance criteria

1. The search SHALL cover the selected satellite by default, or every satellite in the current keps source when "All satellites" is chosen (P3).
2. The search SHALL start now (O1) and cover the chosen time window: 12, 24 or 48 hours, default 24 (O2).
3. The search SHALL find every mutual window in that time that lasts at least 60 seconds, to within 5 seconds at each end, for satellites whose passes last less than a quarter of their orbit (every amateur satellite in a low orbit). Shorter windows may be missed; they are too short for a contact. The limit follows from the pass predictor the passes screen also uses (see the design, "What the search guarantees").
4. WHEN a window is already open at the start THEN it SHALL be shown, starting at the start time.
5. For a satellite that hardly moves in the sky (as the overview defines it, `SatelliteMotion.isStationary`) the planner SHALL NOT search for windows. It SHALL say whether the satellite is above the minimum elevation at both stations now ("Usable from both stations now" with both look angles) and otherwise leave it out.
6. The search SHALL run in the background and SHALL show progress when searching all satellites. It SHALL stop when the planner stops being on screen (the user opens the station editor or another screen, or the app goes to the background) and when a choice changes. WHEN the planner is on screen again THEN a search that did not finish SHALL start again, and finished results SHALL be kept if nothing they depend on has changed.
7. WHEN the keps are older than the app's existing stale limit THEN the planner SHALL show the existing stale-elements warning.
8. A satellite whose elements cannot be used SHALL be skipped without stopping the search.
9. WHEN the selected satellite, the keps, the home location, the other station, the minimum elevation or the time window have changed since results were calculated THEN those results SHALL NOT be shown as current: the planner SHALL search again.

### Requirement 5: Results

**User story:** As an operator, I want a clear list of windows, so that I can pick one and arrange the contact.

#### Acceptance criteria

1. Each window SHALL show the satellite (when searching all), the date, start and end times, the duration, the best moment, and the lower elevation at the best moment.
2. Times SHALL be shown in the device's time zone and format, as on the passes screen, with UTC alongside the best moment, because the two stations are often in different time zones.
3. Windows SHALL be sorted by start time.
4. WHEN there are no windows THEN the planner SHALL say so, give the distance between the stations, and suggest a longer time window or a lower minimum elevation.
5. Windows that have ended SHALL be removed while the screen is open.

### Requirement 6: Detail of a window

**User story:** As an operator, I want to know exactly where to point and what to tune at both ends, so that I can tell my contact and set up my own station.

#### Acceptance criteria

1. Tapping a window SHALL show its detail.
2. The detail SHALL show, for each station at the best moment: azimuth, elevation and range, and the total path.
3. The detail SHALL show a timeline through the window, one row a minute and at the start, best moment and end, with both stations' azimuth and elevation.
4. WHEN the satellite has modes in the transponder database THEN the detail SHALL offer the same mode choice as the sky screen, and show for each station at the best moment its own downlink (receive) and uplink (transmit) frequencies corrected for Doppler, with `DopplerMath`, and the CTCSS tone when the mode has one.
5. WHEN the satellite has no transponder data THEN the detail SHALL say so, as the sky screen does.
6. The detail SHALL be readable aloud by a screen reader in a sensible order: station, then azimuth, elevation, range, then frequencies.

### Requirement 7: The best moment

**User story:** As an operator, I want the app to choose the moment that gives the contact its best chance.

#### Acceptance criteria

1. The best moment SHALL be the time in the window with the highest lower elevation (P5).
2. WHEN several moments share the highest lower elevation (to 0.1°) THEN the one with the shortest total path SHALL be chosen.

### Requirement 8: Accessibility and layouts

**User story:** As a user with any device or ability, I want the planner to work for me.

#### Acceptance criteria

1. The planner SHALL work on phones and tablets in portrait and landscape, and in split-screen; the content SHALL scroll and SHALL be centred with a maximum width on wide screens.
2. Text SHALL stay readable and not clip at 200 percent font size.
3. Section titles SHALL be headings for screen readers; every control SHALL have a spoken label; state SHALL NOT be conveyed by colour alone.
4. The planner SHALL follow the theme, including red night mode.

### Requirement 9: Speed

**User story:** As a user, I want results quickly, so that the planner is pleasant to use.

#### Acceptance criteria

1. A search for one satellite over 48 hours SHALL finish within 2 seconds on the Galaxy Tab A7 Lite (SM-T225).
2. A search of all satellites in the Amateur source over 24 hours SHALL show its first results within 2 seconds and finish within 15 seconds on the SM-T225.
3. Results SHALL appear as each satellite finishes.

### Requirement 10: Correctness

**User story:** As an operator, I want to trust the windows, so that my contact and I are not waiting for a satellite neither of us can see.

#### Acceptance criteria

1. For the windows covered by requirement 4.3, starts and ends SHALL agree within 5 seconds with a brute-force check that steps every second, and the brute-force check SHALL find no window of 60 seconds or more that the planner missed. This SHALL be tested for a low orbit (ISS), a higher one (AO-7) and a slightly elliptical one (RS-44).
2. The look angles SHALL be the same values the sky screen shows for each station at the same moment.
3. The orbit prediction code and its golden regression test SHALL NOT change.
4. The tests SHALL use fixed copies of the orbital elements and fixed start times kept in the repository, never elements downloaded at test time, so that every result is reproducible.

### Requirement 11: Documentation

#### Acceptance criteria

1. The wiki Help page SHALL have a Contact planner section.
2. The README and the wiki Home page feature lists SHALL mention the planner.
3. `CHANGELOG.md` SHALL have an entry for the release.
4. The privacy policy SHALL be checked and changed only if the facts change (they should not, D1 and D2).
