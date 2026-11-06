# Lotto — SRS 0.2 Offline Android App

Lotto is being developed as an Android-only, offline-first application. The on-device SQLite database is the system of record.

## Architecture rules
- No cloud database, backend server, account service, or data synchronization.
- No customer, sale, credit, payment, or receipt data is transmitted outside the device.
- Internet access is used only to retrieve public draw results from LotteryChecks and HuayReview. The app does not connect to GLO; users open GLO independently when the two sources disagree. Sales, customers, credit, reports, backup, and cached results remain local/offline; private transaction data is never transmitted.
- GitHub Actions is used only to compile and package APK test artifacts; it is not part of the running application.

## Current implementation status
- SRS 0.2 baseline: `docs/SRS-0.2.md`.
- Room/SQLite local schema for periods, customers, sale documents/lines, immutable cancellation records, payments, limits, results, and audit events.
- Atomic sale-posting and bill-cancellation DAO methods; SQLite triggers protect posted sale documents, lines, cancellation records, result records, and supported category constraints.
- Sales support four categories with independent limits and payout rates: 3-digit top (last three digits of first prize), 3-digit permutation/โต๊ด (permutation of that tail), 2-digit top (last two digits of first prize), and 2-digit bottom (published last-two result). Number values are stored as text so leading zeroes remain intact.
- Draw results are checked against two public sources: LotteryChecks and HuayReview. Matching first-prize and last-two values are confirmed silently; mismatches block payout and settlement and require the user to open GLO independently and enter the manually verified first-prize and last-two results. If either source is unavailable, settlement remains blocked. Source checks are stored locally as an append-only audit trail. LotteryChecks attribution/rate limits and HuayReview CC BY 4.0 attribution are observed. GLO is not integrated into the app.
- Encrypted local backup/restore is implemented with a user-supplied passphrase; it packages the SQLite database and receipt images, validates the archive/database before replacement, and includes rollback protection.
- No in-app PIN, user accounts, staff roles, or per-module permissions. Device screen lock is the device-level protection.
- Final physical-device and airplane-mode acceptance testing is still required.
- Research scope: this project uses LotteryChecks and HuayReview public result data under their published reuse terms and attribution requirements. Commercial use/distribution is outside the current scope and requires a separate license review.
- Payout summaries use the saved per-period rate snapshot. Half-payout retains the full stake and applies 50% of the configured rate; each winning line is rounded HALF_UP to the nearest satang before totals are summed. Sales-after-payout is not net profit and does not include cash refunds for cancelled cash bills.
- Confirmed sales are immutable. Cancellation is a separate auditable event with timestamp, reason, and original amount; a replacement purchase is a new sale document. Cancelled bills remain visible in history and period summaries but are excluded from active totals.

## Build debug APK
The workflow is `.github/workflows/android-build.yml`. Pushes to `main`, `feature/**`, and `srs-0.2-offline` trigger Android lint, unit/database tests, and a debug APK build. The `lotto-debug-apk` artifact includes `app-debug.apk`, its SHA-256 checksum, build metadata (commit/branch/run), and Room schemas.

## Toolchain
- JDK 17
- Android Gradle Plugin 8.7.3
- Gradle 8.9
- Compile SDK 35 / Minimum SDK 26

This is a test build, not a signed production release. Backups are portable encrypted files; keep the passphrase separately because it cannot be recovered.

## Upcoming draw calendar
The Android app checks the public LotteryChecks draw calendar and HuayReview annual calendar independently. Only matching ordered upcoming dates are confirmed. The app stores a local append-only calendar audit, refreshes daily with WorkManager when network is available, and issues a seven-day local reminder when Android notification permission is granted. Source disagreement suppresses reminders; the app does not infer dates or connect to GLO.
