# Design: Pass card layout

## Where things are today

- `PassCard` in `ui/PassesScreen.kt` draws six lines from `PassSummary.lines()` (`ui/PassFormatting.kt`), with a `primaryContainer` background when selected and the bell when an alert is set. `PassesPane` lists them with `items(visible, key = { it.id })`.
- `PassSummary` (`domain/PassSummary.kt`) has `start`, `end`, `tca`, the azimuths, `maxElevationDeg`, `isOver(now)` and `isInProgress(now)`.
- `Countdown.format` (`domain/Countdown.kt`) gives "4:05" and "1:04:05". `rememberTicker` (`ui/components/Lifecycle.kt`) ticks only while the screen is started.
- `DisplayFormats` (`ui/PassFormatting.kt`) formats dates and times in the user's locale and zone. `LocalRedNight` says whether red night is on; the theme is in `ui/theme/Theme.kt`, the red night palette in `night/NightPalette.kt`, and `ThemeMode` (system, light, dark, red night) in `night/ThemeMode.kt`.

## The change

A first version of all of this is in the trial branch `try/pass-card`, built and checked on the TabA11 emulator on 24 November 2026. The points below marked "(not in the trial)" came from the review of this spec and are still to be made.

- **States (pure, `domain/PassCardState.kt`):** `PassCardState { UPCOMING, SOON, PASSING, OVER }` and `PassCardStates.of(pass, now)`, with `SOON_BEFORE = 5 minutes` (P1, O2). Tested at each boundary (`PassCardStateTest`).
- **Pill text (pure, not in the trial):** a function that gives the pill's visible text and its spoken text for a state and a time ("3:17" and "AOS in 3:17", "Passing" and "Passing"), and the time at which the card next changes state. Unit-tested, so Requirement 2.2, 2.3 and 2.7 do not depend on manual checks alone.
- **Card (`ui/PassCardView.kt`):** `PassCardView(pass, formats, now, selected, hasAlert, onClick, onLongClick, belowMinimum)` replaces `PassCard`. A bold `titleMedium` line "22:04 to 22:24" (string `pass_card_times`) with the bell and the pill on the right; two `FlowRow`s for duration with maximum elevation and the two azimuths, reusing the existing strings. The pill is a rounded `Text` with a content description ("AOS in 3:17", "Passing").
- **Colours:** constants in a new `ui/theme/PassCardColors.kt`, chosen by the theme mode the app is actually using (light, dark or red night, with "system" resolved the same way `Theme.kt` resolves it), not by guessing from the background's brightness (the trial guesses; not in the trial). The orange and green are fixed and do not follow the wallpaper colours of Android 12 and later, on purpose: here they mean something, and they should look the same on every phone. Grey cards keep `surfaceVariant`, so they do follow the wallpaper.

  | State | Light | Dark | Red night |
  |---|---|---|---|
  | Grey card | `surfaceVariant`, text `onSurfaceVariant` | same | same |
  | Orange card | `#FFF4E0`, text `#1A1A1A` | `#3D2E12`, text `#ECECEC` | `surfaceVariant`, text `onSurfaceVariant` |
  | Orange pill | fill `#F59E0B`, text `#1A1A1A` (8.1:1) | fill `#FFB74D`, text `#1A1A1A` | no fill, 2 dp `primary` outline, text `primary` |
  | Green card | `#E3F5E1`, text `#1A1A1A` | `#1E3A22`, text `#ECECEC` | `errorContainer`, text `onErrorContainer` |
  | Green pill | fill `#1B6E1F`, text white (6.4:1) | fill `#66BB6A`, text `#1A1A1A` | fill `error`, text `onError` (black, 5.25:1) |

  Every pair is measured against Requirement 2.6 when the colours are made; the trial's white text on the light orange pill (2.15:1) fails it. In red night the green card's `errorContainer` is brighter than the other cards, and the pill is filled rather than outlined (Requirement 2.5); the trial's `secondaryContainer` and `primaryContainer` were only 1.14:1 apart. Selection is a 2 dp `primary` border (Requirement 3.3).
- **Days (pure, not in the trial):** `PassDays.group(passes, zone)` in `domain` puts the passes under their local day and gives each heading its key, beside `PassFilter` and `Countdown`. Unit-tested for Requirement 3.1 (a pass just after local midnight, a zone other than UTC). The trial does this inline in the list.
- **List:** `PassesPane` adds a heading item from `DisplayFormats.dayHeading` (`FormatStyle.FULL`) before each day, keyed `day-<catnum>-<date>`. The trial found that a key without the satellite let a new list keep its scroll on a shared day heading, hiding the new day's first passes. That key does not cover a new calculation for the same satellite, so the list also scrolls to the top whenever a calculation finishes (not in the trial; Requirement 3.2).
- **Tick (not in the trial):** the time is read inside each card, not at the top of `PassesPane`, so the pickers and the list heading are not redrawn every second (Requirement 4.2). The tick is once a second while any card is orange or green; otherwise it waits until the next state change from the pill-text function (Requirement 2.4). `rememberTicker` still stops it in the background. The trial ticks once a second at the top of the pane.

## What does not change

`PassSummary.lines()` and the sky screen's pass information, alerts, the widget, the overview's cards, `PassStore`.

## Risks

- **Colour alone.** Colour-blind users rely on the pill's words and, in red night, on its outline or fill; grey cards have no pill.
- **Narrow panes.** The time line can wrap beside a pill in the tablet's two-pane list ("22:04 to / 22:24"); acceptable, to be judged on the Galaxy A6 and the tablet.

## Clean-up when the trial's code is carried over

The trial's patch damaged the indentation in `PassFormatting.kt`, `PassesScreen.kt` and `strings.xml`. It left unused imports in `PassesScreen.kt` (`ExperimentalFoundationApi`, `combinedClickable`, `CardDefaults`). Some new calls use full names (`uk.me.g4dpz.HamSatDroid.ui.components.rememberTicker`, `java.time.Instant`, `java.time.LocalDate`) where the rest of the file uses imports. All of these are fixed in task 3.2.
