# Requirements: Pass Information Layout

## Introduction

Feedback from tester Peter Goodhall (MM9SQL), 9 November 2026: "on the pass pages the info could be better aligned like in OscarWatch".

Checked in the code (version 2.0.0), the pass information is shown as stacked `Label: value` text lines with no alignment between lines or between cards:

- **Pass list cards** (`PassCard` in `PassesScreen.kt`): six lines per pass (Date, Start time, Duration, AOS azimuth, Max elevation, LOS azimuth), built by `PassSummary.lines()`. Six cards fill a phone screen about twice over.
- **Pass information block** on the sky screen: the same six lines again.
- **Satellite position block**: Elevation, Azimuth, Latitude, Longitude, Range, as five lines.
- **Frequencies (Doppler) block** (`FrequencyLines` in `SkyScreen.kt`): Nominal, Tune and Shift for the downlink, then again for the uplink, as stacked lines of text.

This spec aligns these, so values line up in columns that can be scanned and compared, and so more passes fit on screen. It is a presentation change: no calculation changes.

"Pass pages" could mean the list, the sky screen, or both. This spec covers both (open question O1 below).

Related specs: `first-run-setup` and `multi-satellite-passes`. The multi-satellite spec adds a satellite name to the card, and depends on the card from this spec having a place for it.

## Open questions (please answer before building the card)

- O1. Which screen did Peter mean by "pass pages": the list of passes, the sky screen's Pass information and Frequencies, or both? **Ask Peter for a screenshot of the OscarWatch screen he means**, so the layout can follow it. Until then the design below is a proposal, not a copy.
- O2. Should azimuths also show a compass direction (for example `132° SE`)? Useful when aiming by eye; takes width. Proposed as optional (requirement 5).
- O3. Should the date show the weekday (`Mon 9 Nov`)? Proposed yes: it is the cheapest way to make a list of passes over several days readable.

## Decisions already made

- D1. Layout only. Values, units, rounding and the pass calculation are unchanged.
- D2. The existing interactions are kept: tap selects and shows the sky view, long-press sets or clears an alert, the bell icon marks a pass with an alert, the selected card is highlighted.
- D3. All text is in `strings.xml`. Numbers and dates use `DisplayFormats`, so they follow the user's language and time zone.
- D4. The layout adapts to width and font scale instead of cutting text off.

## Glossary

- **Cell**: one label and its value, for example `Max el` over `50.6°`.
- **Grid**: cells arranged in columns that line up from one card to the next.
- **AOS / LOS / TCA**: start, end and closest approach of a pass.

## Requirements

### Requirement 1: Pass list cards

**User story:** As an operator, I want to scan a list of passes at a glance, so that I can pick one quickly.

#### Acceptance criteria

1. Each pass card SHALL show a header with the weekday and date and the start time, in a larger type than the rest.
2. Each card SHALL show Duration, AOS azimuth, Max elevation and LOS azimuth as labelled cells in one row, with the same column positions on every card in the list.
3. Labels SHALL be short (for example `Duration`, `AOS`, `Max el`, `LOS`) and in a smaller type, above or beside their values; values SHALL be in the normal body type.
4. A card SHALL be no more than three lines high at normal font size on a phone (header, one row of cells; a second row only when the width requires it, see requirement 4).
5. WHEN a pass is in progress THEN its card SHALL say so in text in the header ("In progress", with the time to LOS).
6. The selected-pass highlight, the alert bell, tap and long-press SHALL work as before.
7. The whole card SHALL be one accessibility element with a single spoken description that reads the date, start time, duration, azimuths and elevation in a sentence (requirement 6).

### Requirement 2: Pass detail blocks on the sky screen

**User story:** As an operator, I want the details of the pass I am working to line up, so that I can read them without searching.

#### Acceptance criteria

1. The Pass information block SHALL use the same cells and column positions as the list card (so the two look alike).
2. The Satellite position block SHALL show Elevation, Azimuth, Range, Latitude and Longitude as aligned label and value rows, with units in a fixed position.
3. The Frequencies block SHALL show a table per link: a row each for Downlink and Uplink, with columns Nominal, Tune (or Transmit) and Shift, right-aligned so the digits line up. Frequencies use thousands separators as today.
4. Inverting-transponder, CTCSS, beacon-only and not-visible messages SHALL stay as text under the table, unchanged.
5. WHEN there is no live reading (the satellite is below the horizon) THEN the Tune and Shift columns SHALL show a dash and the block SHALL keep its shape.

### Requirement 3: Consistent type and spacing

**User story:** As a user, I want the screens to feel like one app.

#### Acceptance criteria

1. Cells, labels and values SHALL use the app's Material 3 type styles and spacing, not fixed sizes, so they follow the system font scale.
2. Numeric values SHALL use a figure style that aligns digits (tabular figures) where the font supports it.
3. The layout SHALL match in light, dark and red night themes; the red night palette SHALL need no new colours.

### Requirement 4: Adapt to width and font size

**User story:** As a user on any device, I want nothing clipped.

#### Acceptance criteria

1. WHEN the card is at least 360 dp wide at normal font size THEN the four cells SHALL fit in one row.
2. WHEN the card is narrower than the one-row minimum, or the font scale makes the cells wider than the card THEN the cells SHALL flow into two rows of two, still aligned, and SHALL NOT be cut off.
3. At 200 percent font scale on a 360 dp wide phone nothing SHALL be clipped or overlap.
4. The choice between one and two rows SHALL be a pure function of available width and font scale, kept in one place (like `LayoutThresholds`), and tested.

### Requirement 5: Optional compass direction (subject to O2)

**User story:** As an operator aiming by eye, I want to know which way a bearing points.

#### Acceptance criteria

1. IF O2 is accepted THEN each azimuth SHALL show its 16-point compass name next to the degrees (for example `132° SE`), using translatable letters.
2. WHEN the extra text would not fit THEN the compass name SHALL be dropped before any number is cut off.

### Requirement 6: Accessibility

**User story:** As a user of a screen reader, I want the same information in a sensible order.

#### Acceptance criteria

1. A pass card SHALL be read as one sentence, for example "Monday 9 November, starts 16:27, 21.7 minutes, rises at azimuth 132 degrees, maximum elevation 50.6 degrees, sets at azimuth 340 degrees. Alert set."
2. Tables SHALL be read row by row with their labels, for example "Downlink, nominal 145,950,000 hertz, tune 145,950,201 hertz, shift 201 hertz".
3. Touch targets SHALL stay at least 48 dp high.
4. Nothing SHALL rely on colour alone (the selected card also uses a visible outline or marker).

### Requirement 7: Tests and documentation

**User story:** As the maintainer, I want the layout changes to be safe to make.

#### Acceptance criteria

1. The cell content (labels, values, units, spoken text) SHALL be produced by pure code and unit tested, including a locale with a comma decimal separator and a 12-hour clock.
2. The one-row or two-row decision SHALL be unit tested at the boundary widths and font scales.
3. README and wiki screenshots that show pass cards SHALL be recaptured.
