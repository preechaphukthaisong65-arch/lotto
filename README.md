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
- The live Room database uses SQLCipher AES-256 encryption. A random database passphrase is wrapped by an Android Keystore AES-GCM key; the raw database passphrase is not stored in plaintext preferences.
- On upgrade from an existing plaintext SQLite installation, the app verifies the original database, exports it to an encrypted temporary database, verifies the encrypted copy, and only then replaces the live file. It retains recovery files until the replacement has passed validation. It never uses destructive Room migration.
- Owners can create and restore password-encrypted portable backups from Settings. The backup wraps a plaintext snapshot in AES-256-GCM using PBKDF2-HMAC-SHA256 (310,000 iterations); the live database remains encrypted with SQLCipher. Backup passwords must be at least 12 characters and are not recoverable by the app.
- Restore validates the decrypted database integrity and exact schema version, re-encrypts it with the current device's SQLCipher key, replaces the database with recovery safeguards, and restarts the app.
- New sales can select an existing customer or create a separate customer ID. Similar names are never automatically merged; credit caps and new prize-debt checks use the stable customer ID. Legacy records without IDs remain unlinked and require manual identity review.
- Confirmed sales can be voided only before result entry, with a reason and explicit cash-refund confirmation. The original sale remains in the database with VOIDED status and an immutable refund/credit-cancellation event.

## Build outputs

- `lotto-debug-apk`: installable debug build for testing.
- `lotto-release-unsigned-apk`: optimized release variant, currently unsigned. A production installable APK/AAB requires a private signing key configured through protected CI secrets; no signing key is stored in this repository.

## Important operational limitations

- The local clock guard detects backward movement relative to timestamps already checkpointed by the app. An offline-only app cannot prove trusted real-world time against a user who controls the device, changes the clock between checkpoints, or reinstalls/clears app data. Production-grade time enforcement requires a trusted external time source or server.
- Existing pre-upgrade customer records have no stable identity; they are not auto-merged. Legacy winning bills require manual debt review before payout.
- Prize payout warnings show outstanding debt for the same customer ID and require acknowledgement when debt exists. Debt is not automatically offset against prizes; the operator must verify the customer and settle amounts explicitly.
- Voiding is prohibited after a draw result is recorded and for credit bills with any payment history. Such cases require a separately reviewed manual correction/refund so existing financial history is not silently rewritten.
- A successful CI build does not replace real-device testing, backup/restore rehearsal, or review of all financial workflows before live use.
