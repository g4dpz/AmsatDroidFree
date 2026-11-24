# Manual test checklist: AMSAT satellite status reports

# Stage 1: reading

## Switch
- [ ] On a fresh install the switch is off and no request is made (network log); the status screen explains the feature and offers "Turn on"
- [ ] Turning it on from the status screen or Settings fetches and shows reports straight away
- [ ] Turning it off removes the block, the marks and the board at once and stops requests

## Status screen
- [ ] The rows match status.amsat.org for the same window at the same time: names, heard of total, last heard
- [ ] Each window (3, 6, 12, 24, 48, 72 h) works, and the chosen one is remembered
- [ ] The grid count looks right; on 72 h it may show "N+ grids"
- [ ] A row for a satellite in your elements shows its name and, when tapped, selects it and returns to the passes screen
- [ ] A row AMSAT lists but your elements do not says "Not in your orbital elements"
- [ ] Refresh twice within 5 minutes: the second says when the next refresh is possible, and no request is made
- [ ] The link opens status.amsat.org, and the credit is shown

## Selected satellite and marks
- [ ] The passes screen shows a block for the selected satellite, for example "AMSAT reports, last 24 h · FM: heard 12 times, last 3 h ago"
- [ ] The ISS shows each of its modes on its own line (Crew, FM, SSTV), with "crew active" where that is the report
- [ ] A satellite on the board with no reports shows "No AMSAT reports in the last 24 hours", and nothing suggests it is not working
- [ ] A satellite AMSAT does not list shows nothing
- [ ] LILACSAT-2 picks up the board's CAS-3H reports
- [ ] Tapping the block opens the status screen
- [ ] All satellites and the chooser show "Heard 3 h ago" beside satellites heard in the last 24 hours, and nothing beside the others
- [ ] A single report reads "heard once"
- [ ] With Keps source set to Weather (celestrak.org), nothing shows outside the status screen

## Fetching
- [ ] Opening the chooser or All satellites straight after a long break fetches fresh reports
- [ ] Changing satellite updates the block at once, with no new download
- [ ] Leaving the app for 20 minutes and coming back fetches a fresh summary; reopening within 5 minutes does not
- [ ] Airplane mode: the stored reports still show, with "as of HH:MM UTC" once over 30 minutes old; after 24 hours the block and marks disappear; with no stored copy nothing shows and nothing crashes; the status screen says it could not update

## General
- [ ] About credits "Satellite status data: AMSAT, https://status.amsat.org"
- [ ] The release build (R8) behaves the same as the debug build
- [ ] No clipping on the A6 (360 dp) in portrait and landscape, including the status screen and its window picker; red night looks right
- [ ] TalkBack on the A6 (David): the block, the marks and the board rows are read as text

# Stage 2: posting reports

Run against AMSAT's local stack with the debug base address, never against status.amsat.org, except the last item.

## Settings
- [ ] "Your callsign" is empty by default; a bad callsign (for example "G4" or "AB/CD/EF") is flagged as you type with the reason; a good one is upper-cased and saved
- [ ] "Send 6-character grid in reports" is off by default

## Report button
- [ ] With the switch on, a callsign and a home location set, the passes-screen block and the matched status-screen rows show Report
- [ ] Without a callsign, Report is disabled and says "Set your callsign in Settings to report"; without a home location it says that instead
- [ ] An unmatched status-screen row ("Not in your orbital elements") has no Report

## Form and confirmation
- [ ] For the ISS the entry picker lists its modes (Crew, FM, SSTV); a satellite with one entry has it preselected
- [ ] Crew active is offered only for the Crew entry
- [ ] Straight after a pass the time defaults to that pass's highest point; otherwise to now; it cannot be set in the future or more than 24 hours back; it is shown in UTC
- [ ] The form shows the callsign and the grid that will be sent: 4 characters (IO82), or 6 with the setting on
- [ ] Send shows a confirmation listing satellite and mode, kind, time UTC, callsign and grid, and saying it is public; Cancel sends nothing (local stack log)
- [ ] Post sends one report; the local board shows it with the right fields and time
- [ ] A second report for the same entry within the same 15 minutes says it replaced the earlier one
- [ ] With the network off, the form stays filled in with the reason and Try again
- [ ] A rejected report (for example an entry the local stack does not know) shows a plain reason and keeps the form
- [ ] After posting, the status screen and the block show the new report at the next refresh, and "You reported Heard at 20:22 UTC" appears

## General
- [ ] The callsign and grid appear in no log (logcat during a post)
- [ ] The release build (R8) reaches the confirmation and Cancel without a crash
- [ ] No clipping on the A6 in portrait and landscape; red night looks right; TalkBack reads the form and the confirmation (David)
- [ ] David's first real report from his callsign after a real pass, from the release build, appears on status.amsat.org

