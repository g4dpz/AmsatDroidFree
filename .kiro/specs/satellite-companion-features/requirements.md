# Requirements: Satellite Companion Features

## Introduction

AmsatDroid Free (HamSatDroid) is now a Kotlin app targeting Android 16 (API 36) with a minimum of Android 11 (API 30). The orbit-prediction library is covered by a golden regression test. This spec modernises the app and adds features that use current Android capabilities and build on the existing pass-prediction code:

1. **Pass alerts**: notify the user before a chosen pass, with a live countdown.
2. **Compass-oriented sky view**: rotate the sky chart to match where the phone is pointing.
3. **Scheduled keps refresh**: keep Keplerian elements current without manual downloads.
4. **Jetpack Compose UI** with Material 3, dark theme and dynamic colour.
5. **Modern storage and settings**: replace Java-serialized files and the deprecated `android.preference` classes.
6. **Home-screen widget** showing the next pass.
7. **Adaptive layouts** for tablets, foldables and resizable windows.
8. **Localisation support** with per-app language.
9. **Red night mode** that keeps night vision, switched manually or by the ambient light sensor.
10. **Doppler-corrected frequencies** for uplink and downlink during a pass.
11. **All-satellite pass overview**: what is up now and what is coming, across every satellite.
12. **Aiming aid**: shows how far to turn and tilt the device to point at the satellite.
13. **Keep screen on** while tracking.
14. **Spoken alerts** (text-to-speech) for hands-free operation.
15. **Landscape dashboard** using the tablet's width.

### Target device

Primary target: **Samsung Galaxy Tab A7 Lite 8.7", model SM-T225 (LTE)**, the tablet used for development. Facts measured on the device unless stated:

- 1340 x 800 px TFT, 8.7", 60 Hz; reported at 213 dpi, which gives **600 dp wide in portrait** (exactly on the two-pane threshold) and about 1116 dp in landscape. The 600 dp two-pane threshold is reached; the 960 dp dashboard threshold is reached only in landscape. Other density settings would move these, so the thresholds live in one place and are confirmed on the device.
- Sensors: **accelerometer, magnetometer, light sensor**. There is **no gyroscope**; the platform still exposes fused rotation-vector, game-rotation-vector and geomagnetic-rotation-vector sensors (software-fused without a gyro), so the compass works but with lower accuracy and more smoothing.
- Location: GPS is present (`FEATURE_LOCATION_GPS`), along with network location. The app still detects GPS at run time so it also runs on devices without it.
- Android: ships with Android 12 (One UI 4, API 31). The app targets API 36 and supports API 30+. The Android 16 Live Update path therefore cannot be exercised on this device.
- MediaTek Helio P35 (mt6765); One UI split-screen and pop-up windows; Samsung battery management can restrict background work.
- Likely used outdoors for portable operation, possibly with no data connection.

The spec's original stated target was the Galaxy Tab A11 (SM-X130); it was changed to the SM-T225 actually used. The app must work well on this device, and still run on any Android 11+ phone or tablet.

### Decisions already made

- D1. Alert lead time defaults to **5 minutes**, user-selectable (5, 10, 15, 30).
- D2. Automatic keps refresh is **on by default, every 24 hours**, on any connected network, using the user's selected keps source.
- D5. The default keps source is **Celestrak amateur (JSON)**; see A14.
- D3. The compass is **on by default** when the device has a rotation-vector sensor.
- D4. All of the following are in scope: Compose, replacing the serialized `elems.bin` / `prefs.bin` files, replacing `android.preference`, the widget, adaptive two-pane layouts, translation support.

### Assumptions (please confirm or change)

