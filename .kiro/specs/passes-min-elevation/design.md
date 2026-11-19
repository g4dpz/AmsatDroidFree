# Design: Minimum elevation filter on the passes screen

## Where things are today
- `AppViewModel.calculate()` calls `PassCalculator.calculate(tle, lat, lon, start, hours)`, which calls `PassPredictor.getPasses(start, hoursAhead, true)`; `hoursAhead` 0 returns the next pass only. The result is kept in `rawPasses` and in `AppUiState.passes` (a list of `PassSummary`, each with `maxElevationDeg`), and saved by `PassStore`.
- `OverviewRows.upcoming(results, minElevation, now)` already filters by maximum elevation as a pure function with tests; the Contact planner does it inside its search.
- Pickers: `DropdownField` (a long value wraps over two lines; a long label has an ellipsis). `PassesPaneLayout` stacks the range and satellite pickers in narrow panes (under 480 dp). Large font sizes are not supported (David, 20 Nov 2026), so nothing here depends on the font scale.

## The change
### Setting
`passesMinElevation` in `SettingsRepository` (`Setting<Int>`, choices `PASSES_MIN_ELEVATION_CHOICES = listOf(0, 10, 20, 30)`, default 0, same validation as `overviewMinElevation`).

### Pure filtering
`PassFilter.visible(passes: List<PassSummary>, minElevationDeg: Int, keepId: String?): List<PassSummary>` in `domain`. It keeps passes with `maxElevationDeg >= min`, plus the one whose id is `keepId`. Tested with a minimum of 0 (everything), boundaries (exactly 10.0), the kept id, an empty list.

### Fixed ranges (6, 12, 24 hours)
`AppUiState` keeps all calculated passes as now. A derived list `visiblePasses` (or a function on the state) applies `PassFilter.visible` with the setting and `selectedPassId`. The Passes screen lists `visiblePasses`. Changing the picker only changes the setting; no calculation runs. `PassStore` is untouched (A5).

### Next pass only
With a minimum above 0, `calculate()` searches in steps: ask the predictor for the next pass; if its maximum elevation is below the minimum, start again from just after its end; stop at the first that reaches it or when the search has gone past the limit (O1, suggested 48 hours). The first lookup keeps wind-back (so a pass already in progress is found); every later lookup turns wind-back **off**, because `getPasses(..., true)` rewinds by a quarter orbit and, started just after a rejected pass ends, would find the same pass again instead of advancing. A test checks that rejecting a pass moves on to a distinct, later pass. The loop lives in `PassCalculator` (new function `nextPassReaching(tle, lat, lon, start, minElevationDeg, limitHours)`), testable with the same fakes as the existing calculator tests, and honours cancellation between steps. Changing the minimum while "Next pass only" is selected calls `calculate()` again.

### Keeping the selected pass
The no-argument `calculate()` clears the selected pass. A calculation started because the minimum changed (Next pass only) therefore uses the existing `calculate(keepSelected = selectedPass)` path, so the sky view keeps its pass (A4), including when no qualifying pass is found. A calculation the user starts with Calculate behaves as today.

### Heading and messages
The stored heading (`PassHeader.Predictions`) is set when results are calculated or restored, so a fixed-range picker change, which only changes the setting, would leave it stale. The filter clause and the "no pass reaches" message are therefore **derived** in the UI state from the current minimum and the unfiltered results (including restored ones), not stored in the header: the screen shows `Predictions` as now, plus a line "Passes reaching at least N degrees" computed from the setting, and `NoPassReaches` when the unfiltered results contain no pass at or above the minimum. That test uses the unfiltered list and ignores a below-minimum pass kept only because it is selected. For "Next pass only" a search that found nothing sets the `NoPassReaches` header at calculation time, with the limit in hours. New strings with the number as a placeholder; plurals are not needed (a number of degrees).

### UI
A `DropdownField` for the minimum next to the range picker, added to `PassesControls`. **With three pickers the stacking breakpoint is higher than for two**: side by side, three pickers get under 180 dp each in a 528 dp pane, so `PassesPaneLayout.stackPickers` stacks the group below **700 dp** of pane width (a new `STACK_THREE_PICKERS_BELOW_WIDTH_DP`; the 480 dp limit stays for two pickers). Test expectations in `PassesPaneLayoutTest`: 528 dp stacks, 699 dp stacks, 700 dp does not. The font scale plays no part (large fonts are not supported).

## What this does not change
The prediction code, `PassStore`'s format, the overview, the planner, the widget and alerts.

## Risks
- **Slow search** with a high minimum and "Next pass only": bounded by the limit and the step count; measured on the SM-T225 before choosing the limit.
- **Two filters to explain** (overview and passes). Mitigated by the same picker label and choices.
- **Selected pass hidden by the filter** would blank the sky view: handled by A4.
