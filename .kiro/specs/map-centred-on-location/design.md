# Design: Map centred on the user's location

## Where things are today
- `domain/MapProjection.kt`: `x(longitudeDeg, mapWidth)` normalises the longitude to 0..360 and puts 0 at the middle (east to the right); `y(latitudeDeg, mapHeight)`; `polylines(points, w, h)` builds lines and starts a new one wherever x jumps by more than half the map's width.
- `ui/WorldMap.kt`: draws `R.drawable.world` with one `drawImage`, then the track and footprint paths, then the home and satellite icons. Paths are built inside `drawWithCache`, so they are rebuilt only when the inputs or the size change.
- `ui/MapScreen.kt` is the only caller of `WorldMap`. The dashboard map on large tablets uses the same composable (check the call sites during task 3).

## The change
### `MapProjection`
Add a centre longitude (degrees, default `0.0`) to `x` and to `polylines`:

    fun x(longitudeDeg: Double, mapWidth: Double, centreDeg: Double = 0.0): Double

The shift is the longitude relative to the centre, wrapped to -180..180, then moved so the centre is in the middle: `((lon - centre + 540) mod 360) / 360 * mapWidth`. With `centreDeg = 0` this gives the present results, which the existing tests prove. `polylines` passes the centre to `x`, so its "jump larger than half the width" rule breaks lines at the new seam with no other change.

### The picture
Draw the Blue Marble bitmap in two pieces with `drawImage(srcOffset, srcSize, dstOffset, dstSize)`:
- The seam is at the longitude `centre + 180`. Its fraction along the picture is `s = ((centre + 180 + 180) mod 360) / 360`, that is `((centre + 360) mod 360) / 360`, measured from the left edge of the picture (which is longitude -180).
- Piece 1 is the picture from `s` to its right edge, drawn at the left of the map; piece 2 is the picture from its left edge to `s`, drawn after it. Widths are rounded in source pixels so the pieces meet exactly, with no hairline gap (use integer source and destination widths that add up to the map's width).
- When the centre is 0 the seam is at the edge and one piece covers everything: the same single draw as today.

### Setting
`centreMapOnHome` (boolean, default false) in `SettingsRepository`, a `SwitchRow` in Settings under Display, with a string `settings_centre_map`. Redraw follows from the setting's `StateFlow`.

### Wiring
`WorldMap` gets a `centreLongitudeDeg: Double` parameter. The caller passes the home longitude when the setting is on and the location is set (`HomeLocation.isSet`), otherwise `0.0`. The centre is part of the drawing cache's key, so it rebuilds when it changes.

## What this does not change
Prediction code, the footprint maths (`SatPos` range circle) and the golden file are untouched: only drawing positions move.

## Risks
- **A hairline seam** where the two picture pieces meet. Mitigation: integer pixel widths that sum exactly, and a check on the tablet at several sizes and in red night mode.
- **A footprint crossing the new seam** is drawn as open lines, not a closed shape, exactly as it already is at the date line. Acceptable; check it on screen with a satellite near the seam.
- **Red night filter** is applied per `drawImage`; both pieces use the same filter, so no visible difference. Check.
- **Home on the date line** (for example 179.9 east): the map is centred there, the seam is near longitude 0. Test the shift for 180 and -180.
