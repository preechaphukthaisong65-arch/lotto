# Lotto

Android application for recording lottery sales, credit accounts, draw results, prize liabilities and payouts.

## Technology

- Kotlin
- Jetpack Compose / Material 3
- Room / SQLite local database
- Offline-first; no Internet permission is declared
- GitHub Actions builds debug and release variants

## Data protection

- App access requires the device's secure screen credential or a strong biometric.
- Confirmed sales remain immutable; corrections are recorded as separate auditable actions rather than deleting the original sale.
- Lottery setting changes are recorded in a local audit history.
- New sales are blocked until every per-number limit, per-draw maximum prize liability, and per-customer credit cap has a positive configured value.
- Result entry is blocked before 16:00 Thailand time on the scheduled draw date.
- Sale and result writes maintain a local clock high-water mark and reject timestamps earlier than the last recorded time.
- Android automatic cloud backup is disabled because the database contains customer and financial records.
- Owners can create and restore password-encrypted database backups from Settings. Backup passwords must be at least 12 characters and are not recoverable by the app.
- Restore accepts only an intact database with the exact schema version supported by the installed app. The app closes after a successful restore and must be opened again.

## Build outputs

- `lotto-debug-apk`: installable debug build for testing.
- `lotto-release-unsigned-apk`: optimized release variant, currently unsigned. A production installable APK/AAB requires a private signing key configured through protected CI secrets; no signing key is stored in this repository.

## Important operational limitations

- The local clock guard detects backward movement relative to timestamps already checkpointed by the app. An offline-only app cannot prove trusted real-world time against a user who controls the device, changes the clock between checkpoints, or reinstalls/clears app data. Production-grade time enforcement requires a trusted external time source or server.
- Customer credit limits and debt matching currently use exact normalized first/last-name matching. Customers with identical names can be conflated; verify identity before extending credit or paying prizes.
- Prize payout warnings show matching outstanding debt but do not automatically offset debt against prizes. The operator must verify the customer and settle amounts explicitly.
- Confirmed sales are intentionally not cancellable or editable under the established business rule. A new auditable transaction must be recorded instead.
- A successful CI build does not replace real-device testing, backup/restore rehearsal, or review of all financial workflows before live use.
