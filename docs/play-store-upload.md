# Uploading AmsatDroid to Google Play

How to build the Android App Bundle (AAB) and upload it in the Play Console. Play Console wording changes from time to time, so treat menu names as a guide.

## 1. Know what you are uploading

| Item | Value |
|---|---|
| Package name | `uk.me.g4dpz.amsatdroid` (fixed for the life of the listing) |
| versionName | `2.0.0` (shown to users) |
| versionCode | `20` (must be higher than every earlier upload of this package) |
| minSdk / targetSdk | 30 / 36 |
| File to upload | `app-release.aab` (an App Bundle, not an APK) |

Every new release needs a higher `versionCode` in `app/build.gradle.kts`. Play rejects a bundle whose code was already used, even from an earlier draft or test track.

## 2. Build the signed bundle

Release signing reads a gitignored `keystore.properties` in the project root (see `keystore.properties.template`). It points at your upload keystore, `~/keystores/amsatdroid-upload.jks`. Never commit either file. Back the keystore up somewhere safe: losing the upload key means a reset request to Google.

From a clean, up-to-date `master`:

```
git checkout master && git pull
./gradlew :app:bundleRelease
```

The output is `app/build/outputs/bundle/release/app-release.aab`. Copy it somewhere outside the repo (for example `~/Downloads/AmsatDroid-2.0.0-code20.aab`). Do not commit binaries.

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

- **Privacy policy:** a public URL is required because the app requests location. State that location is used on the device only to compute pass predictions, and that the app does not send it to any server of its own. The app does use the network to download orbital elements (keps) and the transponder database; say so. Be precise about backups: the manifest has `allowBackup="true"` with no exclusions, so the saved home latitude and longitude (in shared preferences) can be included in Android Auto Backup to the user's Google account. Either say that in the policy and the data safety answers, or exclude those preferences from backup (`dataExtractionRules` and `fullBackupContent`) or set `allowBackup="false"` before making an absolute "not sent anywhere" claim.
- **Data safety form:** location (approximate and precise) is processed on the device only and is not sent to the developer or any third party. Confirm each answer against the privacy policy, including the backup point above.
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
5. Release notes, per language, for example: `Version 2.0: rebuilt in Kotlin and Compose; pass alerts, compass and aim mode, Doppler, home screen widget, red night mode, all-satellites overview, phone and tablet layouts.`
6. Save, Review release, fix any errors, then Start rollout.

For later production updates you can use a staged rollout (for example 20 percent) and raise it afterwards. Staged rollout may not be offered for the first production release of a new app, which then goes to all users in the selected countries; check what the console offers.

## 8. After you submit

- Review usually takes from a few hours to a few days. The first review of a new or long-dormant listing can take longer.
- Watch Publishing overview and your email for policy messages.
- Test the installed build from the Play link on a real device (location, alerts, widget), not only a sideloaded APK. Sideloaded debug builds use a different signature, so uninstall them first.

## 9. Releasing updates

1. Raise `versionCode` (and `versionName` if user-visible) in `app/build.gradle.kts`.
2. Merge through a pull request as usual and wait for CI to pass.
3. Build the bundle from the merged `master` (section 2).
4. Create a new release on the track and upload it.

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
