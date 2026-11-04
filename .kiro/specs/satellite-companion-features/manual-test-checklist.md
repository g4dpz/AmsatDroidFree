# Manual test checklist

These need a device or emulator. Mark an item `[1]` when it passes (or `[x]`), leave `[ ]` if not yet done.

## Upgrade from the previous version
- [ ] Install the previous build, set home location and a non-default satellite, then upgrade: location is preserved
- [ ] After upgrade the app starts without errors and satellites are listed (bundled keps)
- [ ] Legacy `elems.bin` / `prefs.bin` are gone from app files; `keps.json` exists after first run

## Keps refresh
- [ ] Fresh install: no "file not found" dialog; satellites listed from bundled keps
- [ ] About shows "bundled" before any update
- [ ] Menu download (AMSAT) succeeds; About shows source and time
- [ ] Airplane mode: manual download shows failure message, old keps retained
- [ ] Auto refresh is on by default; it runs (WorkManager inspector or `adb shell cmd jobscheduler run -f uk.me.g4dpz.HamSatDroid <id>`); About time updates
- [ ] With app open, background refresh then resume: list updates, selected satellite unchanged
- [ ] Turn auto refresh off: work cancelled
- [ ] A corrupt `keps.json` falls back to bundled keps without crashing
- [ ] A fresh install uses Celestrak amateur (JSON) as its source after the first refresh
- [ ] With the Celestrak amateur source: AO-129 and AO-130 (6-digit catalogue numbers) appear in the satellite list and can have passes calculated
- [ ] With the AMSAT source: those two are absent (expected, TLE-only list)
- [ ] A second manual download of the same source within 2 hours shows "already up to date" and makes no network request (check with a proxy or the logs)
- [ ] All six sources download and parse (note any that return no data, such as the last-30-days group when empty)
- [ ] An error response (for example from a deliberately wrong URL in a debug build) is shown as a failure, existing keps are kept, and nothing retries in a loop
- [ ] Requests carry the app's User-Agent and go to `celestrak.org`, not `.com`
- [ ] With the bundled keps (fresh install, airplane mode), the passes screen shows the red "elements are N days old" warning for the selected satellite; after a successful download the warning disappears
- [ ] With fresh keps, setting a start time more than 30 days ahead shows the warning; "Use current time" removes it

## Compose parity
- [ ] Satellite dropdown, duration dropdown (Next pass only, 6, 12, 24 hours) work
- [ ] Calculate shows a progress indicator, then passes and the header text
- [ ] "No passes found" and error headers appear as before
- [ ] Start-time picker sets and clears ("Use current") the start time
- [ ] Tapping a pass opens the sky view with correct AOS, LOS, track and current position
- [ ] Ground view shows track, footprint, home marker and satellite marker; updates each second
- [ ] Settings: lat/lon/locator validation and syncing match the old behaviour (try 91, 181, bad locator, comma decimal locale)
- [ ] GPS and network location buttons work; permission prompt; cancel works
- [ ] Menu: all six keps downloads, help link, about
- [ ] Back and predictive back animation work on every screen
- [ ] Light and dark theme follow the system; dynamic colour on Android 12+
- [ ] Edge-to-edge: no content hidden under status or navigation bars, including with a display cutout
- [ ] Charts are smooth (no visible stutter), no leftover timers after leaving a screen

## Adaptive layout
- [ ] Phone portrait and landscape: single pane, no lost state on rotation
- [ ] Tablet or resizable emulator: list and sky view side by side
- [ ] Resize window / fold and unfold: selected satellite, passes and selected pass kept
- [ ] World map keeps 2:1 aspect ratio at any size

## Pass alerts
- [ ] Long-press a future pass: permission prompt on Android 13+; allow; bell shown
- [ ] Deny notification permission: message shown, no bell, no alarm
- [ ] Default lead time is 5 minutes
- [ ] Alert fires at AOS minus lead; countdown to AOS runs without opening the app
- [ ] At AOS the notification switches to countdown to LOS
- [ ] At LOS the notification disappears
- [ ] Android 16: notification appears as a Live Update (chip, lock screen); disabling promoted notifications in settings falls back to a normal notification
- [ ] Android 11 to 15: same flow with a standard ongoing notification
- [ ] Exact alarm opt-in dialog appears once; "No" is remembered; alert still fires (possibly late)
- [ ] Reboot with a future alert set: alert still fires
- [ ] Change time zone or clock: alert still fires at the right absolute time
- [ ] Change lead time: existing alert moves
- [ ] Long-press again: alert cleared, notification removed
- [ ] Ended passes cannot be given an alert
- [ ] Alert set while the pass is already in progress shows the "is up" state

## Compass
- [ ] Compass is on by default
- [ ] Sky view rotates as the phone turns; facing direction at top
- [ ] Facing north gives the same picture as the non-compass view
- [ ] Rotate device to reverse landscape: heading still correct
- [ ] No jitter; crossing north does not spin the chart the long way round
- [ ] Toggle off: chart is north-up; setting persists after restart
- [ ] Low accuracy hint appears and clears after calibrating
- [ ] Leaving the sky view stops sensor use (check with a sensor or battery tool)
- [ ] Device or emulator without the sensor: no toggle, north-up chart

