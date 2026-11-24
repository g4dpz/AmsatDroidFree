# Requirements: Pass card layout

## Introduction

Peter Goodhall (MM9SQL), the OscarWatch author, asked for the pass information to be better aligned, "like in OscarWatch". Each pass in the list is today six stacked `Label: value` lines (date, start time, duration, AOS azimuth, maximum elevation, LOS azimuth), the same for every pass, whether it is days away or overhead now.

This spec gives the list OscarWatch's shape: day headings, a compact card per pass, and colours that say at a glance which pass is coming up and which is in progress. It is based on two OscarWatch references Peter and David shared (20 and 24 November 2026) and on David's decisions below. The look will be judged on a phone with a trial build (branch `try/pass-card`) before the spec is final.

An older draft on branch `spec/pass-card-layout` (9 November 2026) predates these decisions and is replaced by this one.

## Decisions made (David)

- P1. **Three card states** (24 Nov 2026, "grey card, information only, assume that the card goes orange, 5 minutes before AOS"; green from Peter's card):
  - **Grey**, information only, while AOS is more than 5 minutes away;
  - **Orange**, from 5 minutes before AOS until AOS, with an orange pill counting down to AOS;
  - **Green**, from AOS to LOS, with a green "Passing" pill.
  This replaces the 22 November rule (a cream card while the satellite is above the horizon, counting down to LOS).
- P2. **Day headings** with the weekday above each day's passes ("Tuesday 24 November 2026").
- P3. **No satellite name** on the card: one satellite is shown at a time, and its name is in the picker and the heading.
- P4. **Keep the AOS and LOS azimuths** (22 Nov 2026), although Peter's card has none.
- P5. **No alert button** on the card (22 Nov 2026): alerts stay on long-press, with the bell shown on a card that has one.
- P6. **No element-age badge** on the card (22 Nov 2026); the existing stale-elements warning above the list stays.
- P7. **Try it first** (24 Nov 2026, "lets start with those and see what it looks / feels like"): a trial build on the phone, then this spec is settled.

## Open points (to judge with the trial)

- O1. The green pill says "Passing" (Peter's card) rather than counting down to LOS (the 22 November decision).
- O2. The orange window is a fixed 5 minutes, not the alert lead time from Settings (5, 10, 15 or 30 minutes).
- O3. Every pass within 5 minutes of AOS turns orange; with one satellite shown, that is at most one card at a time.
- O4. The duration keeps one decimal ("19.8 min"), as today; Peter's card shows whole minutes ("42 min").

## Requirement 1: The card

**User story:** As an operator, I want each pass to read at a glance: when, how long, how high, and where it rises and sets.

1. Each pass in the passes list SHALL be one card showing, in this order: the AOS and LOS times as "22:04 to 22:24" in bold, larger than the rest; the duration and the maximum elevation on one line; the AOS and LOS azimuths on one line. Each label and value SHALL stay together, moving to the next line as a whole when the pane is too narrow.
2. A pass that is listed only because it is selected, below the minimum elevation, SHALL keep its note ("Below the 20° minimum ...").
3. A card with an alert SHALL show the bell. Tap SHALL open the pass and long-press SHALL set or clear the alert, as today.
4. The sky screen's pass information SHALL stay as it is.

## Requirement 2: The card states

**User story:** As an operator, I want to see straight away which pass is about to start and which is overhead.

1. A card SHALL be grey, with no pill, while its AOS is more than 5 minutes away, and after its LOS.
2. From 5 minutes before AOS until AOS it SHALL be orange, with a pill counting down to AOS ("3:17", in minutes and seconds; hours too if ever needed).
3. From AOS until LOS it SHALL be green, with a pill reading "Passing".
4. The states SHALL change on their own, without recalculating, while the list is on screen; the countdown SHALL tick once a second.
5. In red night mode the cards SHALL use shades of red only: orange and green become two red shades, and the pill's words carry the state.
6. In the dark theme the colours SHALL be darker versions that keep the text readable.
7. A screen reader SHALL read the pill as words ("AOS in 3:17", "Passing").

## Requirement 3: Day headings and selection

1. Passes SHALL be grouped under a heading with the weekday and date of their AOS in the user's time zone ("Tuesday 24 November 2026").
2. A new calculation SHALL show its list from the top, also when the new list shares a day with the old one.
3. On a wide screen, where the sky view is beside the list, the selected pass SHALL be marked with a border instead of a different background, since the background now shows the state.

## Requirement 4: Layout

1. The cards SHALL fit without clipping on the Galaxy A6 (360 dp) and in the tablet's narrow two-pane list, in portrait and landscape, at the default font size.
2. The list's performance SHALL stay as it is: the once-a-second redraw happens only while the passes screen is on screen.
