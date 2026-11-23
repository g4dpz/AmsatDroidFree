# Uploading AmsatDroid to Google Play

How to build the Android App Bundle (AAB) and upload it in the Play Console. Play Console wording changes from time to time, so treat menu names as a guide.

## 1. Know what you are uploading

| Item | Value |
|---|---|
| Package name | `uk.me.g4dpz.amsatdroid` (fixed for the life of the listing) |
| versionName and versionCode | in `app/build.gradle.kts`; the history is in `CHANGELOG.md` and the table below |
| Next code to set | 2301 for another upload of 2.3.0, 2400 for 2.4.0 (see "Version numbering") |
| minSdk / targetSdk | 29 / 36 |
| File to upload | `app-release.aab` (an App Bundle, not an APK) |

Every new release needs a higher `versionCode` in `app/build.gradle.kts`. Play rejects a bundle whose code was already used, even from an earlier draft or test track.

### Version numbering

`versionName` is the text users see. `versionCode` is the whole number Play uses to order uploads. They are separate and both live in `app/build.gradle.kts`.

**versionName** follows semantic versioning, MAJOR.MINOR.PATCH:

| Change | Bump | Example |
|---|---|---|
| Bug fixes and small corrections, no new feature | PATCH | 2.0.0 to 2.0.1 |
| New functionality | MINOR, and PATCH back to 0 | 2.0.1 to 2.1.0 |
| A breaking change, or what users would see as a different app | MAJOR | 2.1.0 to 3.0.0 |

Bump the name once per **release**, not once per feature. A release can carry several features under one number, and the release notes list them. Version numbers that climb with every small change stop telling users anything.