## Aiming aid
- [ ] Aim mode is off by default; toggling it shows pointing and satellite az/el
- [ ] Tablet flat, top edge toward a known landmark: azimuth agrees with a phone compass app (note the error)
- [ ] Tablet held upright and tilted up: elevation matches a clinometer app
- [ ] "Turn right/left" and "raise/lower" instructions move in the right direction when the tablet is turned
- [ ] Within 5° on both axes: "on target" shows with an icon and text, not colour alone
- [ ] Satellite below the horizon: shows rise time and azimuth instead
- [ ] Accuracy and the magnet/metal warning are shown; check behaviour next to a radio or magnetic mount
- [ ] Pointing axis setting (top edge / back of device) changes the result as expected
- [ ] Leaving the screen stops the sensors

## Doppler
- [ ] Add a downlink and uplink for a satellite; they persist after restart
- [ ] During a pass the corrected downlink falls as the satellite recedes and rises as it approaches (check the sign against a known pass or another tool)
- [ ] The shift in Hz is plausible (a few kHz on 435 MHz, a few kHz less on 145 MHz)
- [ ] Below the horizon: values shown with the "not currently visible" note
- [ ] Comma-decimal device locale: entering frequencies still works
- [ ] Edit and delete entries; satellite with no entries shows the add prompt
- [ ] Values update every second and stop updating when the screen is left

## Red night mode
- [ ] Theme choices System, Light, Dark and Red night all work and persist
- [ ] Red night: no white, blue or green anywhere (screens, dialogs, pickers, menus, charts, status bar icons)
- [ ] Chart elements can still be told apart (track, footprint, labels, satellite marker)
- [ ] Automatic mode off by default; when on, covering the light sensor switches to red after a few seconds and uncovering switches back without flicker
- [ ] No light sensor (emulator): automatic option hidden
- [ ] Switching theme keeps the current screen and state
- [ ] Outdoors in daylight in Light mode the text is still readable on the TFT panel

## All-satellite overview
- [ ] Overview opens from the menu; "Now" shows satellites currently up with az/el
- [ ] Upcoming list is sorted by AOS and rows appear progressively while it calculates
- [ ] 6, 12 and 24 hour windows and the minimum-elevation filter work
- [ ] Record the time to complete a full 24 hour calculation on the Tab A11: ______ seconds (emulator: about 4 s first run, 0.4 s after)
- [ ] UI stays responsive while calculating; leaving the screen cancels the work
- [ ] Tapping a row opens the sky view for that pass; long-press toggles an alert
- [ ] Results agree with calculating the same satellite on the Passes screen

## Keep screen on
- [ ] Sky and map screens keep the screen on past the normal timeout
- [ ] Leaving the screen or backgrounding the app lets the screen time out again
- [ ] Turning the setting off restores normal timeout behaviour

## Spoken alerts
- [ ] Off by default; turning it on speaks at the alert time, at AOS and at LOS
- [ ] Works with the screen off and the app in the background
- [ ] Other audio is ducked briefly and restored afterwards
- [ ] With no TTS engine (or disabled): the notification still appears and nothing breaks
- [ ] Spoken language follows the app language

## Landscape dashboard
- [ ] Landscape on the Tab A11 shows the list, sky chart and map together
- [ ] The map keeps a 2:1 shape; charts update live
- [ ] Split-screen at half width falls back to the two-pane or single-pane layout without losing state

## Widget
- [ ] Widget can be added from the launcher picker
- [ ] No home location: widget says to set it
- [ ] With location and satellite: name, AOS time, "in n min", max elevation and azimuths shown
- [ ] Change satellite in the app: widget updates
- [ ] After a keps refresh: widget updates
- [ ] Tap opens the app
- [ ] Light/dark and dynamic colour
- [ ] Remove the last widget: periodic work cancelled

## Localisation
- [ ] No hard-coded English remains in UI, notifications or widget (switch to a test locale such as pseudolocale `en-XA` and check)
- [ ] Dates and numbers follow the device locale in the UI
- [ ] A comma-decimal locale does not break latitude/longitude parsing or storage
- [ ] Per-app language row opens the system setting on Android 13+; hidden on older versions

## Galaxy Tab A11 specifics
- [ ] Record: model number (SM-X130 Wi-Fi expected), Android and One UI version, window width in dp (portrait and landscape), and whether `FEATURE_LOCATION_GPS` is present (Samsung and GSMArena disagree)
- [ ] Portrait and landscape both show the two-pane layout
- [ ] Samsung split-screen and pop-up window: layout adapts live; no lost state
- [ ] DeX (if available): app is resizable and usable with mouse and keyboard
- [ ] If the device reports no GPS: "Use GPS" hidden or disabled with an explanation; entering a locator works
- [ ] If the device reports GPS: an outdoor fix sets the location (note time to fix); Wi-Fi-only means network location may be poor or empty indoors
- [ ] Sensors: confirm with a sensor app that only accelerometer, magnetometer and light are present (no gyroscope)
- [ ] Compass without a gyroscope: heading stable enough to use; flat on a table and held upright both correct; hint appears when uncalibrated; check the source in use in a debug build
- [ ] Alerts with the screen off for several hours and battery usage left at the default: note whether they arrive on time
- [ ] Alerts after setting battery usage to Unrestricted via the settings shortcut: arrive on time
- [ ] Keps auto refresh runs overnight with the default battery setting; note result
- [ ] Android 15: standard ongoing notification; after the Android 16 update: Live Update behaviour on One UI 8
- [ ] Widget on the tablet home screen: resizes between compact and large layouts
- [ ] 90 Hz display: compass-driven rotation is smooth, no dropped frames; with nothing moving the app is idle (low CPU in a profiler)

## Regression
- [ ] `./gradlew :app:testDebugUnitTest` green (golden test unchanged)
- [ ] Pass times match the pre-migration app for the same satellite, location and start time
