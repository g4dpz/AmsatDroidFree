# Design: First-run Setup

## Overview

A two-step guide (Location, First satellite) shown once, plus a guard and a banner so the default location can never produce silent, wrong passes. Everything reuses what exists: `LocatorSync` and `LocationFetcher` for location, `AppViewModel` for the satellite list and the first calculation, `SettingsRepository` for storage. The keps and alerts steps of the first draft were dropped (requirements O2).

New code is small: one pure rule (`HomeLocation.isSet`), one pure policy (`SetupPolicy`), one new setting, one extracted composable, the guide screen, a banner and a new pass heading.

## Findings that shape the design (version 2.0.0, updated for 2.0.4)

| Where | What it does today |
|---|---|
| `Settings.kt` | `DEFAULT_LATLON = "0"`, `DEFAULT_LOCATOR = "JJ00aa"`; there is no "set" flag |
| `AppViewModel.calculate()` | since 2.0.3 checks `HomeLocation.isSet`: unset gives `PassHeader.LocationNotSet`, cancels a running calculation and clears stored passes; non-numeric text still gives `PassHeader.InvalidLocation` |
| `SettingsScreen.kt` | the location UI (drafts, commit, GPS/network fetch, permission launcher) sits inside the screen, in the section under `settings_section_location` (about lines 231 to 275 of 424), plus the `CommitField` helper |
| `AppNavHost` | start destination is `Routes.PASSES` or the route requested by a notification or widget |
| `AlertController.kt` | asks for `POST_NOTIFICATIONS` (API 33+) the first time an alert is set |
| `WidgetUpdater.compute` | since 2.0.3 also returns `NoLocation` when the location is unset |
| Overflow menu (`PassesScreen.kt`) | has no "Settings" item: "Set home location" opens the Settings screen (a tester could not find Settings; see requirements O1) |
| Messages | `header_location_not_set` and `overview_no_location` say "Set it in Settings"; reword under O1 |

## Domain and settings

```kotlin
// domain/HomeLocation.kt
object HomeLocation {
    /** True unless both values are missing, non-numeric or zero (the stored defaults). */
    fun isSet(latText: String, lonText: String): Boolean {
        val lat = LocationText.toDegrees(latText)
        val lon = LocationText.toDegrees(lonText)
        return !(lat == null || lon == null || (lat == 0.0 && lon == 0.0))
    }
}
```

A non-numeric value counts as unset here, but `AppViewModel` keeps `PassHeader.InvalidLocation` for it so the existing message and tests stay.

```kotlin
// data/Settings.kt
val setupFinished = Setting(store.getBoolean(SETUP_FINISHED, false)) { store.putBoolean(SETUP_FINISHED, it) }
const val SETUP_FINISHED = "setupFinished"
```

```kotlin
// domain/SetupPolicy.kt
object SetupPolicy {
    /** The guide opens by itself only for a user who has not set a location and has not finished or skipped it. */
    fun shouldShowAtStart(locationSet: Boolean, finished: Boolean): Boolean = !locationSet && !finished
}
```

Upgrade path: an existing install has a non-zero location, so `shouldShowAtStart` is false and nothing is stored. An existing install that never set a location (rare) sees the guide once.

## Guard and heading (shipped in 2.0.3, PR #21)

`PassHeader` gains `data object LocationNotSet`. `AppViewModel.calculate()` parses the stored latitude and longitude first; only when both are numbers does it check `HomeLocation.isSet`, and when that is false it sets this heading instead of calculating. Non-numeric text therefore still gives `PassHeader.InvalidLocation`. `PassFormatting.text()` maps it to a string. `WidgetUpdater.compute` returns `WidgetState.NoLocation` when the location is unset. `OverviewViewModel` exposes the same condition (`locationValid`) and `OverviewScreen` shows a message. Still to do: the button on that message that opens the guide, added with the banner. As built, the guard also cancels a calculation already running for the old location (checked by generation) and calls `passStore.clear()` so stale passes do not return after a restart.

Still to build (requirement 6.6, changed after review): `AppViewModel.initialLoad()` restores `passStore.load()` without looking at the location, so passes saved for 0, 0 by version 2.0.2 or earlier would appear under the banner. It checks `HomeLocation.isSet` first; when the location is unset it does not restore them and calls `passStore.clear()`. A non-numeric location (`InvalidLocation`) is treated the same way, since such passes cannot be trusted either.

## Shared location editor

`SettingsScreen` has its location section inline. It is extracted unchanged into `ui/components/LocationEditor.kt`:

```kotlin
@Composable
fun LocationEditor(settings: SettingsRepository, locationFetcher: LocationFetcher, modifier: Modifier = Modifier)
```

It owns the drafts, `LocatorSync` commits, the fetch state and the permission launcher. `SettingsScreen` calls it where the section was; the guide calls it on step 1. The change is a pure move, verified by the unchanged `LocatorSyncTest` and a manual pass over Settings.

## The guide

