# Design: Pass Information Layout

## Overview

Replace the stacked `Label: value` text with small reusable layout pieces: a **cell** (small label over value), a **cell row** that lines up across cards, and a **label and value table** for the sky screen. The data, formatting and calculations stay as they are; only how they are laid out changes.

Open question O1 (which screen, and what OscarWatch looks like) can change the details below. The structure does not depend on the answer: the pieces are the same, only the arrangement differs.

## What exists (version 2.0.0)

| Piece | Where | Today |
|---|---|---|
| `PassCard` | `ui/PassesScreen.kt` | `Card` > `Row` > `Column` of `pass.lines(formats)` as `Text`, bell icon at the end |
| `PassSummary.lines(formats)` | `ui/PassFormatting.kt` | six strings from `pass_date`, `pass_start_time`, `pass_duration`, `pass_aos_azimuth`, `pass_max_elevation`, `pass_los_azimuth` |
| Pass information | `SkyContent` in `ui/SkyScreen.kt` | `pass.lines(formats).joinToString("\n")` in one `Text` |
| Satellite position | `SkyContent` | five `Text` lines (`sky_elevation` and friends) |
| Frequencies | `DopplerCard` and `FrequencyLines` in `ui/SkyScreen.kt` | stacked `Text` lines (`doppler_nominal`, `doppler_corrected`, `doppler_shift`) per link |
| Widths | `LayoutThresholds`, `PassesPaneLayout` | pickers stack under 480 dp; the pane can be 300 to 600 dp wide |

## Proposed card

At 360 dp and wider, normal font:

```
Mon 9 Nov        16:27                    🔔
Duration     AOS az     Max el     LOS az
21.7 min     132°       50.6°      340°
```

- Header row: weekday and date (bodyMedium) at the start, start time (titleMedium, bold) next to it, bell at the end. If the pass is in progress the header carries "In progress, LOS in 8:59" instead of the plain time.
- Cell row: four equal-weight columns, each a `labelSmall` label over a `bodyLarge` value. Equal weights give the same column positions in every card.
- Two-row fallback (narrow or large font): the cells flow as two rows of two, with the same equal weights.

That is three lines instead of six. Two cards fit where one did.

## Pieces

```kotlin
// ui/components/Cells.kt
data class Cell(val label: String, val value: String, val spoken: String)

@Composable fun CellRow(cells: List<Cell>, columns: Int, modifier: Modifier = Modifier)
@Composable fun LabelValueTable(rows: List<TableRow>, modifier: Modifier = Modifier)
```

- `CellRow` lays cells out with a `Row` of `Modifier.weight(1f)` columns, wrapping to `columns` per row when needed.
- `LabelValueTable` is a two- or four-column grid built with `Layout` (or a `Column` of `Row`s with shared column widths measured by `SubcomposeLayout`), so each column takes the width of its widest entry across rows.
- Values use `fontFeatureSettings = "tnum"` where supported, otherwise a monospaced fallback is not used (keeps the look consistent).

### Content: pure code

```kotlin
// ui/PassCells.kt
fun PassSummary.header(formats: DisplayFormats, now: Instant): PassHeaderText
fun PassSummary.cells(formats: DisplayFormats): List<Cell>
fun PassSummary.spokenDescription(formats: DisplayFormats, hasAlert: Boolean): String
```

These return strings from `strings.xml` (new short labels, same value formats as today). They need `@Composable` only for `stringResource`; the logic can take a `Resources`-like lookup so it is unit testable, the way `PassFormatting.kt` is structured today. `lines()` stays for any caller that still wants plain text (the share or log paths, tests) and is removed only when nothing uses it.

### Width decision: pure code

```kotlin
// ui/LayoutThresholds.kt
object PassCardLayout {
    const val ONE_ROW_MIN_WIDTH_DP = 360f
    /** 1 row of 4 when the card is wide enough for the font scale, otherwise 2 rows of 2. */
    fun columns(widthDp: Float, fontScale: Float): Int =
        if (widthDp >= ONE_ROW_MIN_WIDTH_DP * fontScale.coerceAtLeast(1f)) 4 else 2
}
```

The scaling means 200 percent font needs about 720 dp for one row, so phones fall back to two rows, which fit. The constant is tuned on the A16 and the SM-T225 (task 1).

### Sky screen blocks

- **Pass information**: the same `cells()` in a `CellRow` (two rows of two on a phone is fine here because the block is the main content), with the date and start time as a header line.
- **Satellite position**: a two-column `LabelValueTable`: label left, value right-aligned, unit included in the value (`50°`, `1,794 km`). Order: Elevation, Azimuth, Range, then Latitude and Longitude.
- **Frequencies**: a `LabelValueTable` with columns `Link`, `Nominal`, `Tune`, `Shift`:

```
              Nominal        Tune           Shift
Downlink USB  145,950,000    145,949,757    -243 Hz
Uplink LSB    432,146,000    432,146,719    +719 Hz
```

  On narrow widths the table scrolls horizontally inside its own box only if it must (it fits at 360 dp at normal font with Hz units moved to the header). Messages (not visible, inverting, CTCSS, beacon only) remain as text under the table.

### Accessibility

`Modifier.semantics(mergeDescendants = true)` on the card with `contentDescription = spokenDescription(...)`; the bell is decorative inside it. Tables use a `semantics { contentDescription }` per row. Touch targets are the whole card (already above 48 dp).

## Strings

New: `pass_cell_duration`, `pass_cell_aos`, `pass_cell_max_el`, `pass_cell_los`, `pass_in_progress`, `pass_spoken` (with placeholders), `table_nominal`, `table_tune`, `table_transmit`, `table_shift`, `table_downlink`, `table_uplink`. The six `pass_*` strings stay while `lines()` stays. If O2 is accepted: `compass_16` array for N, NNE, ... NNW.

## Risks

- **Not what Peter meant.** Mitigation: O1 first; the pieces are reusable whichever way the arrangement goes.
- **Clipping on small or large-font devices.** Mitigation: the width rule, tested, and checked at 200 percent on a 360 dp phone.
- **Table column widths.** `SubcomposeLayout` measurements cost a little; the table is small (3 rows), so it is fine.
- **Screenshots out of date.** Recapture after the change (task 7).

## Testing

- Unit: `PassCellsTest` (labels, values, units, weekday, in-progress text, spoken sentence, comma-decimal locale, 12-hour clock, a pass crossing midnight), `PassCardLayoutTest` (boundary widths, font scales 1.0, 1.3, 2.0), `TableFormattingTest` (shift sign and dash for no reading).
- Manual: `manual-test-checklist.md` in this folder.
