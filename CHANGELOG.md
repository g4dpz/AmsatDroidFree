# Changelog

What changed in each version of AmsatDroid, newest first. Each entry says what a user would notice; it is not a list of every commit. Version numbers follow [semantic versioning](https://semver.org/): fixes bump the last number, new functionality the middle one. `versionCode` is Google Play's ordering number and goes up with every upload.

The bullet points of the newest entry are also what goes into the Play Console release notes (500 characters per language, plain text, without the version heading or the `###` lines), so keep each release's bullets short, under about 450 characters, and in plain words.

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
