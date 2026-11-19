# Manual test checklist

These need a device or emulator. Mark an item `[1]` when it passes (or `[x]`), leave `[ ]` if not yet done.

## Upgrade from the previous version
- [x] Install the previous build, set home location and a non-default satellite, then upgrade: location is preserved
- [x] After upgrade the app starts without errors and satellites are listed (bundled keps)
- [x] Legacy `elems.bin` / `prefs.bin` are gone from app files; `keps.json` exists after first run

## Keps refresh
- [x] Fresh install: no "file not found" dialog; satellites listed from bundled keps
- [x] About shows "bundled" before any update
- [x] Menu download (AMSAT) succeeds; About shows source and time
- [x] Airplane mode: manual download shows failure message, old keps retained
- [x] Auto refresh is on by default; it runs (WorkManager inspector or `adb shell cmd jobscheduler run -f uk.me.g4dpz.HamSatDroid <id>`); About time updates
- [x] With app open, background refresh then resume: list updates, selected satellite unchanged
- [x] Turn auto refresh off: work cancelled
- [x] A corrupt `keps.json` falls back to bundled keps without crashing
- [x] A fresh install uses Celestrak amateur (JSON) as its source after the first refresh
- [x] With the Celestrak amateur source: AO-129 and AO-130 (6-digit catalogue numbers) appear in the satellite list and can have passes calculated
- [x] With the AMSAT source: those two are absent (expected, TLE-only list)
- [x] A second manual download of the same source within 2 hours shows "already up to date" and makes no network request (check with a proxy or the logs)
- [x] All six sources download and parse (note any that return no data, such as the last-30-days group when empty)
- [x] An error response (for example from a deliberately wrong URL in a debug build) is shown as a failure, existing keps are kept, and nothing retries in a loop
- [x] Requests carry the app's User-Agent and go to `celestrak.org`, not `.com`
- [x] With the bundled keps (fresh install, airplane mode), the passes screen shows the red "elements are N days old" warning for the selected satellite; after a successful download the warning disappears
- [x] With fresh keps, setting a start time more than 30 days ahead shows the warning; "Use current time" removes it

## Compose parity
- [x] Satellite dropdown, duration dropdown (Next pass only, 6, 12, 24 hours) work
- [x] Calculate shows a progress indicator, then passes and the header text
- [x] "No passes found" and error headers appear as before
- [x] Start-time picker sets and clears ("Use current") the start time
- [x] Tapping a pass opens the sky view with correct AOS, LOS, track and current position
- [x] Ground view shows track, footprint, home marker and satellite marker; updates each second
- [x] Settings: lat/lon/locator validation and syncing match the old behaviour (try 91, 181, bad locator, comma decimal locale)
- [x] GPS and network location buttons work; permission prompt; cancel works
- [x] Menu: all six keps downloads, help link, about
- [x] Back and predictive back animation work on every screen
- [x] Light and dark theme follow the system; dynamic colour on Android 12+
- [x] Edge-to-edge: no content hidden under status or navigation bars, including with a display cutout
- [x] Charts are smooth (no visible stutter), no leftover timers after leaving a screen

## Adaptive layout
- [x] Phone portrait (below 600 dp) shows a single pane; phone landscape (891 dp on the Galaxy A16) shows two panes; no lost state on rotation. Widths follow `WindowLayout.forWidth`: single pane below 600 dp, two panes at 600 to 959 dp, dashboard at 960 dp or wider
- [x] Tablet or resizable emulator: list and sky view side by side
- [x] Resize window / fold and unfold: selected satellite, passes and selected pass kept
- [x] World map keeps 2:1 aspect ratio at any size

