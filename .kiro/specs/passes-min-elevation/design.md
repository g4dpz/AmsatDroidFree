# Design: Minimum elevation filter on the passes screen

## Where things are today
- `AppViewModel.calculate()` calls `PassCalculator.calculate(tle, lat, lon, start, hours)`, which calls `PassPredictor.getPasses(start, hoursAhead, true)`; `hoursAhead` 0 returns the next pass only. The result is kept in `rawPasses` and in `AppUiState.passes` (a list of `PassSummary`, each with `maxElevationDeg`), and saved by `PassStore`.
- `OverviewRows.upcoming(results, minElevation, now)` already filters by maximum elevation as a pure function with tests; the Contact planner does it inside its search.
- Pickers: `DropdownField` (large font handled in #63). `PassesPaneLayout` stacks the range and start-time controls in narrow panes.

## The change
### Setting
`passesMinElevation` in `SettingsRepository` (`Setting<Int>`, choices `PASSES_MIN_ELEVATION_CHOICES = listOf(0, 10, 20, 30)`, default 0, same validation as `overviewMinElevation`).

### Pure filtering
`PassFilter.visible(passes: List<PassSummary>, minElevationDeg: Int, keepId: String?): List<PassSummary>` in `domain`. It keeps passes with `maxElevationDeg >= min`, plus the one whose id is `keepId`. Tested with a minimum of 0 (everything), boundaries (exactly 10.0), the kept id, an empty list.

### Fixed ranges (6, 12, 24 hours)
`AppUiState` keeps all calculated passes as now. A derived list `visiblePasses` (or a function on the state) applies `PassFilter.visible` with the setting and `selectedPassId`. The Passes screen lists `visiblePasses`. Changing the picker only changes the setting; no calculation runs. `PassStore` is untouched (A5).

### Next pass only
With a minimum above 0, `calculate()` searches in steps: ask the predictor for the next pass; if its maximum elevation is below the minimum, start again from just after its end; stop at the first that reaches it or when the search has gone past the limit (O1, suggested 48 hours). The loop lives in `PassCalculator` (new function `nextPassReaching(tle, lat, lon, start, minElevationDeg, limitHours)`), testable with the same fakes as the existing calculator tests, and honours cancellation between steps. Changing the minimum while "Next pass only" is selected calls `calculate()` again.

### Heading and messages
New `PassHeader` states carry the minimum: `Predictions(..., minElevation)` adds a clause; `NoPassReaches(name, ..., minElevation, hours)` for Requirement 2.3 and 3.2. New strings with the number as a placeholder, plurals not needed (a number of degrees).

### UI
A `DropdownField` for the minimum next to the range picker, using the same stacking rule (`PassesPaneLayout.stackPickers`), added to `PassesControls`. A selected pass that the filter would hide is listed with a note "below the minimum".

## What this does not change
The prediction code, `PassStore`'s format, the overview, the planner, the widget and alerts.

## Risks
- **Slow search** with a high minimum and "Next pass only": bounded by the limit and the step count; measured on the SM-T225 before choosing the limit.
- **Two filters to explain** (overview and passes). Mitigated by the same picker label and choices.
- **Selected pass hidden by the filter** would blank the sky view: handled by A4.