- A1. Exact alarms are requested only if the user opts in; without them alerts still work using inexact timing.
- A2. The serialized files are replaced by small JSON files, **not Room**. The data is about 150 satellites and a handful of passes, which does not justify a database or annotation processing.
- A3. The settings screen is built in Compose on top of `SharedPreferences`, **not the AndroidX Preference library**, because that library is View-based and would be the one remaining non-Compose screen. The goal (no deprecated `android.preference`) is met either way. Existing preference keys are kept.
- A4. The widget shows the next pass for the satellite last selected in the app, with no widget configuration screen.
- A5. This spec delivers translation *support* (all strings externalised, locale configuration, in-app picker). Which languages to ship, and who translates them, is open. English is the only language delivered unless you nominate others.
- A7. The SM-T225 in portrait (600 dp) is treated as a "medium or larger" window, so the two-pane layout starts at 600 dp, not 840 dp. The real dp widths are verified on the device.
- A8. Automatic red night mode (by light sensor) is **off by default**; the user can choose the theme manually, and can switch the automatic mode on.
- A9. Spoken alerts are **off by default**.
- A10. Keep-screen-on while the sky or map screen is visible is **on by default**, because the screen is being watched during a pass; it can be turned off.
- A11. Doppler needs satellite frequencies, which are not in the keps. These come from the **OscarWatch transponder database** (`satellite_database.json`): a copy is **bundled with the app** as the fallback, and the app **syncs the published copy** from `https://tle.oscarwatch.org/satellite_database.json`. The database is **read-only in the app** — there is no in-app frequency editor and the user does not enter frequencies. Entries are matched to satellites by NORAD id. This replaces the earlier plan where the user entered and stored frequencies themselves. The database carries, per satellite, one or more transponder modes with downlink/uplink frequencies, downlink/uplink modulation modes (USB/LSB/CW/FM/FMN), a Doppler direction (NOR/REV, REV for inverting transponders), and an optional FM uplink CTCSS tone.
- A12. The aiming aid points along the device's **top edge** by default (as if the tablet is strapped to an antenna boom or held like a Yagi), with the back of the device as an option.
- A13. Out of scope: Bluetooth or USB radio and rotator control, and microSD or USB-C features. A "clock last synced" warning for offline use is a possible follow-up, not in this spec.
- A14. **Default keps source (decided).** The default is **Celestrak amateur, downloaded as JSON**. AMSAT stays available as an option. Reason: AMSAT's file is TLE-only, so it can never contain objects numbered above 99999 (checked: it has 97 satellites, highest catalogue number 71922, and no AO-129 or AO-130), while Celestrak's amateur group has 98 including three with 6-digit numbers. The two lists also differ by about 48 satellites each way, so switching source changes which satellites are listed.
- A15. Celestrak JSON is used rather than CSV. CSV is about a third of the size, but JSON is parsed by the `org.json` that Android already provides, and the amateur group is only about 41 KB.
- A6. Old serialized files are not migrated. Old satellite and pass files are discarded; the bundled keps load immediately and the first refresh restores current elements. Home location and other preferences are preserved.

## Glossary

- **OMM**: Orbit Mean-Elements Message, the CCSDS standard whose keywords Celestrak's JSON and CSV formats use.
- **GP data**: general perturbations orbital data, the mean elements used with SGP4.
- **AOS / LOS**: acquisition / loss of signal; start and end of a pass.
- **Keps**: Keplerian elements (TLEs).
- **Live Update**: Android 16 promoted ongoing notification shown on the lock screen and status bar.
- **Window size class**: Compose classification of available width (compact, medium, expanded).
- **Doppler shift**: change in received frequency caused by the satellite's motion towards or away from the observer. Range rate is positive when the satellite is moving away.
- **Downlink / uplink**: the satellite's transmit frequency (heard on the ground) and the frequency the satellite listens on (transmitted from the ground).
- **Azimuth / elevation (az/el)**: compass bearing and angle above the horizon.

## Requirements

### Requirement 1: Set and clear a pass alert

**User story:** As an operator, I want to be reminded shortly before a pass starts, so that I do not miss it.

#### Acceptance criteria

1. WHEN the user long-presses a pass in the pass list THEN the app SHALL toggle an alert for that pass and confirm with a message.
2. WHILE a pass has an alert THEN the pass list SHALL show that it has one.
3. WHEN an alert is set THEN the app SHALL store the satellite, AOS, LOS, maximum elevation and azimuths so that it survives app restarts.
4. WHEN the user clears an alert THEN the app SHALL cancel any scheduled alarms and remove any related notification.
5. WHEN a pass has already ended THEN the app SHALL NOT allow an alert to be set and SHALL prune stored alerts for ended passes.
6. WHEN the lead time preference is changed THEN existing alerts SHALL be rescheduled with the new lead time.
7. The default lead time SHALL be 5 minutes.

### Requirement 2: Deliver the alert

**User story:** As an operator, I want a timely notification with a countdown, so that I can get to my equipment.

#### Acceptance criteria

