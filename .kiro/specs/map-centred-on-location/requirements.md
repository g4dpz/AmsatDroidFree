# Requirements: Map centred on the user's location

## Introduction
A user in Sydney, Australia asked (15 November 2026) whether the map could use OpenStreetMap instead of the NASA Blue Marble picture "so that we can have the map centred on his location". The aim is a map with his home in the middle, not street detail.

The map is a fixed 2:1 picture of the world with longitude 0 in the middle (`MapProjection.x` puts 0 at the centre and 180 at the edges). For Sydney (about 151 degrees east) the home sits near the right-hand edge, and a pass over Australia is split across the right and left edges of the picture.

This spec adds an option to **centre the same picture on the home longitude**, wrapping the picture round so nothing is cut off. It adds no map tiles, no network use and no new library.

## Decisions made
- D1. **OpenStreetMap tiles are not used.** They would contact `tile.openstreetmap.org` and reveal each user's IP address and the area they view, which conflicts with the privacy policy ("does not send your location") and the Data safety answers, and they need a library, attribution, caching and offline handling. Considered and rejected on 15 November 2026. Street detail is not needed for a ground track.
- D2. **The Blue Marble picture stays.** The change is only where the picture's seam is.
- D3. **Latitude is unchanged.** Only longitude is shifted.

## Assumptions (please confirm or change)
- A1. The option is **off by default**, so nobody's map changes until they choose.
- A2. It centres on the **home longitude** from Settings, not the live device position, so the map does not jump around and needs no location permission.
- A3. It applies to **both** places the map is drawn: the Map view screen and the dashboard map on large tablets held sideways. Both use one composable (`WorldMap`).
- A4. It ships in **2.2.0**, after the crash-reports release.

## Requirement 1: The setting
**User story:** As an operator in Australia, I want the map centred on where I am.
1. Settings, under Display, SHALL offer **Centre the map on my location**, a switch, off by default.
2. The choice SHALL persist, and changing it SHALL redraw the map at once without leaving the screen.
3. WHILE the option is off, the map SHALL look exactly as it does now.
4. IF the home location is not set (latitude and longitude both zero), the map SHALL stay as it is, centred on longitude 0.

## Requirement 2: The centred map
1. WHILE the option is on, the middle of the map SHALL be the home longitude, to within one pixel.
2. The picture SHALL wrap: the part that falls off one edge SHALL appear at the other, so the whole world is always shown with no gap or repeat.
3. The map SHALL keep its 2:1 shape at every size.
4. Latitude SHALL be drawn as now (north up, pole to pole).

## Requirement 3: What is drawn on it
1. The home icon SHALL be in the middle horizontally.
2. The satellite icon, ground track and footprint SHALL be placed with the same shift, so they line up with the picture.
3. A track or footprint line that crosses the new left or right edge SHALL be broken there, with no line drawn across the whole map (as the date line is handled today).
4. A footprint that reaches over a pole SHALL still be drawn without a stray horizontal line.

## Requirement 4: Looks and accessibility
1. In Red night mode the picture, icons, track and footprint SHALL keep their current red treatment.
2. The map's spoken description SHALL not change, and the new switch SHALL have a visible label.

## Requirement 5: Quality
1. The shift SHALL be a pure function with unit tests, including home longitudes 0, 151.2 (Sydney), -122 (San Francisco), 180 and -180.
2. The existing `MapProjectionTest` cases SHALL still pass unchanged (a centre of 0 gives today's result), and the golden regression test SHALL stay green.
3. Drawing SHALL not allocate per frame; paths are rebuilt only when the data, the size, the colours or the centre change.

## Not in scope
- Map tiles, zoom, pan or a different map image.
- Following the device's live position.
- Choosing a centre other than home.
