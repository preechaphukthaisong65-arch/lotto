# Lotto — SRS 0.2 Offline Android App

Lotto is being developed as an Android-only, offline-first application. The on-device SQLite database is the system of record.

## Architecture rules
- No cloud database, backend server, account service, or data synchronization.
- No customer, sale, credit, payment, or receipt data is transmitted outside the device.
- Internet access is not required for core operation. Any future public calendar/result import must be optional and must never transmit private transaction data.
- GitHub Actions is used only to compile and package APK test artifacts; it is not part of the running application.

## Current implementation status
- SRS 0.2 baseline: `docs/SRS-0.2.md`.
- Room/SQLite local schema for periods, customers, sale documents/lines, payments, limits, results, and audit events.
- Atomic sale-posting DAO method and database triggers protecting posted sale documents and lines from update/delete.
- Initial home navigation, period creation/list, and customer creation/list.
- Sales, credit/payment workflows, limit engine, receipts, settlement, PIN, and encrypted backup/restore remain to be implemented and tested before the app can be called complete.
- Payout rules and other unresolved decisions in SRS 0.2 are intentionally not guessed or enabled.

## Build debug APK
The workflow is `.github/workflows/android-build.yml`. Pushes to `main`, `feature/**`, and `srs-0.2-offline` trigger a debug build. On a successful run, download the `lotto-debug-apk` artifact and extract `app-debug.apk`.

## Toolchain
- JDK 17
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Compile SDK 35 / Minimum SDK 26

This is a test build, not a signed production release.