**versionCode** must be higher on **every** upload to Play, including a fix and including a test-track upload. It never goes down and is never reused. From 2.3.0 (David's choice, 21 Nov 2026) it is the version's digits followed by an upload digit: MAJOR, MINOR, PATCH, then 0 for the first upload of that version and 1 to 9 for re-uploads of the same `versionName`.

| Version | First upload | Re-upload |
|---|---|---|
| 2.3.0 | 2300 | 2301 |
| 2.3.1 | 2310 | 2311 |
| 2.4.0 | 2400 | |
| 3.0.0 | 3000 | |

- **More than ten uploads of one version.** The upload digit runs out at 9 (2309), and the next code (2310) belongs to 2.3.1. Before a tenth re-upload, raise PATCH instead (2.3.0 becomes 2.3.1, code 2310) and carry on from there.
- **A MINOR or PATCH above 9.** The four-digit code cannot hold it. Switch once, for good, to a fixed-width code: MAJOR, then MINOR and PATCH as two digits each, then the upload digit (2.10.0 = 210000, 2.10.1 = 210010, 3.0.0 = 300000). Every later release keeps that width, so codes stay in order and no two versions share one. Do not use a variable width: 2.1.10 and 2.11.0 would collide. Earlier codes: 20 to 28 for 2.0.0 to 2.0.8, then 2100 to 2103 (+1 per upload) for 2.1.0 to 2.2.1.

While testing, several uploads may share one `versionName` (for example 2.3.0 as 2300, then 2301) with rising codes. Testers get an update at most every two days (David's choice, 22 Nov 2026, to keep the noise down for testers; not a Play rule), so merged work waits on `master` for the next release.

Releases so far (the full list is in `CHANGELOG.md`):

| Release | Contents | versionCode |
|---|---|---|
| 2.0.0 to 2.0.8 | first test-track builds, fixes, the setup guide | 20 to 28 |
| 2.1.0 | Contact planner; never released on its own, its contents shipped in 2.2.0 | 2100, 2101 |
| 2.2.0 and 2.2.1 | Contact planner, map centred on your location, drop-down fixes | 2102, 2103 |
| 2.3.0 | choose satellites, minimum elevation, Sky view button, Android 10; first closed-track release | 2300 |
| 2.4.0 (planned) | pass-card layout | 2400 |

## 2. Build the signed bundle

Release signing reads a gitignored `keystore.properties` in the project root (see `keystore.properties.template`). It points at your upload keystore, `~/keystores/amsatdroid-upload.jks`. Never commit either file. Back the keystore up somewhere safe: losing the upload key means a reset request to Google.

From a clean, up-to-date `master`:

```
git checkout master && git pull
./gradlew :app:bundleRelease
```

The output is `app/build/outputs/bundle/release/app-release.aab`. Copy it somewhere outside the repo (for example `~/Downloads/AmsatDroid-2.3.0-code2300.aab`). Do not commit binaries.

Optional checks:

- Signature fingerprint: `keytool -printcert -jarfile app-release.aab` (SHA-256 should match the upload certificate Play shows under Setup, App signing).
- Version: `./gradlew :app:assembleRelease`, then `aapt2 dump badging app/build/outputs/apk/release/app-release.apk | grep ^package` shows versionCode and versionName.

If `keystore.properties` is missing, the release build is left unsigned and Play will reject it.

## 3. Play App Signing

Play holds the key that signs the APKs users install. Your keystore is only the upload key.

- First upload of the app: Play App Signing is the default for new apps. Accept it, and Play generates the app signing key.
- Later uploads: Test and release, Setup, App signing shows the registered upload certificate. Its SHA-256 must match your keystore's. If it does not, Play rejects the upload and you need to request an upload key reset.

### Background: the 2013 listing

The original app was published in 2013 as `uk.me.g4dpz.HamSatDroid`. That listing is still registered to the account with the status "App removed", so the package name cannot be reused (Create app reports "This package name is already in use"), and, being older than Play App Signing, it rejects an `.aab` unless it is enrolled with the original signing key. To avoid both problems, AmsatDroid is published as a new app under `uk.me.g4dpz.amsatdroid`. It is a new listing, not an update: the old installs, statistics and reviews stay with the removed app.

A possible original key was found on the development Mac (`~/Documents/Personal/keys/g4dpz/android.keystore`, alias `android`, created 15 Jun 2014, SHA-256 `B2:53:09:44:DF:33:33:12:64:2A:06:E5:7C:89:67:1D:C2:DD:AE:E7:22:EB:4B:E7:D9:78:27:97:8B:0A:02:9C`; an identical copy is in iCloud Drive). It is not confirmed to be the 2013 signing key. Keep it, and its passwords, backed up. It would be needed to enrol the old listing through the console's PEPK export (Setup, App signing).

## 4. Create or open the app

In the Play Console choose Create app. Set the app name (AmsatDroid), default language, App, Free, and accept the declarations. The package name is taken from the first bundle you upload, so it must be `uk.me.g4dpz.amsatdroid`; it cannot be changed afterwards. The console reports whether a package name is already in use.

## 5. Complete the app content

These sit under Policy and programs, App content. Each must be finished before a production release.

- **Privacy policy:** a public URL is required because the app requests location. State that location is used on the device only to compute pass predictions, and that the app does not send it to any server of its own. The app does use the network to download orbital elements (keps) and the transponder database; say so. Backups: the manifest sets `allowBackup="false"`, so the saved home latitude and longitude (in shared preferences) are not included in Android Auto Backup or device-to-device transfer. If that setting is ever turned back on, the coordinates can reach the user's Google account, and the policy and data safety answers must say so. The cost of `false` is that users restoring a phone must set their home location and preferences again.
- **Data safety form:** location (approximate and precise) is processed on the device only and is not sent to the developer or any third party. Confirm each answer against the privacy policy.
- **Content rating:** answer the questionnaire (a utility app, no user content, no ads).
- **Target audience:** choose adults or a general audience; not directed at children.
- **Ads:** none.
- **Permissions to justify if asked** (from `AndroidManifest.xml`): `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` (home position), `POST_NOTIFICATIONS` (pass alerts), `SCHEDULE_EXACT_ALARM` (alerts at the set time), `POST_PROMOTED_NOTIFICATIONS` (Android 16 live notification), `RECEIVE_BOOT_COMPLETED` (re-arm alerts after reboot), `INTERNET` and `ACCESS_NETWORK_STATE` (keps and database download). If the console shows an exact alarm declaration, explain that alerts must fire before a satellite rises. Check whether it is required for your target API when you reach it.

## 6. Store listing

Main store listing:

- App name (30 characters) and short description (80 characters).
- Full description (4000 characters). Describe pass prediction, sky chart and compass, aiming, Doppler, pass alerts, the widget, and red night mode.
- App icon 512 x 512 PNG.
- Feature graphic 1024 x 500.
- Phone screenshots (at least 2). Add 7-inch and 10-inch tablet screenshots, as the app has a two-pane tablet layout. Existing captures are in `docs/screenshots`.
- Contact email, and optionally website and phone.

## 7. Upload the bundle

Pick a track:

- **Internal testing:** fastest, up to 100 testers by email, no review wait. Use it first to check the install.
- **Closed testing:** needed before production if the developer account is a newer personal account (Google currently requires a minimum number of testers opted in for a minimum number of days; check the figures in the console).
- **Production:** public.

Steps:

1. Testing or Production, then Create new release.
2. Upload `app-release.aab` (drag and drop). Wait for processing.
3. Check the warnings. Missing deobfuscation file is expected: the build has minification off, so there is no mapping file to upload.
4. Release name defaults to the version; leave it.
5. Release notes, per language: paste the bullet points of the release's section in `CHANGELOG.md` (see section 9).
6. Save, Review release, fix any errors, then Start rollout.

For later production updates you can use a staged rollout (for example 20 percent) and raise it afterwards. Staged rollout may not be offered for the first production release of a new app, which then goes to all users in the selected countries; check what the console offers.

## 8. After you submit

- Review usually takes from a few hours to a few days. The first review of a new or long-dormant listing can take longer.
- Watch Publishing overview and your email for policy messages.
- Test the installed build from the Play link on a real device (location, alerts, widget), not only a sideloaded APK. Sideloaded debug builds use a different signature, so uninstall them first.

## 9. Releasing updates

1. Set `versionCode` in `app/build.gradle.kts` for every upload from the version (2.3.0 is 2300, a re-upload of it 2301; see "Version numbering" in section 1), and raise `versionName` when the release changes what users see (see "Version numbering" in section 1: fixes bump PATCH, new functionality bumps MINOR).
2. Merge through a pull request as usual and wait for CI to pass.
3. Build the bundle from the merged `master` (section 2).
4. Create a new release on the track and upload it.

### Release notes

Release notes are typed in the Play Console for each release, not built into the bundle, so they must be written every time.

1. In the same pull request as the version bump, add a section for the new version at the top of `CHANGELOG.md` (what a user would notice, in plain words). Keep the bullet points of a release under about 450 characters, so they fit Play's limit with room to spare.
2. On the release page in the Play Console, paste the **bullet points only** into the release notes box for English, not the version heading line and not the markdown headings such as `### Fixed`: Play shows plain text, so those symbols would appear literally and the heading would use up characters. The limit is 500 characters per language. Play offers to copy the previous release's notes, so replace them, or the new release will repeat the old text.
3. The wiki Home page and the tester emails can reuse the same wording.

## 10. Common rejections

| Message | Cause and fix |
|---|---|
| Version code has already been used | Raise `versionCode` |
| Upload certificate does not match | Wrong keystore; use the registered upload key, or request an upload key reset |
| To upload an Android App Bundle you must be enrolled in Play app signing | Old pre-2021 app that is not enrolled; this applies to the removed 2013 listing, not to the new package (section 3, background) |
| This package name is already in use (Create app) | The package belongs to an existing app, here `uk.me.g4dpz.HamSatDroid` (the removed 2013 listing); use `uk.me.g4dpz.amsatdroid` |
| You uploaded an APK, not a bundle | Upload the `.aab` |
| Target API level too low | Raise `targetSdk` (currently 36) |
| Privacy policy missing | Add the URL under App content |
| Package name already exists / belongs to another account | The package is owned by the account that first published it; use that account |