1. WHEN the alert time (AOS minus lead time) is reached THEN the app SHALL post a notification naming the satellite, AOS azimuth, maximum elevation and a countdown to AOS.
2. WHEN AOS is reached THEN the notification SHALL change to show the time remaining until LOS.
3. WHEN LOS is reached THEN the notification SHALL be removed.
4. WHERE the device runs Android 16 or later and the user allows promoted notifications THEN the notification SHALL request Live Update promotion.
5. WHERE Live Updates are unavailable THEN the notification SHALL fall back to a standard ongoing notification with the same content.
6. The countdown SHALL be rendered by the system (chronometer), so the app SHALL NOT need to run while it counts.
7. WHEN the device reboots, the app is updated, or the clock or time zone changes THEN the app SHALL reschedule all future alerts.
8. Notification text SHALL come from string resources (see Requirement 13).

### Requirement 3: Permissions and graceful degradation

**User story:** As a user, I want the app to explain what it needs and keep working if I say no.

#### Acceptance criteria

1. WHEN the user sets their first alert on Android 13 or later THEN the app SHALL request the notification permission.
2. IF the notification permission is denied THEN the app SHALL tell the user alerts cannot be shown and SHALL NOT schedule alarms.
3. IF exact alarms are not permitted THEN the app SHALL schedule inexact alarms and SHALL offer, once, to take the user to the system setting to allow exact alarms.
4. IF exact alarms later become permitted or revoked THEN the app SHALL reschedule alerts using the appropriate mode.
5. The app SHALL NOT request any permission the user has not triggered by using a feature.
6. The settings screen SHALL include a short note that some manufacturers (including Samsung) restrict background apps, with a button that opens the app's system settings page so the user can set battery usage to unrestricted. The app SHALL NOT use the battery-optimisation exemption request.

### Requirement 4: Compass-oriented sky view

**User story:** As an operator, I want the sky chart to turn as I turn, so that I can find the satellite in the real sky.

#### Acceptance criteria

1. WHEN the sky screen is visible and the compass is enabled THEN the chart SHALL rotate so the direction the device is facing is at the top.
2. The heading SHALL be corrected from magnetic to true north using the magnetic declination at the observer's position and the current date.
3. The heading SHALL account for display rotation.
4. The heading SHALL be smoothed so the chart does not jitter, and smoothing SHALL handle the 359° to 0° wrap-around correctly.
5. WHEN the sky screen is not visible THEN the app SHALL unregister the sensor listener.
6. The heading source SHALL be chosen in this order: rotation-vector sensor, geomagnetic rotation-vector sensor, then accelerometer plus magnetometer combined with `SensorManager.getRotationMatrix`. The app SHALL work on a device without a gyroscope.
7. IF none of these is available THEN the chart SHALL stay north-up and the compass control SHALL be hidden.
8. WHEN sensor accuracy is low THEN the screen SHALL show a short hint to calibrate the compass.
9. The compass SHALL be enabled by default; the user SHALL be able to switch it off and on, and the choice SHALL persist.
10. The chart orientation SHALL be correct when the device lies flat and when it is held upright, in portrait or landscape.
11. Pass track, AOS/LOS labels and current satellite marker SHALL remain correct relative to the compass (only the chart rotation changes, not the satellite maths).

### Requirement 5: Scheduled keps refresh

**User story:** As an operator, I want predictions to stay accurate without remembering to download keps, and I want the app to treat the data providers fairly.

#### Acceptance criteria

