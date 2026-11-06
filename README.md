# Lotto — SRS 0.2 Offline Android App

Lotto is being developed as an Android-only, offline-first application. The on-device SQLite database is the system of record.

## Architecture rules
- No cloud database, backend server, account service, or data synchronization.
- No customer, sale, credit, payment, or receipt data is transmitted outside the device.
- Internet access is used only when the user requests public result retrieval from the official GLO API. Sales, customers, credit, reports, backup, and cached results remain local/offline; private transaction data is never transmitted.
- GitHub Actions is used only to compile and package APK test artifacts; it is not part of the running application.

## Current implementation status
- SRS 0.2 baseline: `docs/SRS-0.2.md`.
- Room/SQLite local schema for periods, customers, sale documents/lines, payments, limits, results, and audit events.
- Atomic sale-posting DAO method and database triggers protecting posted sale documents and lines from update/delete.
- Sales are restricted to 3-digit top (ตรง) and 3-digit top permutation (โต๊ด), with separate limits. Credit/payment workflows, period limits, receipts, result matching, reports, and immutable payout-rate snapshots are implemented on the offline branch.
- Encrypted local backup/restore is implemented with a user-supplied passphrase; it packages the SQLite database and receipt images, validates the archive/database before replacement, and includes rollback protection.
- No in-app PIN, user accounts, staff roles, or per-module permissions. Device screen lock is the device-level protection.
- Final physical-device and airplane-mode acceptance testing is still required.
- Approved half-payout rule: retain the full recorded stake and apply 50% of the configured payout rate. Final payout calculations remain disabled until their end-to-end accounting implementation is verified.
- Confirmed sales are immutable. Cancellation is a separate auditable event with timestamp and reason; a replacement purchase is a new sale document. Cancelled bills remain visible in history but are excluded from active totals.

## Build debug APK
The workflow is `.github/workflows/android-build.yml`. Pushes to `main`, `feature/**`, and `srs-0.2-offline` trigger Android lint, unit/database tests, and a debug APK build. The `lotto-debug-apk` artifact includes `app-debug.apk`, its SHA-256 checksum, build metadata (commit/branch/run), and Room schemas.

## Toolchain
- JDK 17
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Compile SDK 35 / Minimum SDK 26

This is a test build, not a signed production release. Backups are portable encrypted files; keep the passphrase separately because it cannot be recovered.