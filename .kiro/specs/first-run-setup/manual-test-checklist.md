# Manual test checklist: First-run Setup

Mark each box when it passes. Devices: Galaxy A16 emulator (411 x 891 dp), Galaxy Tab A7 Lite SM-T225 (600 dp portrait, about 1000 dp landscape). Use `adb shell pm clear uk.me.g4dpz.amsatdroid` for a fresh start.

## Fresh install
- [ ] First launch opens the guide, not the passes screen
- [ ] Step 1: typing a locator fills latitude and longitude; typing lat/lon fills the locator
- [ ] Step 1: Next is disabled with a reason until a location is set; Skip is available
- [ ] Step 1: Use GPS asks for permission, fills the fields and shows the accuracy (hidden on a device without GPS)
- [ ] Step 1: denying permission shows a message and typing still works
- [ ] Step 2: shows downloading, then the satellite count; "bundled, may be out of date" before the download finishes
- [ ] Step 2: Update now twice in a row shows "already up to date" the second time
- [ ] Step 2: automatic update switch and source match what Settings shows afterwards
- [ ] Step 3: Allow notifications shows the system prompt on Android 13+; denying shows the message and Next still works
- [ ] Step 3: lead time matches Settings afterwards
- [ ] Step 4: Done lands on the passes screen with the chosen satellite and passes already calculated
- [ ] Second launch goes straight to the passes screen

## Skipped guide
- [ ] Skip on step 1 shows the passes screen with the banner
- [ ] Calculate pass with no location shows the message and no passes
- [ ] The all-satellites overview shows the same message and button
- [ ] The widget shows the "set your location" state
- [ ] The banner button opens the guide at the location step
- [ ] Setting a location makes the banner disappear without leaving the screen

## Upgrade and existing users
- [ ] Installing over a configured build does not show the guide
- [ ] Overflow menu "Setup guide" opens it; leaving it without editing changes nothing

## Navigation and state
- [ ] Rotating on each step keeps the step and typed text
- [ ] Killing the app mid-guide (`adb shell am force-stop`) and reopening returns to the start of that step
- [ ] System Back goes to the previous step; on step 1 it leaves the guide
- [ ] Opening the app from a notification or the widget opens that screen, not the guide

## Layout and access
- [ ] Phone portrait, phone landscape, tablet portrait, tablet landscape, split-screen: content scrolls and is not clipped
- [ ] 200 percent font size: nothing clipped
- [ ] TalkBack reads each step title as a heading and every button by name
- [ ] Red night mode: all steps readable
- [ ] Step indicator is text ("Step 2 of 4"), not colour only
