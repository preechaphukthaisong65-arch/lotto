# Lotto — Android Offline Foundation

This branch contains an Android starter application and a GitHub Actions workflow that builds a debug APK.

## Current scope

- Native Android application using Kotlin.
- Offline-first foundation; no cloud database, account service, or synchronization.
- Local-only app shell.
- No betting, wagering, ticket-entry, or payout-calculation functionality.

## Build an APK with GitHub Actions

The workflow is located at `.github/workflows/android-build.yml`.

It runs automatically when code is pushed to `main` or a `feature/**` branch. It can also be started manually from the repository's **Actions** tab using **Android APK Build** → **Run workflow**.

When the run completes successfully:

1. Open the completed workflow run.
2. Scroll to **Artifacts**.
3. Download **lotto-debug-apk**.
4. Extract the ZIP and install `app-debug.apk` on an Android device.

The artifact is retained for 14 days. This is a debug build for testing, not a signed production release.

## Development configuration

- JDK: 17
- Android Gradle Plugin: 8.7.3
- Gradle: 8.9
- Compile SDK: 35
- Minimum SDK: 26
