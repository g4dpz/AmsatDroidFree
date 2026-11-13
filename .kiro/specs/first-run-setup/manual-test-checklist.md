# Manual test checklist: First-run Setup

Mark each box when it passes. Devices: Galaxy A16 emulator (411 x 891 dp), Galaxy Tab A7 Lite SM-T225 (600 dp portrait, about 1000 dp landscape). Use `adb shell pm clear uk.me.g4dpz.amsatdroid` for a fresh start.

## Fresh install

- [ ] First launch opens the guide, not the passes screen
- [ ] Step 1: typing a locator fills latitude and longitude; typing lat/lon fills the locator
- [ ] Step 1: Next is disabled with a reason until a location is set; Skip is available
- [ ] Step 1: Use GPS asks for permission, fills the fields and shows the accuracy (hidden on a device without GPS)
- [ ] Step 1: denying permission shows a message and typing still works
- [ ] Step 2: the satellite list fills in (and updates in place if the first keps download finishes while it is open)
- [ ] Step 2: Done lands on the passes screen with the chosen satellite and passes already calculated
- [ ] The guide does not ask for notifications; setting the first alert still does (Android 13+)
- [ ] Second launch goes straight to the passes screen

## Skipped guide

- [ ] Skip on step 1 shows the passes screen with the banner
- [ ] Calculate pass with no location shows the message and no passes
- [ ] The all-satellites overview shows the same message and button
- [ ] The widget shows the "set your location" state
- [ ] The banner button opens the guide at the location step
- [ ] Setting a location makes the banner disappear without leaving the screen

## Upgrade and existing users

- [ ] Upgrade from a 2.0.2 build that calculated passes without a location set: on first start of the new build, no old passes show under the banner, and they do not return after a restart
- [ ] Upgrade with a location set: last session's passes are restored as before

- [ ] Installing over a configured build does not show the guide
- [ ] Overflow menu "Setup guide" opens it; leaving it without editing changes nothing
- [ ] The menu shows "Settings" (not "Set home location") and "Setup guide" as separate items
- [ ] Every "set your location" message points at the banner or Setup guide and names nothing the menu does not show

## Navigation and state

- [ ] Rotating on each step keeps the step and typed text
- [ ] With "Don't keep activities" on in Developer options (or: press Home, then `adb shell am kill uk.me.g4dpz.amsatdroid`), returning to the app mid-guide reopens it at the start of that step
- [ ] After a force stop (`adb shell am force-stop uk.me.g4dpz.amsatdroid`) and relaunch, the app opens on the guide only if the location is still unset and the guide was not finished or skipped; otherwise on the passes screen
- [ ] System Back goes to the previous step; on step 1 it leaves the guide
- [ ] Fresh install: after Done (or Skip), Back from the passes screen leaves the app; it does not reopen the guide or show a second passes screen
- [ ] Opened from the menu: Skip returns to the passes screen; Done returns to it with passes calculated; one Back then leaves the app
- [ ] Opened from the overview's message: Skip returns to the overview
- [ ] Opening the app from a notification or the widget opens that screen, not the guide

## Layout and access

- [ ] Phone portrait, phone landscape, tablet portrait, tablet landscape, split-screen: content scrolls and is not clipped
- [ ] 200 percent font size: nothing clipped
- [ ] TalkBack reads each step title as a heading and every button by name
- [ ] Red night mode: all steps readable
- [ ] Step indicator is text ("Step 1 of 2"), not colour only
