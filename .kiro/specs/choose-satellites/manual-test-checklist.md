# Manual test checklist: Choose satellites

- [ ] With nothing chosen the Satellite picker, the All satellites screen and the Contact planner show every satellite, as before
- [ ] Settings shows "Choose satellites" with "All satellites"; the end of the Satellite picker menu has "Choose satellites…"; both open the chooser
- [ ] The chooser lists every satellite with a checkbox, in the picker's order; the search matches names and catalogue numbers, ignoring case
- [ ] The count is right; Select all and Clear all work (and only on the satellites the search shows, when one is typed)
- [ ] Back or Cancel changes nothing; Done saves; the choice survives a restart
- [ ] After choosing five satellites the picker shows those five (and the selected one, if it is not among them), in the usual order
- [ ] The All satellites overview shows only the five, with "Showing your 5 satellites" and a working Change link
- [ ] The Contact planner's scope button reads "My satellites" and searches only the five; "Selected satellite" is unchanged; changing the list restarts a running search
- [ ] Switching the keps source so that a chosen satellite is missing: it is skipped, the planner says how many were left out, and the chooser shows it under "Not in the current elements" where it can be removed
- [ ] If none of the chosen satellites is in the current elements the overview says so and links to the chooser
- [ ] Clear all returns every screen to the full list
- [ ] The setup guide's satellite step still offers every satellite
- [ ] No clipping on the A6 (360 dp) in portrait and landscape; the keyboard open with the search field; the list scrolls
- [ ] Red night mode: the chooser looks like the other screens
- [ ] TalkBack (if tried): each checkbox reads its name and state; the count is announced
