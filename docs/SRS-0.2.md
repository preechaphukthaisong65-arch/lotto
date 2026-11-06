# LOTTO — Software Requirements Specification (SRS)

Version: 0.2  
Date: 6 November 2026  
Platform: Android  
Architecture: Offline-first / Local Database  
Storage: SQLite on device  
Status: Requirements baseline

## 1. Project objective
Build an Android application for managing lottery-period records, sales documents, customers, credit balances, payments, limits, draw results, receipts, reports, and local backup/restore. Core functions must work without an internet connection.

## 2. Non-negotiable architecture and privacy
- SQLite database stored on the Android device is the system of record.
- No cloud database, backend server, account service, or synchronization.
- No customer or transaction data may be transmitted to external services.
- Network access, if later introduced, is restricted to explicitly requested public calendar/result sources and must not include private transaction data.
- Cached public data must remain usable offline.
- GitHub Actions is build automation only; it is not an application backend.

## 3. Main modules
1. Sales
2. Sales documents and history
3. Customers
4. Credit ledger
5. Payments
6. Number limits and full/blocked numbers
7. Draw results and period settlement
8. Reports
9. Settings
10. Encrypted local backup and restore

## 4. Draw periods
- Every document belongs to exactly one draw period.
- Period data must remain isolated and queryable historically.
- Initial operational targets: close sales at 14:00 and prepare/open the next period at 18:00, subject to configurable calendar rules.
- A closed period rejects new sales and backdated transactions.
- Public draw-calendar import is optional and must never upload transaction data.

## 5. Sales categories and entry
Only two sale categories are supported: 3-digit top (THREE_TOP) and 3-digit permutation/โต๊ด (THREE_TOAD). Each line must contain exactly three ASCII digits; leading zeroes must be preserved. No 2-digit, 3-digit bottom, or other category may be entered or accepted by the database.
Support equal-price and per-number variable-amount entry. Amounts are entered numerically, totals are calculated locally, and the app must validate before commit. A successful save creates a unique immutable document identifier and must not create duplicates on repeated taps.

## 6. Limits and half-payout markers
Limits must be configurable independently for each exact 3-digit number in THREE_TOP and THREE_TOAD. A number's limit in one category must not consume or block capacity in the other category. The system must distinguish allowed remaining capacity from rejected excess. Half-payout status is configured per number/category in the period's limit (หวยอั้น) rule; the sales screen must not offer a manual full/half selector. The posting transaction resolves and stores the status automatically from that rule, and the receipt, history, reports, and settlement read the stored status. If no half-payout rule applies, the line is full payout. Exact payout formulas remain a decision gate and must not be guessed in code.

## 7. Customers and credit
Cash customers may be anonymous or use a generic label. Credit customers require a complete identity record as configured before a credit sale can be saved. Credit balances are derived from immutable sale and payment ledger entries. Support full and partial payments with timestamps and per-customer history.

## 8. Documents and immutability
After the user confirms and saves a sale document, its source document and lines cannot be edited or deleted. If incorrect, the user may cancel the entire bill; the app must retain the original bill and write a separate immutable cancellation record containing the document ID, original amount, cancellation timestamp, and reason. The cancelled bill must be visibly marked in history and excluded from active sales, limits, reports, credit balances, and result matching. A replacement purchase must be entered as a completely new bill; editing the original or silently rewriting its lines is prohibited. A bill with posted credit payments or a period with a recorded result/settlement must not be cancelled until a separately approved reversal workflow exists. Database-level protections must prevent update/delete of posted source rows and cancellation records.

## 9. Receipts
After a successful sale commit, generate a receipt image locally and store it on-device, linked to its source document. Include document ID, period, timestamp, number/category/amount lines, total, and half-payout labels where applicable. Receipt generation failure must be recoverable from the source document without duplicating the sale.

## 10. Results and settlement
Official results must be imported from the Government Lottery Office (GLO) official website/API, with source URL/label, draw date, retrieval timestamp, and verification metadata retained. Imported public results are cached locally for offline use. If GLO cannot be reached or the response cannot be validated, the app must not substitute a third-party result source. Manual result entry is not the authoritative import path. Settlement is calculated from original documents and approved rule configuration, then saved as a period report. Payout rates are user-configurable in Settings and must not be hardcoded: 3-digit top and 3-digit permutation/โต๊ด each have independent configurable rates. Legacy database columns may remain for migration compatibility but must not appear as supported sale categories or editable settings. Users may change these rates for future periods. The rate values and configuration version used for a settlement must be snapshotted with the period settlement so later setting changes cannot alter historical results. Example defaults for initial setup may be 2-digit 70 baht per baht and 3-digit straight 500 baht per baht; 3-digit permutation/โต๊ด 100 baht per baht. These examples are editable and are not immutable business constants. Result categories are validated as 2-digit top/bottom or 3-digit top/bottom, with the entered number length matching the category. A 3-digit top result also matches 3-digit โต๊ด entries by digit permutation while preserving leading zeroes. The app may display matched winning lines and their stake totals, but this is not a payout calculation. After a closed period has at least one result and payout rates are configured, one immutable per-period snapshot stores all four rates and the configuration version. Later settings changes must not alter that snapshot. This snapshot is not itself a final payout settlement. Approved half-payout formula: retain the full actual stake and apply 50% of the normal payout rate to that line. Example: 100 baht stake at 70x uses 35x for a half-payout line, resulting in 3,500 baht. This must be shown consistently in result details and payout calculation. Official result source is GLO. The cancellation/refund treatment for money already physically received remains a separate accounting implementation gate.

