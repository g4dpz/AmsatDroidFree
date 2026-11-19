# Requirements: Minimum elevation filter on the passes screen

## Introduction
A tester asked (19 November 2026): "Any plans to add filters for minimum elevation for passes?"

Today the **All satellites** overview has a **Minimum elevation** picker (0, 10, 20 or 30 degrees; `overviewMinElevation`) and the **Contact planner** has one for both stations (`plannerMinElevation`). The main **Passes** screen has none: it lists every pass the library finds, including ones that barely clear the horizon and are not worth setting up for.

This spec adds the same filter to the Passes screen, for the selected satellite.

## Decisions made
- D1. **Same meaning as the overview.** A pass is shown when its **maximum elevation** is at least the chosen value, as in the overview and in Requirement 16 of `satellite-companion-features`.
- D2. **Same choices as the overview**: 0, 10, 20 and 30 degrees, default 0 (every pass, as now).
- D3. **Filtering only hides.** Nothing is deleted: choosing 0 again shows every pass that was calculated.

## Assumptions (please confirm or change)
- A1. The filter is its **own saved setting** (`passesMinElevation`), not shared with the overview's, so the two screens do not change each other. Sharing one value is the alternative.
- A2. For a fixed range (6, 12 or 24 hours) the filter **hides passes after they are calculated**, with no new calculation. Changing the picker updates the list at once.
- A3. For **Next pass only** the filter must find the next pass that *reaches* the minimum, which can be days away. The search therefore continues forward (in steps) until a pass reaches the minimum or a limit is reached (see open question O1).
- A4. The pass currently **selected** (shown in the sky view) stays in the list even when the filter would hide it, so choosing a filter never makes the sky view lose its pass. It is marked as below the minimum.
- A5. The saved passes (`PassStore`) keep **all** calculated passes; the filter is applied when they are shown, so restoring them after a restart uses the current filter.

## Open questions
- O1. **How far may "Next pass only" search?** With a minimum of 30 degrees on a satellite whose passes are mostly low, the next qualifying pass can be many hours or days away. Options: stop at 48 hours and say "No pass reaches 30 degrees in the next 48 hours"; or search a week. The longer the search, the longer Calculate takes (one satellite over 48 hours took about 1.6 s on the SM-T225). Suggested: 48 hours.
- O2. **Share the setting with the overview?** See A1.

## Requirement 1: The picker
**User story:** As an operator, I want to hide low passes so the list only shows ones worth setting up for.
1. The Passes screen SHALL offer a **Minimum elevation** picker with the choices 0, 10, 20 and 30 degrees, default 0.
2. The choice SHALL persist across restarts and apply to every satellite.
3. The picker SHALL be near the range picker, and SHALL stack with it and move its label above it by the same rules as the other pickers (narrow panes, large font).
4. Changing the choice SHALL update the list at once, without pressing Calculate, for the 6, 12 and 24 hour ranges.

## Requirement 2: What is shown
1. A pass SHALL be listed only when its maximum elevation is at least the chosen minimum, or it is the selected pass (A4).
2. The heading SHALL say that a filter is active and what it is, for example "Passes reaching at least 20 degrees".
3. IF every calculated pass is below the minimum THEN the screen SHALL say so ("No pass reaches 20 degrees in this range") and SHALL not show the generic "no passes" text.
4. With the minimum at 0 the list SHALL be exactly what it is today.

## Requirement 3: Next pass only
1. WITH "Next pass only" and a minimum above 0, Calculate SHALL show the next pass that reaches the minimum, searching forward as in A3.
2. IF none is found within the search limit THEN the screen SHALL say so, naming the limit.
3. Changing the minimum with "Next pass only" selected SHALL recalculate (it cannot be answered from the stored list), showing the usual calculating indicator.

## Requirement 4: Consistency
1. The passes in progress at the start time SHALL be treated like any other pass (filtered on their maximum elevation).
2. The alert, widget and overview features SHALL not change: the filter is a view of the Passes list only. (Setting an alert on a listed pass works as now.)
3. The golden regression test and the existing pass tests SHALL pass unchanged.

## Requirement 5: Looks and accessibility
1. The picker SHALL have a visible label, readable at 200 percent font, with no clipping on a 360 dp phone in portrait and landscape.
2. The active filter SHALL be stated in text, not shown by colour alone.
3. Red night mode SHALL apply as to the other pickers.

## Not in scope
- Filtering by anything but maximum elevation (duration, time of day, azimuth).
- A different filter per satellite.
- Changing the overview or the Contact planner.
