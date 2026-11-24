# Manual test checklist: Pass card layout

At the default font size (large font sizes are not supported).

- [ ] Passes are grouped under day headings with the weekday ("Tuesday, 24 November 2026")
- [ ] Each card shows "22:04 to 22:24" in bold, then duration and maximum elevation, then the AOS and LOS azimuths
- [ ] A pass more than 5 minutes away is grey with no pill
- [ ] 5 minutes before AOS the card turns orange and the pill counts down to AOS, second by second
- [ ] At AOS the card turns green and the pill says "Passing"; at LOS it turns grey
- [ ] The changes happen on their own, without pressing Calculate pass, also when no card was orange or green before
- [ ] A pass with an alert shows the bell; long-press still sets and clears alerts; tap still opens the pass
- [ ] A selected pass below the minimum elevation still shows its note
- [ ] On the tablet, the selected pass has a border and keeps its state colour
- [ ] Calculating another satellite shows its list from the top
- [ ] Scrolling down, then calculating again for the same satellite, shows the new list from the top
- [ ] Light theme: the text on the orange and green pills and cards is easy to read
- [ ] Dark theme: the text on the orange and green pills and cards is easy to read
- [ ] Red night: everything is red; the orange pill is an outline, the green pill is filled and the green card is brighter; all text is readable
- [ ] TalkBack reads the pill ("AOS in 3:17", "Passing")
- [ ] No clipping on the A6 (360 dp) in portrait and landscape, or in the tablet's two-pane list
- [ ] The sky screen's pass information is unchanged
