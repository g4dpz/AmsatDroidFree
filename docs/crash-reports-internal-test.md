# Crash reports in the internal test build

Applies to builds from the branch `internal/sentry-crash-reports` (2.1.0, versionCode 2101 and up). Not for the closed
or production tracks: take it out first (see the end).

## What is sent

When the app crashes or stops responding, and "Send anonymous crash reports" in Settings is on (the default in this
build), Sentry receives the error and its stack trace, the app version, the Android version, the device model, and the
time. The app removes the user, the server name, breadcrumbs and the device's own name before sending, and sends no
screenshots, performance data or session tracking. It never adds the home location, locator, callsign or any account.
Sentry's server sees the connection's IP address unless "Prevent Storing of IP Addresses" is switched on in the Sentry
project settings (switch it on).

## Privacy policy paragraph (wiki, Beta testers section)

> **Crash reports (internal test builds only).** Internal test builds can send an anonymous crash report to Sentry
> (sentry.io, hosted in the EU) when the app crashes or stops responding. It contains the error and stack trace, the
> app version, the Android version and the device model. It does not contain your location, name, callsign, email
> address or any account or advertising identifier. You can switch it off in Settings, Privacy, "Send anonymous crash
> reports". Production releases do not send crash reports.

## Play Console, Data safety (App content)

Update while a build with crash reporting is live, and revert afterwards:

- Data types: App info and performance, Crash logs (and Diagnostics if you want to be conservative).
- Collected: yes. Shared: yes, with Sentry as a third-party service (read the form's current definitions; a service
  processing data only on your behalf can fall under an exemption, but declaring it is the safe answer).
- Purpose: Analytics or App functionality. Data is encrypted in transit. Users can request deletion: no (data is
  anonymous), unless you add a way.
- Optional: collection can be switched off in Settings.

## Removing it before closed testing

1. Delete `io.sentry:sentry-android-core` from `app/build.gradle.kts`, the `sentryDsn` block and the `SENTRY_DSN`
   BuildConfig field.
2. Delete `crash/CrashReporting.kt`, its test, the `io.sentry.auto-init` meta-data in the manifest, the `crashReports`
   setting, the Settings "Privacy" section and its three strings, and the call in `HamSatDroidApp`.
3. Remove `sentry.properties.template` and the `.gitignore` entry (keep your local `sentry.properties` out of the repo).
4. Revert the Data safety form and remove the wiki paragraph.
5. Release as a new versionCode from master, not from this branch. Simplest: never merge this branch.
