# Manual test checklist: My Satellites

Mark each box when it passes. Devices: Galaxy A16 emulator (411 x 891 dp), Galaxy Tab A7 Lite SM-T225 (600 dp portrait, about 1000 dp landscape). Use a recent keps download so several satellites have passes.

## Mode and list
- [ ] The passes screen offers One satellite and My satellites; One satellite looks and works as before
- [ ] The chosen mode is still chosen after closing and reopening the app
- [ ] Choosing My satellites with an empty list opens the editor straight away
- [ ] The summary shows names and "and N more"

## Editor
- [ ] Every satellite in the keps is listed with a checkbox
- [ ] Typing a name or a catalogue number filters the list; case does not matter
- [ ] The selected count is right; Clear all empties the selection
- [ ] Selected satellites are shown first or clearly marked
- [ ] Cancel changes nothing; Done saves
- [ ] Rotating with the editor open keeps the selection and the search text
- [ ] The keyboard does not hide the list or the search field
- [ ] After switching keps source, a saved satellite missing from the new list shows as "not in current elements" and can be removed

## Merged list
- [ ] Calculate pass shows one list in time order, each card naming its satellite
- [ ] Next pass only shows the next pass of each satellite in the list
- [ ] 6, 12 and 24 hours show the right spans
- [ ] An always-up satellite (for example ES'HAIL 2) appears in the Always up section with az and el
- [ ] A satellite with bad or missing elements is skipped, and the note counts it
- [ ] Old elements are flagged by name
- [ ] Changing the list or range clears the old results
- [ ] The minimum elevation filter (if adopted) hides low passes and says so
- [ ] A calculation with 99 satellites on the SM-T225 finishes in under about two seconds
- [ ] Pressing Calculate again while one is running does not mix results

## Working a pass
- [ ] Tapping a pass of the second and third satellite opens the right sky view and name
- [ ] Aim mode and Doppler frequencies use that satellite
- [ ] Map view shows that satellite, not the One satellite selection
- [ ] Long-press sets an alert; the notification and spoken text name the right satellite
- [ ] The alert bell shows on cards of any satellite
- [ ] Rotating keeps the mode, the list, the results and the selected pass

## Saved results and upgrade
- [ ] Closing and reopening shows the last merged results, labelled as restored
- [ ] Installing over a build with a saved single-satellite list loads it in One satellite mode
- [ ] Opening a pass from the All satellites overview switches to One satellite and leaves My satellites untouched

## Widget (if adopted)
- [ ] In My satellites mode the widget shows the earliest next pass across the list, with its name
- [ ] With an empty list it shows "choose a satellite"
- [ ] In One satellite mode it is unchanged

## Layout and access
- [ ] Phone portrait and landscape, tablet two-pane and dashboard, split-screen: nothing clipped
- [ ] 200 percent font size: nothing clipped
- [ ] TalkBack announces the selected mode and each checkbox as "name, checked" or "not checked"
- [ ] Red night mode covers the mode control, the editor and the cards
