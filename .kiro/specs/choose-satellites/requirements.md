# Requirements: Choose satellites

## Introduction
Peter Goodhall (MM9SQL), and several other testers (20 Nov 2026), ask to **choose the satellites they are interested in** instead of being shown the whole list. The Satellite picker offers about 99 satellites; most users work a handful.

This is a filter on what the user can choose from. It is **not** tracking several satellites at once: the app still shows one satellite at a time, and the passes screen, the sky view, the map, the alerts and the widget are unchanged. (An earlier spec, `multi-satellite-passes`, planned merged pass lists and a mode switch. David narrowed the idea to this on 20 Nov 2026; that branch is superseded and unmerged.)

The user picks the satellites once, in a list with search. Three places then offer only those satellites:
1. the **Satellite picker** on the passes screen,
2. the **All satellites** overview,
3. the **Contact planner**'s search across satellites.

## Decisions made (David, 20 Nov 2026)
- D1. **One saved list** serves the picker, the All satellites overview and the Contact planner.
- D2. **If the user has chosen a list, the app uses it; otherwise it uses the ISS.** The user can empty a chosen list completely, and an empty chosen list means nothing is offered (see requirements 3 to 5 for what each screen then says). There is no "empty means everything".
- D3a. **The prompt.** While no list has been chosen the app offers the ISS and tells the user to choose their favourites: being shown all 99 satellites was the original objection, so nobody is shown the full list by default, and the prompt is how they find the way to the chooser. (David, 20 Nov 2026.)
- D3. **One satellite at a time**: nothing about how passes are calculated, stored, selected or shown changes.
- D4. **No large-font handling** (large font sizes are not supported).

