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
- Sales, credit/payment workflows, period limits, receipts, result matching, reports, and immutable payout-rate snapshots are implemented on the offline branch.
- Encrypted local backup/restore is implemented with a user-supplied passphrase; it packages the SQLite database and receipt images, validates the archive/database before replacement, and includes rollback protection.
- PIN roles and per-module permissions remain a deliberate decision gate; no speculative role model is enabled.
- Final physical-device and airplane-mode acceptance testing is still required.
- Payout rules and other unresolved decisions in SRS 0.2 are intentionally not guessed or enabled.

## Build debug APK
The workflow is `.github/workflows/android-build.yml`. Pushes to `main`, `feature/**`, and `srs-0.2-offline` trigger Android lint, unit/database tests, and a debug APK build. The `lotto-debug-apk` artifact includes `app-debug.apk`, its SHA-256 checksum, build metadata (commit/branch/run), and Room schemas.

## Toolchain
- JDK 17
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Compile SDK 35 / Minimum SDK 26

This is a test build, not a signed production release. Backups are portable encrypted files; keep the passphrase separately because it cannot be recovered.