## 11. Reports
Period/day sales, category totals, cash/credit breakdown, receivables, payments, outstanding balances, half-payout entries, winning entries, payout totals, and period net totals. Reports are derived from source ledgers, not manually re-entered totals. Period reports can be shared as CSV; exported reports must retain the distinction between gross document totals and any unresolved correction offsets.

## 12. Storage and reliability
Use Room/SQLite with foreign keys, indexes, transactions, stable IDs, duplicate protection, integrity checks, and local persistence across app restarts. Handle interrupted writes, low storage, corrupt database, incorrect period/customer selection, device clock drift, and restore failures.

## 13. Backup and restore
Manual local backup, encrypted export, version validation, integrity check before restore, and manual device-to-device transfer. No automatic cloud backup or sync.

Implemented phase-7 backup format:
- The user supplies and confirms a passphrase of at least 10 characters; the app derives a 256-bit key with PBKDF2-HMAC-SHA256 (210,000 rounds) and encrypts with AES-256-GCM using a fresh random salt and nonce for every backup.
- One archive contains the Room/SQLite database and local receipt images. On Android 10 and newer it is created directly in the device's Downloads/Lotto folder through MediaStore; older supported Android versions use the app-specific Documents directory. No cloud document picker or network upload is used for export.
- Restore authenticates/decrypts before extraction, rejects unsafe archive paths and oversized archives, checks SQLite integrity, foreign keys, schema version, and required Lotto tables, then replaces the local database and receipts with rollback protection.
- The backup passphrase is not stored by the app and cannot be recovered. Losing it makes that backup unreadable.
- Supported restore schema versions are 1 through 7, subject to the app's registered Room migrations. Restore never accepts a database newer than the app.
- Android Auto Backup is disabled in the manifest. Backup files are user-managed and must be stored securely.

## 14. Security
No in-app PIN, user accounts, staff roles, or per-module permissions. The device's Android screen lock is the device-level access protection. Preserve immutable records and audit events. Use a passphrase-derived key for exported backup encryption; do not hardcode keys.

Phase-7 implemented protections:
- Application data remains inside the Android app sandbox; no internet permission, cloud service, or synchronization is added.
- Android system backup is disabled (allowBackup=false).
- Exported backup data is encrypted with a portable passphrase-derived key and authenticated encryption; the passphrase is held only in memory during the operation and cleared afterwards.
- Restore validates contents before replacement and rolls back to the prior database/receipts if installation or Room re-open validation fails.
- In-app PIN enrollment, user accounts, roles, and per-module permissions are explicitly out of scope. The backup passphrase remains separate from device screen-lock security.

## 15. Acceptance tests
Install/launch; create period; all entry types; variable amounts; limit enforcement; half-payout labeling; cash/credit customers; partial/full payments; receipt image generation; immutable documents; settlement; reports; backup/restore; airplane-mode operation; process death/restart; and failure recovery on a physical Android device.

Phase-7 automated acceptance coverage includes encryption/decryption round trip, wrong-passphrase rejection, tamper detection, and minimum passphrase length. Restore validation additionally checks archive paths, archive size, SQLite integrity, foreign keys, schema version, required tables, and rollback on replacement failure. Manual cross-device restore and physical-device interruption testing remain required.

## 16. Unresolved decisions — do not invent
1. Exact half-payout formula and precedence.
2. Limit aggregation rules across top/bottom/toad.
3. Authoritative result source and verification process.
4. Accounting report definitions and correction offsets.
5. PIN roles and per-module permissions.

Any wagering, payout, or related functionality must undergo applicable legal/compliance review before operational release. Until decisions are approved, these calculations must remain disabled or clearly marked unconfigured.

## 17. Build and delivery
- GitHub Actions runs Android lint, Room/database unit tests, and debug APK assembly on the offline development branch.
- The workflow verifies that the APK exists and is non-empty, generates a SHA-256 checksum, and records the source commit, branch, and workflow run in build metadata.
- The APK, checksum, build metadata, and Room schema snapshots are uploaded as one short-retention test artifact.
- Debug APKs are test builds only. A signed production release, key custody, and release signing process are not enabled in this phase.
