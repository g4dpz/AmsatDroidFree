# Design: Choose satellites

## Where things are today
- `AppUiState.satellites` is every satellite in the current elements; the Satellite picker on `PassesScreen` (`DropdownField`, `PassesControls`) offers all of them. `SatelliteOrder` sorts them.
- `OverviewViewModel` calculates for `viewModel.tles` (every satellite) with `OverviewCalculator`; the Contact planner's "All satellites" scope searches every satellite (`plannerAllSatellites` in the settings, `ContactPlannerViewModel`).
- Settings are in `SettingsRepository` over a `KeyValueStore`; routes are in `AppNavHost`.

## The change
### The list
- `CatnumList` (pure, `domain`): `parse(text): Set<Int>` (comma separated, trims, drops junk and duplicates), `format(set): String` (sorted, comma separated).
- `mySatellites` in `SettingsRepository`: `Setting<Set<Int>>` stored as one string under the key `mySatellites`, default empty.

### One rule, used three times
`SatelliteChoice` (pure, `domain`):

    fun offered(all: List<SatelliteItem>, chosen: Set<Int>, selectedCatnum: Int?): List<SatelliteItem>
    fun forSearch(all: List<TLE>, chosen: Set<Int>): Selection   // the TLEs to use and how many chosen ones are missing

`offered`: if `chosen` is empty, `all`; otherwise the items whose catalogue number is in `chosen`, plus the item for `selectedCatnum`, kept in `all`'s order. `forSearch`: if `chosen` is empty, every TLE and zero missing; otherwise the chosen ones that exist and the count of the rest. Both are tested (empty, all present, some missing, none present, selected outside the list, junk numbers).

### Screens
- **Picker:** `PassesControls` passes `SatelliteChoice.offered(state.satellites, chosen, state.selectedCatnum)` to the `DropdownField`, and the field gets an extra last menu entry "Choose satellites…" (`DropdownField` takes an optional `footer` label and callback; the footer does not select anything).
- **Chooser:** `ChooseSatellitesScreen` at `Routes.CHOOSE_SATELLITES`: a top bar with Cancel and Done, a search `OutlinedTextField`, a count line with Select all and Clear all, and a `LazyColumn` of checkbox rows (chosen first, then the rest in `SatelliteOrder`, then a "Not in the current elements" group). The draft is a `rememberSaveable` set, written to the setting on Done only.
- **Settings:** a "Choose satellites" row under the orbital elements section, showing "All satellites" or "N chosen", opening the chooser.
- **Overview:** `OverviewViewModel` takes the chosen set from the settings; its `tles` input is `SatelliteChoice.forSearch(...)`'s TLEs; a change of the set restarts the calculation (it already restarts when the elements change). A line above the list says "Showing your N satellites" with a Change link, only when the set is not empty.
- **Planner:** the all-satellites search takes `SatelliteChoice.forSearch(...)`; the button label switches by whether the set is empty; the "left out" count joins the existing skipped note; a change of the set restarts a running search.

## What does not change
Prediction code, `PassStore` and `passes.json`, the selected satellite and its passes, the sky view, the map, alerts, the widget, the setup guide's satellite step (it uses the full list).

## Risks
- A chosen satellite missing from another keps source disappears when the source is switched; the "not in current elements" group in the chooser makes that visible. Mitigation: A5.
- A selected satellite outside the list can look odd in the picker; A6 keeps it so the picker never contradicts the screen.
- Nothing is migrated, so the only compatibility risk is the new key being absent: absent means empty means everything.
