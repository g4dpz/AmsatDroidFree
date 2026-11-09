# Manual test checklist: Pass Information Layout

Mark each box when it passes. Devices: Galaxy A16 emulator (411 x 891 dp), Galaxy Tab A7 Lite SM-T225 (600 dp portrait, about 1000 dp landscape). Set the font size in Settings, Display, Font size and style.

## Pass list cards
- [ ] Each card shows weekday and date, start time, then Duration, AOS, Max el and LOS in one aligned row
- [ ] The columns line up from one card to the next
- [ ] About twice as many passes fit on screen as before
- [ ] A pass in progress says so in the header, with the time to LOS
- [ ] Tap selects a pass and shows the sky view; the selected card is clearly marked
- [ ] Long-press sets and clears an alert; the bell shows and hides
- [ ] The card for a pass that starts after midnight shows the right date

## Sky screen
- [ ] Pass information uses the same cells as the card
- [ ] Satellite position values line up, with units in the same place
- [ ] Frequencies table: digits line up in each column, right-aligned
- [ ] With the satellite below the horizon the Tune and Shift columns show dashes and the table keeps its shape
- [ ] Inverting transponder, CTCSS, beacon-only and not-visible messages still appear under the table
- [ ] Changing the transponder mode updates the table

## Width and font size
- [ ] Phone portrait: one row of four cells at normal font
- [ ] Phone portrait at 130 percent font: nothing clipped (two rows if needed)
- [ ] Phone portrait at 200 percent font: nothing clipped or overlapping
- [ ] Phone landscape: the list pane and the sky screen are readable
- [ ] Tablet two-pane portrait and landscape dashboard: cards fit the pane
- [ ] Split-screen and a narrow pop-up window: no clipping

## Themes and access
- [ ] Light, dark and red night all look right
- [ ] TalkBack reads a card as one sentence, including "alert set" when there is one
- [ ] TalkBack reads a Frequencies row with its labels
- [ ] Touch targets feel easy to hit

## Comparison
- [ ] Layout compared with Peter's OscarWatch screenshot and any differences noted or fixed
- [ ] (If compass names are added) azimuths show `132° SE`, and the name drops out before a number is cut off
