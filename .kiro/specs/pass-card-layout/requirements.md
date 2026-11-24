# Requirements: Pass card layout

## Introduction

Peter Goodhall (MM9SQL), the OscarWatch author, asked for the pass information to be better aligned, "like in OscarWatch". Each pass in the list is today six stacked `Label: value` lines (date, start time, duration, AOS azimuth, maximum elevation, LOS azimuth), the same for every pass, whether it is days away or overhead now.

This spec gives the list OscarWatch's shape: day headings, a compact card per pass, and colours that say at a glance which pass is coming up and which is in progress. It is based on two OscarWatch references Peter and David shared (20 and 24 November 2026) and on David's decisions below. The look will be judged on a phone with a trial build (branch `try/pass-card`) before the spec is final.

An older draft on branch `spec/pass-card-layout` (9 November 2026) predates these decisions and is replaced by this one. That branch can be deleted once this spec is merged.

## Decisions made (David)

- P1. **Three card states** (24 Nov 2026, "grey card, information only, assume that the card goes orange, 5 minutes before AOS"; green from Peter's card):
  - **Grey**, information only, while AOS is more than 5 minutes away;
  - **Orange**, from 5 minutes before AOS until AOS, with an orange pill counting down to AOS;
  - **Green**, from AOS to LOS, with a green "Passing" pill.
  This replaces the 22 November rule (a cream card while the satellite is above the horizon, counting down to LOS).
- P2. **Day headings** with the weekday above each day's passes ("Tuesday, 24 November 2026" in en-GB).
- P3. **No satellite name** on the card for now: one satellite is shown at a time, and its name is in the picker and the heading. The planned merged list of several satellites (spec `multi-satellite-passes`, on its own branch) will need the name on each card; the times line is where it will go, so this layout must not rule that out.
- P4. **Keep the AOS and LOS azimuths** (22 Nov 2026), although Peter's card has none.
- P5. **No alert button** on the card (22 Nov 2026): alerts stay on long-press, with the bell shown on a card that has one.
- P6. **No element-age badge** on the card (22 Nov 2026); the existing stale-elements warning above the list stays. Peter's 24 November card shows the element age, so this is reopened as O5.
- P7. **Try it first** (24 Nov 2026, "lets start with those and see what it looks / feels like"): a trial build on the phone, then this spec is settled.
- P8. **Default font size only.** Large font sizes are not supported (David, 20 Nov 2026), so layouts are checked at the default font size.

## Open points (to judge with the trial)

- O1. The green pill says "Passing" (Peter's card) rather than counting down to LOS (the 22 November decision). In the same decision: whether the orange pill shows the bare countdown ("3:17") or names its target ("AOS 3:17"); a screen reader always hears "AOS in 3:17".
- O2. The orange window is a fixed 5 minutes, not the alert lead time from Settings (5, 10, 15 or 30 minutes). With a 15-minute alert, the notification comes 10 minutes before the card turns orange.
- O3. Every pass within 5 minutes of AOS turns orange. With one satellite shown, that is at most one card at a time; the merged list of several satellites could have more.
- O4. The duration keeps one decimal ("19.8 min"), as today; Peter's card shows whole minutes ("42 min").
- O5. Element age on the card, as on Peter's 24 November card, or not (P6).
- O6. A pass that is already over (seen when a saved list is shown again hours later) is grey like a pass to come. Alternatives: a dimmed card, a "Finished" pill, or leaving finished passes out.
- O7. The day heading scrolls away with the list, so after a few scrolls the cards show times but no day. Alternative: a heading that stays at the top until the next day's heading pushes it off.
- O8. The cards keep their labels ("Duration: 19.8 min", "AOS azimuth: 200°"). Alternative: a shorter form without labels, such as "19.8 min · 25.0°".

## Requirement 1: The card

**User story:** As an operator, I want each pass to read at a glance: when, how long, how high, and where it rises and sets.

1. Each pass in the passes list SHALL be one card showing, in this order: the AOS and LOS times as "22:04 to 22:24" in bold, larger than the rest; the duration and the maximum elevation on one line; the AOS and LOS azimuths on one line. Each label and value SHALL stay together, moving to the next line as a whole when the pane is too narrow.
2. A pass that is listed only because it is selected, below the minimum elevation, SHALL keep its note ("Below the 20° minimum ...").
3. A card with an alert SHALL show the bell. Tap SHALL open the pass and long-press SHALL set or clear the alert, as today.
4. The sky screen's pass information SHALL stay as it is.

## Requirement 2: The card states

**User story:** As an operator, I want to see straight away which pass is about to start and which is overhead.

1. A card SHALL be grey, with no pill, while its AOS is more than 5 minutes away, and after its LOS (unless O6 decides otherwise).
2. From 5 minutes before AOS until AOS it SHALL be orange, with a pill counting down to AOS ("3:17", in minutes and seconds; hours too if ever needed).
3. From AOS until LOS it SHALL be green, with a pill reading "Passing".
4. The states SHALL change on their own, without recalculating, while the list is on screen. The countdown SHALL tick once a second while a card in the list is orange or green; otherwise the list SHALL be redrawn only when the next card is due to change state.
5. In red night mode the cards SHALL use shades of red only. The orange and green states SHALL differ in more than colour: the orange pill is an outline and the green pill is filled, and the green card is brighter than the others. The pill's words carry the state.
6. Text on cards and pills SHALL have a contrast of at least 4.5:1 against its background in the light and dark themes. In red night it SHALL be at least 3:1, because a red-only palette cannot reach more than 5.25:1 even against black.
7. A screen reader SHALL read the pill as words ("AOS in 3:17", "Passing").

## Requirement 3: Day headings and selection

1. Passes SHALL be grouped under a heading with the weekday and date of their AOS in the user's time zone, in the locale's full date format ("Tuesday, 24 November 2026" in en-GB).
2. A new calculation SHALL show its list from the top, also when it is for the same satellite or shares a day with the old list.
3. On a wide screen, where the sky view is beside the list, the selected pass SHALL be marked with a border instead of a different background, since the background now shows the state.

## Requirement 4: Layout

1. The cards SHALL fit without clipping on the Galaxy A6 (360 dp) and in the tablet's narrow two-pane list, in portrait and landscape, at the default font size (P8).
2. The list's performance SHALL stay as it is. The once-a-second redraw SHALL happen only while the passes screen is on screen, and SHALL redraw only the cards' pills and colours, not the pickers or the list heading.
