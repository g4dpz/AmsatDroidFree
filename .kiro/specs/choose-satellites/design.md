# Design: Choose satellites

## Where things are today
- `AppUiState.satellites` is every satellite in the current elements; the Satellite picker on `PassesScreen` (`DropdownField`, `PassesControls`) offers all of them. `SatelliteOrder` sorts them.
- `OverviewViewModel` calculates for `viewModel.tles` (every satellite) with `OverviewCalculator`; the Contact planner's "All satellites" scope searches every satellite (`plannerAllSatellites` in the settings, `ContactPlannerViewModel`).
- Settings are in `SettingsRepository` over a `KeyValueStore`; routes are in `AppNavHost`.

## The change
### The list
- `CatnumList` (pure, `domain`): `parse(text): Set<Int>` (comma separated, trims, drops junk and duplicates), `format(set): String` (sorted, comma separated), and `ISS = 25544`.
- `mySatellites` in `SettingsRepository`: `Setting<Set<Int>>` stored as one string under the key `mySatellites`; it may be empty.
- **No chosen list.** While the key is absent the setting reads as `{ISS}` (`CatnumList.ISS`); nothing is written. The key is written only when the user presses Done in the chooser, and from then on the stored value is exactly what the user chose, so an empty string is a deliberately empty list and the app never overwrites it.
- **Selected satellite on a new install:** `AppViewModel.initialLoad` falls back to the ISS, then to the first satellite, when no satellite has been selected yet (A8).

### One rule, used three times
`SatelliteChoice` (pure, `domain`):

    fun offered(all: List<SatelliteItem>, chosen: Set<Int>, selectedCatnum: Int?): List<SatelliteItem>
    fun forSearch(all: List<TLE>, chosen: Set<Int>): Selection   // the TLEs to use and how many chosen ones are missing

`offered`: the items whose catalogue number is in `chosen`, plus the item for `selectedCatnum`, kept in `all`'s order. `forSearch`: the chosen TLEs that exist and the count of the rest. An empty `chosen` gives only the selected satellite from `offered` and nothing from `forSearch`; the overview and the planner show their "No satellites chosen" message when `forSearch` is empty and `chosen` is empty, and their "none of your satellites is in the current elements" message when `chosen` is not empty but nothing is found. Both are tested (ISS only, several chosen, empty, some missing, none present, selected outside the list, junk numbers).

### Screens
- **Picker:** `PassesControls` passes `SatelliteChoice.offered(state.satellites, chosen, state.selectedCatnum)` to the `DropdownField`, and the field gets an extra last menu entry "Choose satellites…" (`DropdownField` takes an optional `footer` label and callback; the footer does not select anything).
- **Chooser:** `ChooseSatellitesScreen` at `Routes.CHOOSE_SATELLITES`: a top bar with Cancel and Done, a search `OutlinedTextField`, a count line with Select all and Clear all, and a `LazyColumn` of checkbox rows (chosen first, then the rest in `SatelliteOrder`, then a "Not in the current elements" group). The draft is a `rememberSaveable` set, written to the setting on Done only; Done with nothing ticked saves an empty list.
- **Settings:** a "Choose satellites" row under the orbital elements section, showing "N chosen" (or "None chosen"), opening the chooser.
- **Prompt:** `ChooseSatellitesBanner` (modelled on `LocationNotSetBanner`) at the top of `PassesControls`, under the picker, shown while `SettingsRepository.mySatellitesChosen` is false (the key is absent); its button opens the chooser. Pressing Done writes the key, so the banner never returns. Strings in `strings.xml`: "Only the ISS is offered. Choose your favourite satellites." and "Choose satellites".
- **Overview:** `OverviewViewModel` takes the chosen set from the settings; its input is `SatelliteChoice.forSearch(...)`'s TLEs; a change of the set restarts the calculation (it already restarts when the elements change). A line above the list says "Showing your N satellites" with a Change link, or the "No satellites chosen" message.
- **Planner:** the all-satellites search takes `SatelliteChoice.forSearch(...)`; the button label is "My satellites"; the "left out" count joins the existing skipped note; an empty list shows the "No satellites chosen" message and a link; a change of the set restarts a running search.

## What does not change
Prediction code, `PassStore` and `passes.json`, the selected satellite and its passes, the sky view, the map, alerts, the widget, the setup guide's satellite step (it uses the full list).

## Risks
- **An update changes the picker for existing testers.** With no saved list they get `{ISS}`, so the picker offers the ISS and the satellite they have selected (A6), not the 99 it offers now. That is David's rule (20 Nov 2026) and A6 keeps their current satellite visible and selected; the chooser, one tap from the picker, restores the rest.
- A chosen satellite missing from another keps source disappears when the source is switched; the "not in current elements" group in the chooser makes that visible (A5).
- An emptied list leaves the picker with only the selected satellite and "Choose satellites…"; the messages on the overview and planner explain it and link to the chooser. The passes screen stays usable because the selected satellite is always offered (A6).
