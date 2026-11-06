# Lotto — SRS 0.2 Offline Android App

Lotto is being developed as an Android-only, offline-first application. The on-device SQLite database is the system of record.

## Architecture rules
- No cloud database, backend server, account service, or data synchronization.
- No customer, sale, credit, payment, or receipt data is transmitted outside the device.
- Internet access is used only when the user requests public result retrieval from the official GLO API. Sales, customers, credit, reports, backup, and cached results remain local/offline; private transaction data is never transmitted.
- GitHub Actions is used only to compile and package APK test artifacts; it is not part of the running application.

## Current implementation status
- SRS 0.2 baseline: `docs/SRS-0.2.md`.
- Room/SQLite local schema for periods, customers, sale documents/lines, immutable cancellation records, payments, limits, results, and audit events.
- Atomic sale-posting and bill-cancellation DAO methods; SQLite triggers protect posted sale documents, lines, cancellation records, result records, and supported category constraints.
- Sales support four categories with independent limits and payout rates: 3-digit top (last three digits of first prize), 3-digit permutation/โต๊ด (permutation of that tail), 2-digit top (last two digits of first prize), and 2-digit bottom (official GLO last-two result). Number values are stored as text so leading zeroes remain intact.
- Draw results are retrieved directly from the official GLO result API; the app validates the requested draw date, first-prize number, and official last-two result. It derives THREE_TOP and TWO_TOP from the first-prize tail, derives THREE_TOAD by permutation, and stores TWO_BOTTOM from the official last-two result. GLO front-three and last-three prizes are not used. GLO retrieval requires internet; saved results remain available offline.
- Encrypted local backup/restore is implemented with a user-supplied passphrase; it packages the SQLite database and receipt images, validates the archive/database before replacement, and includes rollback protection.
- No in-app PIN, user accounts, staff roles, or per-module permissions. Device screen lock is the device-level protection.
- Final physical-device and airplane-mode acceptance testing is still required.
- Production release gate: verify commercial-use permission for GLO result data; the GLO Data Catalog currently lists a Creative Commons Non-Commercial license for the result dataset.
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