1. WHILE automatic refresh is enabled THEN the app SHALL download keps about once every 24 hours when the network is available.
2. Automatic refresh SHALL be enabled by default.
3. The app SHALL download over https from the user's selected keps source, using JSON (CCSDS OMM keywords) for Celestrak sources and TLE text for AMSAT.
4. WHEN a download succeeds and parses to at least one satellite THEN the app SHALL replace the stored elements atomically.
5. IF a download fails because of a network error, timeout or server error (HTTP 5xx) THEN the existing elements SHALL be kept and the work MAY be retried with backoff, for a small bounded number of attempts.
6. IF a download returns HTTP 301, 403, 404 or 429, or a response that is not valid data (including an error message returned with HTTP 200) THEN the existing elements SHALL be kept, the failure SHALL be recorded, and the app SHALL NOT retry until the next scheduled run or a user action.
7. The app SHALL NOT download the same source more than once every 2 hours. Manual and automatic downloads SHALL share this limit, and a manual request inside the window SHALL tell the user the elements are already current and when they were fetched.
8. The app SHALL identify itself with a User-Agent that names the app and version, SHALL use the `celestrak.org` host directly, SHALL download only the one selected source, and SHALL limit the response size and time.
9. WHEN the app is brought to the foreground after a background update THEN the satellite list SHALL reflect the new elements without losing the current selection.
10. The app SHALL record the time and source of the last successful update and the time and reason of the last failure, and show them in the About dialog.
11. The user SHALL be able to turn automatic refresh off in settings; turning it off SHALL cancel the scheduled work.
12. WHEN the user chooses a manual download from the menu THEN it SHALL follow the same rules and SHALL also record the update time and source.
13. Satellites with catalogue numbers above 99999 SHALL be loaded, stored, listed and predicted when the source provides them.
14. Orbital elements built from JSON SHALL produce the same prediction inputs as the same elements read from a TLE (see Requirement 7.4).
15. WHEN the selected satellite's elements are more than 30 days older than the prediction start time (now, or the start time the user chose) THEN the passes screen SHALL show a warning naming the satellite and the age in days, and suggesting a keps update from the menu. Predictions SHALL still be calculated. The age SHALL come from the satellite's own element epoch, not from the time the keps were downloaded.
16. The satellite list SHALL be ordered with the OSCAR-numbered satellites first, by their number (then by the prefix letters when two share a number, e.g. AO-7, UO-11, IO-26, FO-29, SO-50, CO-55), then the rest alphabetically by name (ignoring case and spaces). The OSCAR number is read from the satellite name. This ordering applies to the satellite chooser; the overview keeps its own order (up now by elevation, upcoming by AOS).

### Requirement 6: Remove the legacy SD-card keps lookup

**User story:** As a user on a modern device, I do not want to be told a file I never created cannot be found.

#### Acceptance criteria

1. WHEN the app starts with no stored elements THEN it SHALL load the bundled elements without showing a dialog.
2. The app SHALL NOT attempt to read `Download/nasabare.txt` and the "load from file" menu item SHALL be removed.
3. The first-run experience SHALL tell the user (in the About dialog or a one-time message) that the bundled elements may be out of date until the first refresh.

### Requirement 7: Safety of existing behaviour

#### Acceptance criteria

1. The golden regression test SHALL continue to pass unchanged.
2. Existing home location and other preference values SHALL be preserved across the upgrade (same preference keys and file).
3. The build SHALL complete with no Kotlin compiler warnings.
4. New logic that does not touch Android APIs (alert timing, heading maths, keps parsing and validation, download policy, TLE and JSON element conversion, chart geometry, map projection, JSON storage) SHALL have JVM unit tests. Converting a TLE to orbital elements and back SHALL reproduce every field the prediction code uses.
5. Each task SHALL leave the app building and runnable.
6. The app SHALL be verified on a Galaxy Tab A7 Lite SM-T225 (or an emulator configured with 1340 x 800 px at 213 dpi, plus a no-gyroscope sensor profile if available) before the work is considered done.

### Requirement 8: Jetpack Compose UI

**User story:** As a user, I want a modern, consistent interface that follows my system theme.

#### Acceptance criteria

1. The app SHALL provide the existing screens in Compose: pass list (main), sky view, ground track map, settings, and an About dialog.
2. The Compose screens SHALL first reach feature parity with the View screens (same data, same actions) before new features are added to them.
3. The app SHALL use Material 3, follow the system light/dark setting, and use dynamic colour on Android 12 and later.
4. The app SHALL be edge-to-edge using the Compose window-insets support, replacing the manual inset padding added earlier.
5. The app SHALL support predictive back.
6. Chart drawing (sky chart, world map) SHALL NOT allocate paths, paints or lists on every frame.
7. Periodic updates (map position every second, sky details every 5 seconds) SHALL be tied to the screen's lifecycle and stop when it is not visible.
8. Long-running work (pass calculation, keps download) SHALL run in coroutines off the main thread and survive configuration changes via a ViewModel.
9. Shared mutable statics (selected satellite, passes, ground station, predictor) SHALL be replaced by state held in ViewModels.
10. The old Activities (`ASDActivity`, `SkyView`, `GroundView`, `PrefHandling`) and layout XML files SHALL be removed once replaced.
11. Screens SHALL redraw only when their inputs change, so the app uses little power when nothing is moving, and animated content (compass-driven rotation, aiming display) SHALL render smoothly at the display's refresh rate (60 Hz on the SM-T225) without dropped frames.