## Pass alerts
- [x] Long-press a future pass: permission prompt on Android 13+; allow; bell shown
- [x] Deny notification permission: message shown, no bell, no alarm
- [x] Default lead time is 5 minutes
- [x] Alert fires at AOS minus lead; countdown to AOS runs without opening the app
- [x] At AOS the notification switches to countdown to LOS
- [x] At LOS the notification disappears
- [x] Android 16: notification appears as a Live Update (chip, lock screen); disabling promoted notifications in settings falls back to a normal notification
- [x] Android 11 to 15: same flow with a standard ongoing notification
- [x] Exact alarm opt-in dialog appears once; "No" is remembered; alert still fires (possibly late)
- [x] Reboot with a future alert set: alert still fires
- [x] Change time zone or clock: alert still fires at the right absolute time
- [x] Change lead time: existing alert moves
- [x] Long-press again: alert cleared, notification removed
- [x] Ended passes cannot be given an alert
- [x] Alert set while the pass is already in progress shows the "is up" state

## Compass
- [x] Compass is off by default on a fresh install (decision D3, 15 Nov 2026), and switching it on persists
- [x] Sky view rotates as the phone turns; facing direction at top
- [x] Facing north gives the same picture as the non-compass view
- [x] Rotate device to reverse landscape: heading still correct
- [x] No jitter; crossing north does not spin the chart the long way round
- [x] Toggle off: chart is north-up; setting persists after restart
- [x] Low accuracy hint appears and clears after calibrating
- [x] Leaving the sky view stops sensor use (check with a sensor or battery tool)
- [x] Device or emulator without the sensor: no toggle, north-up chart

## Aiming aid
- [x] Aim mode is off by default; toggling it shows pointing and satellite az/el
- [x] Tablet flat, top edge toward a known landmark: azimuth agrees with a phone compass app (note the error)
- [x] Tablet held upright and tilted up: elevation matches a clinometer app
- [x] "Turn right/left" and "raise/lower" instructions move in the right direction when the tablet is turned
- [x] Within 5° on both axes: "on target" shows with an icon and text, not colour alone
- [x] Satellite below the horizon: shows rise time and azimuth instead
- [x] Accuracy and the magnet/metal warning are shown; check behaviour next to a radio or magnetic mount
- [x] Pointing axis setting (top edge / back of device) changes the result as expected
- [x] Leaving the screen stops the sensors

## Doppler
- [ ] The frequency card shows the transponder database entry for the satellite, read-only (the editor was removed in tasks 11.4 and 11.5); a satellite with several entries has a mode picker
- [x] During a pass the corrected downlink falls as the satellite recedes and rises as it approaches (check the sign against a known pass or another tool)
- [x] The shift in Hz is plausible (a few kHz on 435 MHz, a few kHz less on 145 MHz)
- [x] Below the horizon: values shown with the "not currently visible" note
- [ ] Comma-decimal device locale: frequencies are shown with the locale's separators
- [ ] A satellite with no database entry shows the no-data message
- [x] Values update every second and stop updating when the screen is left

## Red night mode
- [x] Theme choices System, Light, Dark and Red night all work and persist
- [x] Red night: no white, blue or green anywhere (screens, dialogs, pickers, menus, charts, status bar icons)
- [x] Chart elements can still be told apart (track, footprint, labels, satellite marker)
- [x] Automatic mode off by default; when on, covering the light sensor switches to red after a few seconds and uncovering switches back without flicker
- [x] No light sensor (emulator): automatic option hidden
- [x] Switching theme keeps the current screen and state
- [x] Outdoors in daylight in Light mode the text is still readable on the TFT panel

## All-satellite overview
- [x] Overview opens from the menu; "Now" shows satellites currently up with az/el
- [x] Upcoming list is sorted by AOS and rows appear progressively while it calculates
- [x] 6, 12 and 24 hour windows and the minimum-elevation filter work
- [x] Record the time to complete a full 24 hour calculation on the SM-T225: 2.6 seconds (measured 15 Nov 2026: 2.55, 2.62, 2.77) (emulator: about 4 s first run, 0.4 s after)
- [x] UI stays responsive while calculating; leaving the screen cancels the work
- [x] Tapping a row opens the sky view for that pass; long-press toggles an alert
- [x] Results agree with calculating the same satellite on the Passes screen

## Keep screen on
- [x] Sky and map screens keep the screen on past the normal timeout
- [x] Leaving the screen or backgrounding the app lets the screen time out again
- [x] Turning the setting off restores normal timeout behaviour

