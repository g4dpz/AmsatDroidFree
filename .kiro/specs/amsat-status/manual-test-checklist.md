# Manual test checklist: AMSAT satellite status reports

- [ ] On a fresh install the switch is off and no request is made; once it is on, with the network up, the passes screen shows a block for the selected satellite within a few seconds, for example "AMSAT reports, last 24 h · FM: heard 12 times, last 20:22 UTC"
- [ ] The ISS shows each of its modes on its own line (Crew, FM, SSTV), with "crew active" where that is the report
- [ ] A satellite on the board with no reports shows "No AMSAT reports in the last 24 hours", and nothing suggests it is not working
- [ ] A satellite AMSAT does not list shows nothing
- [ ] With Keps source set to Weather (celestrak.org), nothing is shown on any screen
- [ ] The counts match status.amsat.org for the same satellite at the same time
- [ ] A single report reads "heard once"
- [ ] Tapping the block opens status.amsat.org in the browser
- [ ] All satellites and the chooser show "Heard in 24 h" on the satellites with Heard reports, and nothing on the others
- [ ] Opening the chooser or All satellites straight after a long break fetches fresh reports
- [ ] Changing satellite updates the block at once, with no new download
- [ ] Leaving the app for 20 minutes and coming back fetches a fresh summary; reopening within 5 minutes does not
- [ ] Airplane mode: the stored reports still show, with "as of HH:MM UTC" once they are over 30 minutes old; after 24 hours they disappear; with no stored copy nothing shows and nothing crashes
- [ ] Switching "Show AMSAT status reports" off removes the block and the tags at once and stops requests; switching it on with the passes screen open fetches and shows them
- [ ] About credits "Satellite status data: AMSAT, https://status.amsat.org"
- [ ] The release build (R8) behaves the same as the debug build
- [ ] No clipping on the A6 (360 dp) in portrait and landscape; red night looks right
- [ ] TalkBack on the A6 (David): the block and the tags are read as text