### Requirement 9: Modern storage

**User story:** As a maintainer, I want stored data that does not break when a class changes.

#### Acceptance criteria

1. Stored keps SHALL be kept as normalised orbital elements in a JSON file (name, catalogue number up to 9 digits, epoch, and the orbital element values), not as TLE text, because TLE text cannot hold catalogue numbers above 99999. The file SHALL be rebuilt into the library's element objects on load.
2. Stored passes and alerts SHALL be kept as JSON.
3. All writes SHALL be atomic (write temporary file, then rename).
4. The app SHALL NOT read or write Java-serialized files; legacy `elems.bin` and `prefs.bin` SHALL be deleted on first run of the new version.
5. A corrupt or unreadable stored file SHALL result in falling back to bundled keps or an empty pass list, never a crash.
6. The serialization-compatibility constraints on `TLE` and `SatPassTime` field names MAY be relaxed afterwards.

### Requirement 10: Settings without `android.preference`

**User story:** As a user, I want to set my location and options reliably.

#### Acceptance criteria

1. The settings screen SHALL provide latitude, longitude, IARU locator, alert lead time, automatic keps refresh and keps source, and compass on/off.
2. Latitude SHALL be validated to ±90, longitude to ±180, and the locator against the Maidenhead pattern; invalid input SHALL be rejected with a message and the value reset as in the current app.
3. WHEN latitude or longitude changes THEN the locator SHALL be updated, and WHEN the locator changes THEN latitude and longitude SHALL be updated, exactly as now.
4. The existing preference keys (`homeLat`, `homeLon`, `homeLocator`) and preference file SHALL be used so existing values survive the upgrade.
5. Numeric values SHALL be stored and parsed with a fixed locale so a comma-decimal device locale cannot break them.
6. No class from the deprecated `android.preference` package SHALL remain in the app.
7. The current "use GPS / use network location" actions SHALL be kept and SHALL use single-shot location.
8. WHERE the device has no GPS (`FEATURE_LOCATION_GPS` absent) THEN the GPS action SHALL be hidden or disabled with an explanation, and the app SHALL point the user to entering the locator or coordinates. The SM-T225 reports GPS; the app must also run on a device without it.
9. IF network location is unavailable or returns nothing THEN the app SHALL say so and suggest entering the locator.
10. Entering the IARU locator SHALL be the most prominent way to set the location in settings, since many devices will have no usable automatic fix.

### Requirement 11: Home-screen widget

**User story:** As an operator, I want to see my next pass without opening the app.

#### Acceptance criteria

1. The app SHALL provide a home-screen widget showing the satellite name, next AOS time (local time and "in n min"), maximum elevation and AOS/LOS azimuths for the satellite last selected in the app.
2. IF no home location has been set THEN the widget SHALL tell the user to set it.
3. The widget SHALL refresh after a keps update, when the user changes satellite or location, and periodically (about every 30 minutes) while at least one widget is placed.
4. WHEN the last widget is removed THEN the periodic work SHALL be cancelled.
5. Tapping the widget SHALL open the app.
6. The widget SHALL calculate passes with the same prediction code as the app.
7. The widget SHALL follow light/dark and dynamic colour.
8. The widget SHALL be resizable, with a sensible minimum size and a larger layout when there is room (tablet home screens).

### Requirement 12: Adaptive layouts

**User story:** As a tablet or foldable user, I want to use the space I have.

#### Acceptance criteria

1. WHERE the window is at least 600 dp wide (medium or expanded width class) THEN the app SHALL show the pass list and the selected pass's sky view side by side. This includes the SM-T225 in portrait and landscape.
2. WHERE the window is narrower than 600 dp THEN the app SHALL show one screen at a time with navigation.
3. The app SHALL NOT lock screen orientation in the manifest and SHALL work in portrait, landscape and resized windows.
4. Rotating or resizing SHALL NOT lose the selected satellite, the calculated passes or the selected pass.
5. The app SHALL work correctly in Samsung split-screen, pop-up and DeX windows of any size, adapting live as the window is resized.
6. The ground track map SHALL scale to the space available and keep the world map aspect ratio.
7. The very wide landscape layout is specified in Requirement 20.

### Requirement 13: Localisation support

**User story:** As a non-English-speaking user, I want the app in my language.

#### Acceptance criteria

