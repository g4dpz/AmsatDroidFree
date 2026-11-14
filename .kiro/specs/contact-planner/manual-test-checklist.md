# Manual test checklist: Contact Planner

Mark each box when it passes. Devices: Galaxy A16 emulator (411 x 891 dp), Galaxy Tab A7 Lite SM-T225 (600 dp portrait, about 1000 dp landscape). Use RS-44 with home IO82xk and another station EM29mm (Kansas City) for the known case, and a nearby station such as IO91wm for many windows.

## Opening

- [ ] Menu, Contact planner opens the planner; Back returns to where you were
- [ ] With the home location unset, the planner shows the not-set message and Set up opens the setup guide
- [ ] With no saved station, the planner explains and offers Add station

## Stations

- [ ] Add station: typing a locator fills latitude and longitude; typing them fills the locator
- [ ] If O5 is accepted: a 4-character square such as EM29 is accepted and stored as its centre, EM29mm
- [ ] An invalid locator or coordinate is rejected with a message; Save is not possible without a valid location
- [ ] The station shows by its name, or by its locator when it has none
- [ ] Edit changes the station; Delete asks first, then removes it
- [ ] The distance between the stations is shown and looks right
- [ ] The chosen station, your minimum elevation, the scope and the time window are kept after leaving and reopening the app
- [ ] At 20 saved stations, Add station says the list is full

## Search and results

- [ ] Selected satellite, 72 hours, RS-44, IO82xk and EM29mm: the windows match the RS-44 example in the requirements (or later ones for the current elements)
- [ ] A nearby station gives a window for almost every pass
- [ ] Raising either minimum elevation removes or shortens windows
- [ ] All satellites: progress shows, results appear as they come, and the search finishes
- [ ] A stationary satellite visible from both (for example QO-100 from two European stations, if in the keps) shows as usable now
- [ ] Opening the planner with a station chosen searches straight away; Calculate again searches again from now
- [ ] Changing a choice restarts the search; leaving the screen stops it
- [ ] Too far apart: the empty message gives the distance and suggestions
- [ ] Times are local, with UTC beside the best moment
- [ ] A window that ends while the screen is open disappears

## Detail

- [ ] Tapping a window expands it; tapping again closes it; the list keeps its place
- [ ] Both stations' azimuth, elevation and range at the best moment, and the total path, are shown
- [ ] The mode picker appears for a satellite with transponder data, and each station shows its own Tune and Transmit frequencies
- [ ] A satellite without transponder data says so
- [ ] The timeline has a row a minute, plus start, best moment and end
- [ ] Your station's look angles match the sky screen for the same pass at the same time

## Quality

- [ ] Phone portrait and landscape
- [ ] Tablet portrait and landscape
- [ ] Split-screen
- [ ] Red night mode: everything readable in red
- [ ] 200 percent font: nothing clipped, everything reachable by scrolling
- [ ] TalkBack: headings, labels, and the detail read station by station
- [ ] Speed on the SM-T225: one satellite over 72 hours, and all satellites over 24 hours, within the limits in requirement 9
- [ ] No network use: the planner works in aeroplane mode
