# Manual test checklist: Contact Planner

Mark each box when it passes. Devices: Galaxy A16 emulator (411 x 891 dp), Galaxy Tab A7 Lite SM-T225 (600 dp portrait, about 1000 dp landscape). Use RS-44 with home IO82xk and another station EM29mm (Kansas City) for the known case, and a nearby station such as IO91wm for many windows.

## Opening

- [x] Menu, Contact planner opens the planner; Back returns to where you were
- [x] With the home location unset, the planner shows the not-set message and Set up opens the setup guide
- [x] With no saved station, the planner explains and offers Add station

## Stations

- [x] Add station: typing a locator fills latitude and longitude; typing them fills the locator
- [x] A 4-character square such as EM29 is accepted and stored as its centre, EM29mm, with a line under the field saying so
- [x] An invalid locator or coordinate is rejected with a message; Save is not possible without a valid location
- [x] The station shows by its name, or by its locator when it has none
- [x] Edit changes the station; Delete asks first, then removes it
- [x] The distance between the stations is shown and looks right
- [x] The chosen station, the minimum elevation, the scope and the time window are kept after leaving and reopening the app
- [x] At 10 saved stations, Add station says the list is full

## Search and results

- [x] Selected satellite, 48 hours, RS-44, IO82xk and EM29mm: the windows are plausible against the RS-44 example in the requirements (the elements are newer by then, so the times differ)
- [x] A nearby station gives a window for almost every pass
- [x] Raising the minimum elevation removes or shortens windows; one setting applies to both stations
- [x] All satellites: progress shows, results appear as they come, and the search finishes
- [x] A stationary satellite visible from both (for example QO-100 from two European stations, if in the keps) shows as usable now
- [x] Opening the planner with a station chosen searches straight away; Calculate again searches again from now
- [x] Changing a choice restarts the search; leaving the screen stops it
- [x] Open the station editor from the planner and come back: an unfinished search starts again, finished results are kept
- [x] Change the selected satellite on the passes screen and open the planner again: the results are for the new satellite
- [x] Too far apart: the empty message gives the distance and suggestions
- [x] Times are local, with UTC beside the best moment
- [x] A window that ends while the screen is open disappears

## Detail

- [x] Tapping a window expands it; tapping again closes it; the list keeps its place
- [x] Both stations' azimuth, elevation and range at the best moment, and the total path, are shown
- [x] No frequencies, mode picker or Doppler appear in the detail
- [x] The timeline has a row a minute, plus start, best moment and end
- [x] Your station's look angles match the sky screen for the same pass at the same time

## Quality

- [x] Phone portrait and landscape
- [x] Tablet portrait and landscape
- [x] Split-screen
- [x] Red night mode: everything readable in red
- [x] 200 percent font: nothing clipped, everything reachable by scrolling (passed on 15 Nov 2026; no longer a requirement, large font sizes are not supported)
- [x] TalkBack: headings, labels, and the detail read station by station
- [x] Speed on the SM-T225: one satellite over 48 hours, and all satellites over 24 hours, within the limits in requirement 9
- [x] No network use: the planner works in aeroplane mode

Ticked by the device scripts on 15 Nov 2026 (emulator and SM-T225; `build/planner/layouts.sh` 45/45 on each, `check-j.sh` 13/13 on the emulator), not by hand. Left for a person: everything else, including the sky-screen comparison, split-screen, red night mode, TalkBack, aeroplane mode, and speed (finish times are in tasks.md 5.2, but first results within 2 seconds were not measured).

Note (15 Nov 2026): David confirmed all boxes in this checklist himself and asked for them to be ticked. Items that ask for a recorded value (for example a time in seconds) were ticked without a value being written down.
