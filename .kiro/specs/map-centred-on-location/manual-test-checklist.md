# Manual test checklist: Map centred on the user's location

- [ ] Settings, Display shows "Centre the map on my location", off by default
- [ ] Off: the Map view looks exactly as before
- [ ] On with home in Sydney: the home icon is in the middle of the map, Australia is whole and unbroken
- [ ] On with home in San Francisco, then at 179.9 east: the home icon is in the middle each time
- [ ] No seam or gap in the picture, at phone portrait, phone landscape, tablet and the large-tablet dashboard map
- [ ] The track and footprint line up with the coast; a line crossing the left or right edge is broken, not drawn across the map
- [ ] A footprint reaching over a pole has no stray horizontal line
- [ ] Red night mode: picture, icons, track and footprint still red and readable, no visible seam
- [ ] The switch redraws the map at once and the choice survives a restart
- [ ] With the home location unset, the switch does nothing and the map stays centred on 0
- [ ] 200 percent font: the Settings switch is readable and reachable
