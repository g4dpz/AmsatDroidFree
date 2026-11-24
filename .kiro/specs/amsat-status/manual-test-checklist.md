# Manual test checklist: AMSAT satellite status reports

- [ ] With the switch on and the network up, the passes screen shows a line for the selected satellite within a few seconds of opening, for example "AMSAT reports, last 24 h: FM heard 12 times, last 20:22 UTC"
- [ ] A satellite with several modes (the ISS) shows each mode separately
- [ ] A satellite with no reports shows "No AMSAT reports in the last 24 hours", and nothing suggests it is not working
- [ ] The counts match what status.amsat.org shows for the same satellite at the same time
- [ ] Tapping the line opens status.amsat.org in the browser
- [ ] All satellites shows "Heard in 24 h" on the satellites with Heard reports, and nothing on the others
- [ ] The chooser shows the same tag
- [ ] Changing satellite updates the line at once, with no new download (the stored copy is used)
- [ ] Leaving the app for 20 minutes and coming back fetches a fresh summary; reopening within 5 minutes does not
- [ ] Airplane mode: the stored reports still show, with "as of" once they are over 30 minutes old; with no stored copy nothing shows and nothing crashes
- [ ] Switching "Show AMSAT status reports" off removes the line and the tags and stops requests; switching it on brings them back
- [ ] About credits AMSAT's status board (CC BY 4.0)
- [ ] The release build (R8) behaves the same as the debug build
- [ ] No clipping on the A6 (360 dp) in portrait and landscape; red night looks right
- [ ] TalkBack (if tried): the line is read as text