1. All user-visible text (screens, dialogs, messages, notifications, widget) SHALL come from string resources with placeholders instead of string concatenation.
2. Pass summaries SHALL be formatted from structured pass data in the UI layer, not from the English `SatPassTime.toString()` text.
3. Numbers, dates and times SHALL be formatted using the user's locale for display, and a fixed locale for storage.
4. The app SHALL declare supported languages in a locale configuration and let Android 13+ users choose a per-app language, with an in-app shortcut to that setting.
5. The English strings file SHALL be complete and organised so translators can work from it.
6. Languages to ship beyond English are open (see A5).

### Requirement 14: Red night mode

**User story:** As an operator observing at night, I want a display that does not ruin my night vision.

#### Acceptance criteria

1. The settings SHALL offer a theme choice of System, Light, Dark and Red night.
2. WHILE Red night is active THEN all screens, dialogs and charts SHALL use only red and black shades (no blue, green or white), with state conveyed by brightness, line style and labels instead of hue.
3. WHERE the device has an ambient light sensor, the settings SHALL offer "Switch to red night mode automatically", off by default.
4. WHILE automatic mode is on and the app is visible THEN the app SHALL switch to Red night when ambient light stays below a low threshold for a few seconds, and back when it stays above a higher threshold (hysteresis, so it does not flicker).
5. The light sensor SHALL be registered only while the app is visible and automatic mode is on.
6. Switching theme SHALL NOT restart the screen or lose state.
7. The app SHALL NOT change the system screen brightness.
8. Text and chart contrast in Red night SHALL remain readable (checked on the device).

### Requirement 15: Doppler-corrected frequencies

**User story:** As an operator, I want to know what frequency to tune and transmit on right now.

#### Acceptance criteria

