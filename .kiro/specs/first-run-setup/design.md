# Design: First-run Setup

## Overview

A four-step guide shown once, plus a guard and a banner so the default location can never produce silent, wrong passes. Everything reuses what exists: `LocatorSync` and `LocationFetcher` for location, `KepsRepository` and `AppViewModel` for keps, `AlertController`'s permission handling for notifications, `SettingsRepository` for storage.

New code is small: one pure rule (`HomeLocation.isSet`), one pure policy (`SetupPolicy`), one new setting, one extracted composable, the guide screen, a banner and a new pass heading.

## Findings that shape the design (version 2.0.0)

| Where | What it does today |
|---|---|
| `Settings.kt` | `DEFAULT_LATLON = "0"`, `DEFAULT_LOCATOR = "JJ00aa"`; there is no "set" flag |
| `AppViewModel.calculate()` | accepts 0, 0 as valid; only a non-numeric value gives `PassHeader.InvalidLocation` |
| `SettingsScreen.kt` | the location UI (drafts, commit, GPS/network fetch, permission launcher) is about 150 lines inside the screen |
| `AppNavHost` | start destination is `Routes.PASSES` or the route requested by a notification or widget |
| `AlertController.kt` | asks for `POST_NOTIFICATIONS` (API 33+) the first time an alert is set |
| `WidgetUpdater.compute` | returns `NoLocation` only when the stored text is not a number |

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

## Guard and heading

`PassHeader` gains `data object LocationNotSet`. `AppViewModel.calculate()` checks `HomeLocation.isSet` first, before parsing, and sets that heading instead of calculating. `PassFormatting.text()` maps it to a string. `WidgetUpdater.compute` returns `WidgetState.NoLocation` when the location is unset. `OverviewViewModel` exposes the same condition so `OverviewScreen` shows the shared "set your location" panel.

## Shared location editor

`SettingsScreen` has its location section inline. It is extracted unchanged into `ui/components/LocationEditor.kt`:

```kotlin
@Composable
fun LocationEditor(settings: SettingsRepository, locationFetcher: LocationFetcher, modifier: Modifier = Modifier)
```

It owns the drafts, `LocatorSync` commits, the fetch state and the permission launcher. `SettingsScreen` calls it where the section was; the guide calls it on step 1. The change is a pure move, verified by the unchanged `LocatorSyncTest` and a manual pass over Settings.

## The guide

New route `Routes.SETUP`. `AppNavHost` picks the start destination:

```
startRoute = requested route (notification or widget) if present
             else SETUP if SetupPolicy.shouldShowAtStart(...)
             else PASSES
```

`SetupScreen(viewModel, settings, locationFetcher, onFinished)`:

- Step number in `rememberSaveable` (survives rotation and process death at the step start, requirement 7).
- A common frame (`SetupFrame`): title as a heading, "Step N of 4", scrolling content, and a bottom row with Back, Skip and Next/Done in fixed positions. Content is centred with a 600 dp maximum width.
- Step 1 `LocationEditor` plus a summary line; Next enabled when `HomeLocation.isSet`.
- Step 2 keps: collects `AppViewModel.state` (`keps`, `downloadingKeps`) and `events` for the "already up to date" message; switch and source dropdown bind to `settings.autoKepsRefresh` and `settings.kepsSource`; "Update now" calls `viewModel.refreshKeps()`.
- Step 3 alerts: permission launcher for `POST_NOTIFICATIONS` (guarded for API 33), lead-time dropdown bound to `settings.alertLeadMinutes`.
- Step 4 satellite: `DropdownField` over `state.satellites`; Done calls `viewModel.selectSatellite(...)`, sets `setupFinished`, navigates to `PASSES` popping `SETUP`, and calls `viewModel.calculate()` when the location is set.
- Skip sets `setupFinished` and navigates to `PASSES`.

The "Setup guide" menu item navigates to `SETUP` without touching `setupFinished` until the user finishes or skips.

## The banner

`LocationNotSetBanner(onSetUp)` is a `Card` in `PassesControls` above the pickers, shown while `!HomeLocation.isSet` for the live `homeLat` and `homeLon` flows. It uses `errorContainer` colours plus an icon and text, so colour is not the only signal. Its button navigates to `SETUP`.

## Strings

New resources (English): `setup_title_*` (four), `setup_step_n_of_m`, `setup_next`, `setup_back`, `setup_skip`, `setup_done`, `setup_location_why`, `setup_location_needed`, `setup_keps_why`, `setup_alerts_why`, `setup_alerts_allow`, `setup_alerts_denied`, `setup_satellite_why`, `menu_setup_guide`, `banner_location_not_set`, `banner_set_up`, `header_location_not_set`.

## Risks

- **Moving the location UI** could change Settings behaviour. Mitigation: move without edits, and check Settings on a device before and after (manual checklist).
- **A user really at 0.0, 0.0** sees the banner. Accepted (A1).
- **Process death mid-guide** reopens at the start of the step, not mid-field; the typed text was committed when the field lost focus, so little is lost.

## Testing

- Unit: `HomeLocationTest` (zero, `"0.0"`, `"0,0"`, `"-0.0"`, one zero one not, whitespace, blank, non-numeric, comma decimals), `SetupPolicyTest` (all four combinations), `SettingsRepositoryTest` (`setupFinished` default and round trip), `AppViewModelTest` (unset gives `LocationNotSet` and no calculation; set calculates; invalid text still `InvalidLocation`), `WidgetUpdaterTest` (unset gives `NoLocation`), `OverviewViewModelTest` (unset gives the not-set state).
- Manual: `manual-test-checklist.md` in this folder, on a fresh install (`adb shell pm clear`), an upgrade over existing data, phone and tablet, landscape, 200 percent font, TalkBack, red night.
