# Phase 1 — Foundation review and hardening

Date: 6 November 2026  
Target branch: `srs-0.2-offline`  
Baseline: SRS 0.2

## Scope completed in source

- Confirmed the development branch exists and is ahead of `main`; all Phase 1 changes are committed only to `srs-0.2-offline`.
- Confirmed the Android toolchain baseline: Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.9, JDK 17, compile/target SDK 35, minimum SDK 26.
- Confirmed the app manifest does not request Android's `INTERNET` permission. Runtime dependencies are AndroidX Core and Room; no cloud/backend/synchronization SDK is configured.
- Added Room database version 2 and an explicit 1-to-2 migration. The migration installs database guards without rebuilding or deleting existing business tables.
- Installed SQLite guards for a single open draw period, valid period references for number-limit rules and draw results, sale-line/document period consistency, and payment/document/customer consistency.
- Enforced append-only behavior for sale documents, sale lines, payment entries, and audit events at SQLite level.
- Hardened sale posting: stable identifiers are required, duplicate line IDs are rejected, money sums use overflow-checked arithmetic, source document/line totals must agree, audit metadata must match the posted document, credit customer identity fields are validated, and number/category batches are aggregated once before limit evaluation.
- Hardened payment posting with matching audit metadata and overflow-safe balance validation. Database inserts retain ABORT conflict behavior so duplicate primary keys fail rather than overwrite.
- Configured Room schema export and added Robolectric tests for open-period uniqueness, atomic rollback on limit failure, and immutable sale/payment rows.
- Updated GitHub Actions to run database unit tests before building the debug APK and to retain the generated Room schema alongside the APK artifact.

## Verification status

| Check | Status |
|---|---|
| Branch isolation from `main` | Verified |
| Existing Room entities / DAO / database reviewed | Verified |
| Android toolchain versions reviewed | Verified |
| No runtime Internet permission | Verified |
| No cloud/backend/sync dependency in current app configuration | Verified |
| Explicit Room migration 1 → 2 | Implemented; CI verification pending |
| SQLite relationship and immutability guards | Implemented; CI verification pending |
| Database integrity tests | Added; CI verification pending |
| Debug APK build after Phase 1 changes | CI verification pending |
| Airplane-mode test on a physical Android device | Not yet performed |
| Upgrade test against an existing on-device version-1 database | Not yet performed |

## Known limitations / gates

- A successful compile does not prove a real-device migration or airplane-mode test. Those remain required before Phase 1 can be declared fully accepted.
- The migration preserves existing tables and rows. It adds guards and indexes; it does not attempt to repair pre-existing inconsistent records automatically.
- The seven SRS decision gates remain unchanged. No payout rate, half-payout formula, cross-category limit aggregation, correction accounting rule, result authority, accounting definition, or PIN role model has been invented.
- The current app still contains unfinished modules. This report covers Phase 1 only and does not represent overall SRS completion.

## Change history

See commits on `srs-0.2-offline` for the database migration, transaction validation, relationship guards, Room schema export, test suite, and CI test step.
