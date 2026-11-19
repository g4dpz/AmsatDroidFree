# Manual test checklist: Minimum elevation filter on the passes screen

- [ ] The Passes screen shows a Minimum elevation picker (0, 10, 20, 30), default 0, and the list is unchanged at 0
- [ ] With a range of 24 hours, choosing 20 hides passes whose maximum elevation is below 20 and the list updates at once
- [ ] A pass with a maximum of exactly 20.0 is shown at 20
- [ ] The heading says the filter is on and what it is; with every pass below the minimum the message says so
- [ ] Choosing 0 again brings every pass back (nothing was lost)
- [ ] The choice survives a restart and applies to a different satellite
- [ ] Next pass only with 30: shows the next pass that reaches 30, or says none does within the limit
- [ ] Changing the minimum with Next pass only recalculates
- [ ] A selected pass stays in the list and in the sky view when the filter would hide it, and is marked below the minimum
- [ ] Setting an alert on a listed pass works as before; the overview, planner and widget are unchanged
- [ ] No clipping on the A6 (360 dp) in portrait and landscape; at 200 percent font the picker's label is above it
- [ ] Red night mode: the picker looks like the others
