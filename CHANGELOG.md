# Changelog

What changed in each version of AmsatDroid, newest first. Each entry says what a user would notice; it is not a list of every commit. Version numbers follow [semantic versioning](https://semver.org/): fixes bump the last number, new functionality the middle one. `versionCode` is Google Play's ordering number and goes up with every upload.

The bullet points of the newest entry are also what goes into the Play Console release notes (500 characters per language, plain text, without the version heading or the `###` lines), so keep each release's bullets short, under about 450 characters, and in plain words.

## [2.3.0] - 2026-11-20 (versionCode 2300)
### Added
- Passes: a Minimum elevation picker hides low passes, and Next pass only finds the next one that reaches it within 24 hours. Shared with All satellites.
- Sky view button: opens the sky chart and Doppler frequencies of the selected satellite, even one with no passes, such as QO-100.
### Changed
- AmsatDroid now runs on Android 10.
- The large-font layouts added in 2.2.1 are removed; the app is designed for the default font size.
## [2.2.1] - 2026-11-19 (versionCode 2103)
### Fixed
- Long satellite names in the drop-downs wrap onto a second line instead of being cut off.
- At large font sizes the drop-down labels sit above their boxes, and the All satellites pickers and the location fields stack, so nothing overlaps or is cut off.
## [2.2.0] - 2026-11-16 (versionCode 2102)
### Added
- Settings, Display, "Centre the map on my location" puts your home in the middle of the world map, wrapping round, so the Pacific is whole for Australia and New Zealand. Off by default.
- Contact planner (menu): when a satellite is above the horizon at both your station and another you save by name and locator.
### Changed
- Red night mode is 20 percent brighter.
- The compass is off until you switch it on.
### Fixed
- The locator is checked as you type it.
- Each setup guide step opens at the top.
## [2.1.0] - 2026-11-15 (versionCode 2101, not released on its own)
### Added
- Contact planner (menu): see when a satellite is above the horizon at both your station and another one you save by name and locator, with the best moment and look angles at both ends.
### Changed
- Red night mode is 20 percent brighter and easier to read in a dark room.
- The compass is off until you switch it on.
### Fixed
- The locator is checked as you type it.
- Each setup guide step opens at the top.
## [2.0.8] - 2026-11-13 (versionCode 28)

### Fixed
- Help in the menu opens the new user guide instead of a "page not found" error. The guide is on the project wiki, which About now gives as the homepage.

## [2.0.7] - 2026-11-13 (versionCode 27)

### Fixed
- On the map, the footprint of a satellite whose coverage reaches over a pole no longer has a stray horizontal line running from its lowest point across the map.

## [2.0.6] - 2026-11-13 (versionCode 26)

### Added
- A short setup guide opens on a fresh install: set your home location, then pick a first satellite. You can skip it, and reopen it any time from Setup guide in the menu.
- Until your location is set, the passes screen and overview show a Set up button.

### Changed
- The Set home location menu item is now called Settings.

## [2.0.5] - 2026-11-13 (versionCode 25)

### Fixed
- On phones, the chosen start time above the Calculate button is readable again. It used to be squeezed into a narrow column beside the Change start time and Use current time buttons, down to a letter or two per line with a larger font. The time now has its own line, with the buttons below it.

## [2.0.4] - 2026-11-13 (versionCode 24)

Uploaded to Google Play but not released; its fix is in 2.0.5.

## [2.0.3] - 2026-11-10 (versionCode 23)
### Fixed
- Passes are no longer calculated for the default location (0, 0) on a fresh install. Until you set your home location, the passes screen, the overview and the widget ask you to set it instead of showing passes for the wrong place.

## [2.0.2] - 2026-11-10 (versionCode 22)

### Fixed
- Switching keps sources (for example Amateur, then New launches) no longer discards the list you came from. Each source keeps its own copy, so you can go straight back instead of waiting two hours for another download.
- The sky screen uses much less processing power when the compass is on. It used to redraw about 50 times a second on a still screen.

## [2.0.1] - 2026-11-10 (versionCode 21)

### Changed
- New app icon: a globe with an orbit ring and satellite.

## [2.0.0] - 2026-11-09 (versionCode 20)

AmsatDroid (previously AmsatDroid Free) rebuilt from the ground up for current Android, under a new package name, `uk.me.g4dpz.amsatdroid`. It is a new app on Google Play, not an update to the 2013 listing.

### Added
- Pass predictions for your location, with a sky chart and ground-track map.
- Doppler-corrected downlink and uplink frequencies, from the OscarWatch transponder database.
- Pass alerts, with optional spoken announcements.
- Compass-rotated sky chart and Aim mode.
- An all-satellites overview of what is up now and what is coming.
- A next-pass home screen widget.
- Red night mode, switchable automatically by the light sensor.
- Automatic keps updates from Celestrak or AMSAT.
- Phone and tablet layouts, including a landscape dashboard.
