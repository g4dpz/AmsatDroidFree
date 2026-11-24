# Design: Pass card layout

## Where things are today

- `PassCard` in `ui/PassesScreen.kt` draws six lines from `PassSummary.lines()` (`ui/PassFormatting.kt`), with a `primaryContainer` background when selected and the bell when an alert is set. `PassesPane` lists them with `items(visible, key = { it.id })`.
- `PassSummary` (`domain/PassSummary.kt`) has `start`, `end`, `tca`, the azimuths, `maxElevationDeg`, `isOver(now)` and `isInProgress(now)`.
- `Countdown.format` (`domain/Countdown.kt`) gives "4:05" and "1:04:05". `rememberTicker` (`ui/components/Lifecycle.kt`) ticks only while the screen is started.
- `DisplayFormats` (`ui/PassFormatting.kt`) formats dates and times in the user's locale and zone. `LocalRedNight` says whether red night is on; the theme is in `ui/theme/Theme.kt`.

## The change

All of this is in the trial branch `try/pass-card`, built and checked on the TabA11 emulator on 24 November 2026.

- **States (pure, `domain/PassCardState.kt`):** `PassCardState { UPCOMING, SOON, PASSING, OVER }` and `PassCardStates.of(pass, now)`, with `SOON_BEFORE = 5 minutes` (P1, O2). Tested at each boundary (`PassCardStateTest`).
- **Card (`ui/PassCardView.kt`):** `PassCardView(pass, formats, now, selected, hasAlert, onClick, onLongClick, belowMinimum)` replaces `PassCard`. A bold `titleMedium` line "22:04 to 22:24" (string `pass_card_times`) with the bell and the pill on the right; two `FlowRow`s for duration with maximum elevation and the two azimuths, reusing the existing strings. The pill is a rounded `Text` with a content description ("AOS in 3:17", "Passing").
- **Colours:** grey cards use `surfaceVariant`. Light theme: orange card `#FFF4E0` with pill `#F59E0B`, green card `#E3F5E1` with pill `#1B6E1F`; dark theme: `#3D2E12` / `#FFB74D` and `#1E3A22` / `#66BB6A`; red night: `secondaryContainer` with `secondary` for orange, `primaryContainer` with `primary` for green (Requirement 2.5). Selection is a 2 dp `primary` border (Requirement 3.3).
- **List:** `PassesPane` takes one `rememberTicker(1_000)` and passes `now` to each card. Before the first pass of each day it adds a heading item from `DisplayFormats.dayHeading` (`FormatStyle.FULL`), keyed `day-<catnum>-<date>`: a key without the satellite let a new list keep its scroll on a shared day heading, so the new day's first passes were hidden above (found in the trial; Requirement 3.2).

## What does not change

`PassSummary.lines()` and the sky screen's pass information, alerts, the widget, the overview's cards, `PassStore`.

## Risks

- **Colour alone.** Red night and colour-blind users rely on the pill's words; grey cards have no pill.
- **Battery.** The list redraws once a second while on screen; `rememberTicker` stops it in the background.
- **Narrow panes.** The time line can wrap beside a pill in the tablet's two-pane list ("22:04 to / 22:24"); acceptable, to be judged on the device.