New route `Routes.SETUP`. `AppNavHost` picks the start destination.

Today `MainActivity` turns a missing `EXTRA_ROUTE` into `Routes.PASSES` before calling `AppNavHost`, and the widget asks for `Routes.PASSES` explicitly (`NextPassWidget.kt`), so the host cannot tell a normal launch from a widget tap. `MainActivity` therefore passes the request through unchanged as a nullable `requestedRoute = intent.getStringExtra(EXTRA_ROUTE)`, and `AppNavHost` takes `requestedRoute: String?` instead of `startRoute: String`:

```
startRoute = requestedRoute (notification or widget) if not null
             else SETUP if SetupPolicy.shouldShowAtStart(...)
             else PASSES
```

`SetupScreen(viewModel, settings, locationFetcher, onFinished)`:

- The steps are a list (`SetupStep` entries, each with a title, content and its own Next condition), so the step count and "Step N of M" come from the list and a step can be added later (for example an analytics consent step, if that work resumes) without changing the frame. Two entries in this version (requirements O2).
- Step index in `rememberSaveable` (survives rotation and process death at the step start, requirement 7).
- A common frame (`SetupFrame`): title as a heading, "Step N of M" (M is the size of the step list), scrolling content, and a bottom row with Back, Skip and Next/Done in fixed positions. Content is centred with a 600 dp maximum width.
- Step 1 `LocationEditor` plus a summary line; Next enabled when `HomeLocation.isSet`.
- Step 2 satellite: `DropdownField` over `state.satellites`; Done calls `viewModel.selectSatellite(...)`, sets `setupFinished`, leaves the guide for the passes screen (below), and calls `viewModel.calculate()` when the location is set.
- Skip sets `setupFinished` and leaves the guide (below).
- Leaving the guide (requirement 7.5). The guide is either the start destination (fresh install, nothing behind it) or was navigated to from the passes screen, the overview or the banner. `AppNavHost` already has `NavController.popOrGoHome(route)`: pop when something is behind, otherwise replace with `PASSES`. Skip and Back on step 1 call `popOrGoHome(Routes.SETUP)`. Done calls `popBackStack(Routes.PASSES, inclusive = false)` and, when that returns false (the guide was the start destination), navigates to `PASSES` with `popUpTo(Routes.SETUP) { inclusive = true }`. Either way only one passes screen is on the stack, and Back from it leaves the app instead of reopening the guide.

The "Setup guide" menu item navigates to `SETUP` without touching `setupFinished` until the user finishes or skips.

## The banner

`LocationNotSetBanner(onSetUp)` is a `Card` in `PassesControls` above the pickers, shown while `!HomeLocation.isSet` for the live `homeLat` and `homeLon` flows. It uses `errorContainer` colours plus an icon and text, so colour is not the only signal. Its button navigates to `SETUP` at the Location step. The overview's "not set" message gets the same button.

## Menu and messages (requirements O1, decided: option a; requirement 10)

The menu item "Set home location" becomes "Settings" (string `menu_set_home_location` renamed to `menu_settings`), a "Setup guide" item is added, and `header_location_not_set` and `overview_no_location` are reworded to point at the banner and guide rather than at Settings. The tester script (`docs/tester-test-script.txt`, which since 2.0.4 explains "Set home location opens the Settings screen") and the README are updated to the new names in the same release.

## Strings

New resources (English): `setup_title_location`, `setup_title_satellite`, `setup_step_n_of_m`, `setup_next`, `setup_back`, `setup_skip`, `setup_done`, `setup_location_why`, `setup_location_needed`, `setup_satellite_why`, `menu_setup_guide`, `menu_settings`, `banner_location_not_set`, `banner_set_up`, `header_location_not_set`.

## Risks

- **Moving the location UI** could change Settings behaviour. Mitigation: move without edits, and check Settings on a device before and after (manual checklist).
- **A user really at 0.0, 0.0** sees the banner. Accepted (A1).
- **Process death mid-guide.** When the system ends the process in the background, `rememberSaveable` brings the guide back at the start of the step, not mid-field; the typed text was committed when the field lost focus, so little is lost. A force stop discards that state, so the app starts afresh by the start-up rule (requirement 7.2); no durable step store is added for that case.

## Testing

- Unit: `HomeLocationTest` (zero, `"0.0"`, `"0,0"`, `"-0.0"`, one zero one not, whitespace, blank, non-numeric, comma decimals), `SetupPolicyTest` (all four combinations), `SettingsRepositoryTest` (`setupFinished` default and round trip), `AppViewModelTest` (unset gives `LocationNotSet` and no calculation; set calculates; invalid text still `InvalidLocation`), `WidgetUpdaterTest` (unset gives `NoLocation`), `OverviewViewModelTest` (unset gives the not-set state).
- Manual: `manual-test-checklist.md` in this folder, on a fresh install (`adb shell pm clear`), an upgrade over existing data, phone and tablet, landscape, 200 percent font, TalkBack, red night.