## Assumptions (please confirm or change)
- A0. **Nobody is migrated.** A user who has never chosen a list, new or existing, has the ISS as the list. For an existing user the picker therefore offers the ISS and the satellite they have selected (A6) until they choose their own list; their selected satellite is not lost and nothing else about the app changes. (An earlier draft wrote the ISS plus the selected satellite into the list on the first start after an update; David's rule of 20 Nov 2026 is simpler: no list chosen, so the ISS.)
- A0a. **The prompt is a banner** on the passes screen, under the Satellite picker, while no list has been chosen: "Only the ISS is offered. Choose your favourite satellites." with a **Choose satellites** button, in the style of the existing "location not set" banner. It goes away for good when the user presses Done in the chooser (even with an empty list). It also has a **Not now** button, which hides the banner for good without changing the list (the ISS stays the only satellite offered); the user can still choose satellites later from Settings or the picker's "Choose satellites…" entry.
- A1. The list is chosen from **Settings** (a "Choose satellites" row showing how many are chosen) and from the **end of the Satellite picker menu** ("Choose satellites…"). On the overview and the planner a line says "Showing your N satellites" with a **Change** link to the same screen.
- A2. The chooser is a **full screen** on every device (a route like the setup guide), not a dialog: text fields in dialogs are awkward with the keyboard open.
- A3. On the planner the scope choice **Selected satellite / All satellites** keeps its two buttons. The second is labelled **My satellites** and searches only the list.
- A4. The overview has no separate All / My switch: it shows the list. To see everything, press "Select all" in the chooser.
- A5. A satellite in the saved list that is **not in the current orbital elements** (another keps source, or a satellite that has dropped out) is skipped everywhere, and shown in a separate group in the chooser so it can be removed.
- A6. The satellite **currently selected** stays in the picker even if it is not in the list, until another is picked, so the picker never shows something different from the screen and is never blank.
- A7. The saved list is stored as comma-separated catalogue numbers in the preferences (key `mySatellites`). A missing key means no list has been chosen, which reads as the ISS alone. A present value, including an empty one, is exactly the list the user chose.
- A8. **Nobody has a satellite selected until one is saved, and then the ISS is selected.** The app today falls back to the first satellite in the download without saving it; it will fall back to the ISS (when the ISS is in the elements, else the first satellite) and still not save it. A user who has never picked a satellite, new or existing, therefore sees the ISS, which matches the picker. A user who has picked one keeps it. The setup guide's satellite step keeps offering every satellite.

## Requirement 1: The saved list
**User story:** As an operator, I want to say once which satellites I use.
1. The app SHALL keep one list of chosen satellites, by catalogue number, in the settings.
2. WHEN the user has not chosen a list THEN the list SHALL be the ISS (catalogue number 25544), for a new install and for an update alike (A0). WHEN the user has saved a list THEN the list SHALL be exactly what was saved, and SHALL be allowed to be empty. The app SHALL never write a list the user did not choose.
3. The list SHALL survive a restart and an app update. Entries that are not valid catalogue numbers (junk, not whole numbers) SHALL be ignored and never crash the app. A valid catalogue number that is not in the current orbital elements (another keps source, a satellite that has dropped out) SHALL stay saved and SHALL be shown in the chooser's "not in the current elements" group, so a choice is not lost by switching source.
4. Duplicates SHALL be removed and the order SHALL not matter (satellites are always shown in the app's usual order).
5. The list SHALL stay on the device; no network request and no new permission is added.

## Requirement 2: Choosing
**User story:** As an operator, I want to pick my satellites quickly from a long list.
1. The chooser SHALL show every satellite in the current orbital elements with a checkbox, in the same order as the picker (`SatelliteOrder`).
2. It SHALL have a search box that filters as the user types, matching the name or the catalogue number, ignoring case.
3. It SHALL show how many are chosen, and offer "Select all" and "Clear all" (for the satellites shown by the search, when one is typed).
4. Chosen satellites SHALL be marked and be easy to find (shown first, or in a group at the top).
5. Satellites in the saved list that are not in the current elements SHALL be shown in a separate group, marked as not in the current elements, and SHALL be removable.
6. WHEN the user presses Done THEN the list SHALL be saved, even if it is empty; WHEN the user presses Back or Cancel THEN nothing SHALL change.
7. WHEN no list has been saved yet THEN the chooser SHALL open with the ISS and the satellite currently selected ticked, so it matches what the picker offers; this is a draft and nothing SHALL be saved until Done. WHEN a list has been saved THEN the chooser SHALL show exactly that list.
8. The search field and the list SHALL both be usable with the keyboard open.

## Requirement 3: The Satellite picker
1. The picker SHALL offer only the chosen satellites that are in the current elements, plus the currently selected satellite (A6), in the usual order. With an empty list it SHALL therefore offer only the selected satellite.
2. The end of the picker menu SHALL always have a "Choose satellites…" entry that opens the chooser.
3. Choosing a satellite in the picker SHALL work exactly as now (selects it, shows its passes).
4. The first-run setup guide's satellite step SHALL keep offering every satellite.
5. A new install SHALL start with the ISS selected (A8).
6. WHILE no list has been chosen THE passes screen SHALL show the prompt of A0a under the picker, with a button that opens the chooser and a Not now button. Once the user has saved a list, even an empty one, or has pressed Not now, the prompt SHALL not appear again.
7. The prompt SHALL be text, readable and labelled for a screen reader, and SHALL use the existing banner styling in every theme, including red night.

## Requirement 4: The All satellites overview
1. "Up now" and the upcoming passes SHALL use only the chosen satellites that are in the current elements, and a line SHALL say "Showing your N satellites" with a Change link.
2. The calculation, filtering, ordering, progress and the minimum elevation setting SHALL be unchanged; the overview simply calculates for fewer satellites.
3. WHEN the list is empty THEN the overview SHALL say "No satellites chosen" with a link to the chooser. WHEN none of the chosen satellites is in the current elements THEN it SHALL say so, with the same link. It SHALL never show an empty screen without an explanation.

## Requirement 5: The Contact planner
1. The scope button that searches across satellites SHALL be labelled "My satellites" and SHALL search exactly the chosen satellites that are in the current elements, with the same progress, cancellation, order and limits as an all-satellites search.
2. Chosen satellites missing from the current elements SHALL be skipped and the screen SHALL say how many were left out.
3. WHEN the list is empty THEN that search SHALL say "No satellites chosen" with a link to the chooser and search nothing.
4. WHEN the list changes THEN a running My satellites search SHALL restart and its results for the old list SHALL not be shown. A "Selected satellite" search, and its results, SHALL not be affected by a change of the list.
5. A "Selected satellite" search SHALL be unchanged.
6. The planner's saved scope (`plannerAllSatellites`) SHALL keep its meaning: a tester who had the all-satellites search chosen before the update SHALL still have it chosen, now over the list.

## Requirement 6: Performance and robustness
1. Choosing from 99 satellites with a search typed SHALL stay responsive on the Galaxy Tab A7 Lite.
2. A list containing unknown catalogue numbers SHALL show the "not in current elements" group and never crash.
3. Changing the keps source SHALL not change the saved list.

## Requirement 7: Accessibility and layout
1. Controls SHALL have spoken labels; each checkbox SHALL read "name, checked or not checked"; the count SHALL be readable.
2. The layouts SHALL work in portrait and landscape on phones and tablets at the default font size, with nothing clipped on a 360 dp phone.
3. The red night theme SHALL cover every new control.

## Not in scope
- Tracking or listing passes of several satellites together, merged lists, a mode switch (see D3).
- Per-screen lists (D1: one list).
- The widget and alerts (unchanged).
- Large font sizes (D4).