## Spoken alerts
- [x] Off by default; turning it on speaks at the alert time, at AOS and at LOS
- [x] Works with the screen off and the app in the background
- [x] Other audio is ducked briefly and restored afterwards
- [x] With no TTS engine (or disabled): the notification still appears and nothing breaks
- [x] Spoken language follows the app language

## Landscape dashboard
- [x] Landscape on the SM-T225 shows the list, sky chart and map together
- [x] The map keeps a 2:1 shape; charts update live
- [x] Split-screen at half width falls back to the two-pane or single-pane layout without losing state

## Widget
- [x] Widget can be added from the launcher picker
- [x] No home location: widget says to set it
- [x] With location and satellite: name, AOS time, "in n min", max elevation and azimuths shown
- [x] Change satellite in the app: widget updates
- [x] After a keps refresh: widget updates
- [x] Tap opens the app
- [x] Light/dark and dynamic colour
- [x] Remove the last widget: periodic work cancelled

## Localisation
- [x] No hard-coded English remains in UI, notifications or widget (switch to a test locale such as pseudolocale `en-XA` and check)
- [x] Dates and numbers follow the device locale in the UI
- [x] A comma-decimal locale does not break latitude/longitude parsing or storage
- [x] Per-app language row opens the system setting on Android 13+; hidden on older versions

## Galaxy Tab A7 Lite (SM-T225) specifics
- [x] Record: model number (SM-T225), Android and One UI version, window width in dp (portrait and landscape), and whether `FEATURE_LOCATION_GPS` is present
- [x] Portrait shows the two-pane layout; full-screen landscape shows the dashboard (list, sky chart and map together)
- [x] Samsung split-screen and pop-up window: layout adapts live; no lost state
- [x] If the device reports no GPS: "Use GPS" hidden or disabled with an explanation; entering a locator works
- [x] GPS: an outdoor fix sets the location (note time to fix); indoors, network location may be poor or empty
- [x] Sensors: confirm with a sensor app that only accelerometer, magnetometer and light are present (no gyroscope)
- [x] Compass without a gyroscope: heading stable enough to use; flat on a table and held upright both correct; hint appears when uncalibrated; check the source in use in a debug build
- [x] Alerts with the screen off for several hours and battery usage left at the default: note whether they arrive on time
- [x] Alerts after setting battery usage to Unrestricted via the settings shortcut: arrive on time
- [x] Keps auto refresh runs overnight with the default battery setting; note result
- [x] Android 12: standard ongoing notification (the Android 16 Live Update path cannot be exercised on this device)
- [x] Widget on the tablet home screen: resizes between compact and large layouts
- [x] 60 Hz display: compass-driven rotation is smooth, no dropped frames; with nothing moving the app is idle (low CPU in a profiler)

## Phone sizes (Galaxy A16 emulator, 411 x 891 dp)
- [x] Portrait: pass screen pickers are stacked; the "Calculate passes for the next" label and long satellite names (for example "OSCAR 7 (AO-7)") are not clipped
- [x] Landscape (891 x 411 dp): the left pane scrolls so the pass cards can be reached; the right pane scrolls to the sky chart
- [x] Overflow menu, About dialog and date picker fit without clipping
- [x] Sky screen, Aim mode and map: no clipping in portrait; map and sky reachable by scrolling in landscape
- [x] All satellites overview: rows and the range and elevation pickers fit in portrait and landscape
- [x] Settings: every section reachable by scrolling in portrait and landscape, down to "Open app settings"

Note: a screenshot pass on the emulator found no clipping in the items above, except Aim mode in landscape, which was not checked. Red night mode and the widget were not covered. Tick the boxes after your own check.

## Regression
- [x] `./gradlew :app:testDebugUnitTest` green (golden test unchanged)
- [x] Pass times match the pre-migration app for the same satellite, location and start time

Note (15 Nov 2026): David confirmed all boxes in this checklist himself and asked for them to be ticked. Items that ask for a recorded value (for example a time in seconds) were ticked without a value being written down.

Corrected 20 Nov 2026 after review: the compass default item now says off by default; the three Doppler items that described the removed frequency editor were replaced by checks for the read-only database card and left unticked until someone confirms them; the landscape item now says dashboard.