1. Satellite transponder characteristics SHALL come from the OscarWatch transponder database (`satellite_database.json`): the app SHALL load a bundled copy as the fallback, sync the published copy from the remote URL, and prefer the synced copy when present (synced → bundled). The database is read-only; the app SHALL NOT provide a frequency editor and the user SHALL NOT enter frequencies.
2. Each satellite's characteristics SHALL comprise one or more transponder modes, each with a type label, a downlink frequency, an optional uplink frequency (absent or zero for a beacon / receive-only mode), downlink and uplink modulation modes (USB/LSB/CW/FM/FMN), a Doppler direction (NOR or REV, REV for an inverting transponder), and an optional FM uplink CTCSS tone. Database entries SHALL be matched to the app's satellites by NORAD id.
3. WHILE the sky screen shows a satellite that has one or more modes in the database THEN it SHALL show, for the selected mode, the mode type and modulation modes, the nominal frequencies, the Doppler-corrected downlink to tune and the corrected uplink to transmit, and the shift in Hz, updated every second. For a beacon / receive-only mode only the downlink SHALL be shown.
4. The corrected values SHALL use the same formulas as `PassPredictor.getDownlinkFreq` and `getUplinkFreq` (range rate in km/s, first-order Doppler), and a unit test SHALL confirm the two agree. For a REV (inverting) mode the correction direction of the uplink relative to the downlink SHALL reflect the inversion.
5. Frequencies SHALL be shown with 1 Hz resolution in a locale-appropriate format.
6. WHEN the satellite is below the horizon THEN the screen SHALL show the values with a note that the satellite is not currently visible.
7. IF a satellite has no modes in the database THEN the screen SHALL say so and SHALL otherwise behave as before.
8. The sync SHALL follow the same download safeguards as the keps refresh (https only, no redirects, a response size cap and timeouts, a User-Agent naming the app and version, and a minimum interval between fetches shared by automatic and manual runs). A failed or invalid sync SHALL keep the existing database (synced copy if present, otherwise bundled) and record the failure; a successful sync that parses to at least one satellite SHALL replace the synced copy atomically.
9. The app SHALL NOT edit, merge field-by-field, or let the user override the database; a sync SHALL replace the synced copy wholesale. (This is simpler than OscarWatch's own merge-and-conflict editor, which is out of scope here.)
10. Loading a missing, corrupt or wrong-schema database (bundled or synced) SHALL fall back to the other copy or to no characteristics, never a crash.

### Requirement 16: All-satellite pass overview

**User story:** As an operator, I want to see every satellite that is up now or coming soon, in one list.

#### Acceptance criteria

1. The app SHALL provide an Overview screen listing, for all satellites in the current keps, the satellites currently above the horizon (with azimuth and elevation) and the upcoming passes in the next 6, 12 or 24 hours (default 12), sorted by AOS.
2. Each pass row SHALL show satellite, time to or since AOS, AOS time, duration and maximum elevation.
3. The user SHALL be able to filter out passes whose maximum elevation is below a chosen minimum (0, 10, 20 or 30 degrees; default 0).
4. Passes SHALL be calculated in parallel off the main thread, and results SHALL appear as they complete without blocking the UI.
5. Each satellite SHALL be calculated with its own predictor and its own ground-station object, because the prediction code mutates the ground station.
6. Satellites that never rise above the horizon at the user's location SHALL be skipped silently.
7. Results SHALL be recalculated when the keps, location, time window or filter change, and the user SHALL be able to refresh manually.
8. Tapping a row SHALL select that satellite and pass and open the sky view; long-pressing a row SHALL toggle a pass alert (Requirement 1).
9. The calculation SHALL be cancellable and SHALL be cancelled when the screen is left.
10. The time taken for a full calculation on the SM-T225 SHALL be measured and recorded.

### Requirement 17: Aiming aid

**User story:** As an operator with a hand-held antenna, I want to be told which way to move to point at the satellite.

#### Acceptance criteria

1. The sky screen SHALL offer an Aim mode, off by default.
2. WHILE Aim mode is on THEN the screen SHALL show the device's pointing azimuth and elevation, the satellite's current azimuth and elevation, and the difference as plain instructions (for example "turn right 20°, raise 10°").
3. The pointing direction SHALL be the device's top edge by default, with the back of the device as a setting.
4. Pointing azimuth SHALL be corrected to true north (Requirement 4.2) and pointing elevation SHALL come from the device tilt.
5. WHEN both differences are within 5 degrees THEN the display SHALL show an "on target" state that does not rely on colour alone.
6. WHEN the satellite is below the horizon THEN the screen SHALL say when and where it rises instead of aiming instructions.
7. The screen SHALL show the sensor accuracy and a warning that magnets and metal near the device (including radio equipment) affect the compass.
8. Aim mode SHALL use the same sensors and lifecycle rules as the compass (Requirement 4.5).
9. The aiming maths SHALL be pure functions with unit tests.

### Requirement 18: Keep screen on while tracking

**User story:** As an operator, I do not want the screen to turn off in the middle of a pass.

#### Acceptance criteria

1. WHILE the sky screen or ground map screen is visible and the setting is on THEN the screen SHALL be kept on.
2. The setting SHALL be on by default and be available in settings.
3. WHEN the screen is left or the app goes to the background THEN the keep-on flag SHALL be cleared.

### Requirement 19: Spoken alerts

**User story:** As an operator with my hands on a radio, I want the app to tell me when a pass is starting.

#### Acceptance criteria

1. The settings SHALL include "Speak pass alerts", off by default.
2. WHEN enabled THEN the app SHALL speak an announcement at the alert time (satellite, minutes to AOS, AOS azimuth, maximum elevation), at AOS and at LOS, using text-to-speech.
3. Spoken text SHALL come from string resources and be spoken in the app's language.
4. The announcement SHALL briefly lower other audio and SHALL NOT take lasting audio focus.
5. IF text-to-speech is unavailable or fails THEN the notification SHALL still be shown and no error SHALL be displayed to the user.
6. The announcement SHALL be best effort and SHALL complete within the time the system allows a broadcast receiver to run.
7. Spoken alerts SHALL work when the app is not in the foreground.

### Requirement 20: Landscape dashboard

**User story:** As a tablet user in landscape, I want to see the passes, the sky and the map together.

#### Acceptance criteria

1. WHERE the window is at least 960 dp wide THEN the app SHALL show the pass list on the left (about 30%) and the sky chart and ground map in the remaining area.
2. In the remaining area the sky chart and map SHALL sit side by side when the area is wide enough for both to be a useful size, and stacked otherwise; the choice SHALL be made by a pure function of the area's width and height.
3. The map SHALL keep its 2:1 aspect ratio.
4. Both charts SHALL update live (map every second, sky details as on the sky screen) while visible.
5. The layout SHALL respond live to window resizing, including split-screen and DeX.
6. Between 600 dp and 960 dp the two-pane layout of Requirement 12 SHALL apply.
