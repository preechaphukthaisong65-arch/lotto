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
- Network access is restricted to explicitly requested public GLO result retrieval and must not include private transaction data.
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
Four sale categories are supported: THREE_TOP (last 3 digits of GLO first prize), THREE_TOAD (permutation of THREE_TOP), TWO_TOP (last 2 digits of GLO first prize), and TWO_BOTTOM (official GLO last-two result). Numbers are text, with exact category digit length and leading zeroes preserved. GLO front-three and last-three prize categories are never used.
Support equal-price and per-number variable-amount entry. Amounts are entered numerically, totals are calculated locally, and the app must validate before commit. A successful save creates a unique immutable document identifier and must not create duplicates on repeated taps.

## 6. Limits and half-payout markers
Limits are configured independently for each exact number in TWO_TOP, TWO_BOTTOM, THREE_TOP, and THREE_TOAD. A number's limit in one category never consumes or blocks capacity in the other category. Explicitly blocked numbers are managed separately from monetary limits. Half-payout status is configured per number/category in the period's limit rule; the sales screen has no manual full/half selector. Posting resolves and stores the status automatically. The receipt, history, reports, and settlement read the stored status. If no half-payout rule applies, the line is full payout.

Approved half-payout formula: keep the full actual stake and apply 50% of the configured normal payout rate to that line. Example: 100 baht at 70x uses 35x, producing 3,500 baht. The displayed stake must remain 100 baht; do not halve the recorded stake.

## 7. Customers and credit
Cash customers may be anonymous or use a generic label. Credit customers require a complete identity record as configured before a credit sale can be saved. Credit balances are derived from immutable sale and payment ledger entries. Support full and partial payments with timestamps and per-customer history.

## 8. Documents and immutability
After the user confirms and saves a sale document, its source document and lines cannot be edited or deleted. If incorrect, the user may cancel the entire bill; the app must retain the original bill and write a separate immutable cancellation record containing the document ID, original amount, cancellation timestamp, and reason. The cancelled bill must be visibly marked in history and excluded from active sales, limits, reports, credit balances, and result matching. A replacement purchase must be entered as a completely new bill; editing the original or silently rewriting its lines is prohibited. A bill with posted credit payments or a period with a recorded result/settlement must not be cancelled until a separately approved reversal workflow exists. Database-level protections must prevent update/delete of posted source rows and cancellation records.

## 9. Receipts
After a successful sale commit, generate a receipt image locally and store it on-device, linked to its source document. Include document ID, period, timestamp, number/category/amount lines, total, and half-payout labels where applicable. Receipt generation failure must be recoverable from the source document without duplicating the sale.

## 10. Results and settlement
GLO is the sole authoritative source for draw results. The app must retrieve results from the official GLO website/API, validate the requested draw date and response structure, and retain the GLO source URL, draw date, retrieval timestamp, and verification status. For this app, THREE_TOP is the final three digits of the GLO first-prize number; THREE_TOAD wins by permutation; TWO_TOP is the final two digits of the same first-prize number; TWO_BOTTOM is the official GLO last-two result. Never use GLO last-three prize. Preserve leading zeroes. The app must not substitute third-party result sources if GLO is unavailable or the response fails validation. Imported public results are cached in the local database and remain available offline. No customer, sale, payment, or other private data may be sent to GLO.

Payout rates are independent for all four sale categories. Legacy shared two-digit rate columns remain only for migration compatibility; TWO_TOP and TWO_BOTTOM use separate new rates. A period snapshot stores the applicable rates and configuration version so later setting changes cannot alter historical periods. If rates are configured, the app creates the snapshot automatically after successful GLO result import; if not, the user must configure rates and save the snapshot. The snapshot is a rate snapshot, not a separate payment ledger.

Approved half-payout formula: retain the full actual stake and apply 50% of the normal configured payout rate to that line. Example: 100 baht at 70x uses 35x and pays 3,500 baht. Do not reduce the recorded stake. Payout is calculated per winning line and rounded HALF_UP to the nearest satang before line payouts are summed. Sales after payout = active gross sales minus total winning payouts; this is not net profit and does not include cash refunds for cancelled cash bills.

## 11. Reports
Reports are derived from source ledgers. Cancelled bills remain visible in sales history and period cancellation summaries but are excluded from active sales, limits, credit balances, and result matching. Period/day reports include active sales, category totals (TWO_TOP, TWO_BOTTOM, THREE_TOP, THREE_TOAD), cash/credit breakdown, receivables, payments, outstanding balances, half-payout entries, winning payouts, and sales-after-payout totals when a rate snapshot exists. Cancelled bill history is inspectable in sales history and period summaries with its reason, timestamp, and original amount. CSV export includes active sales and a separate cancellation summary. Sales-after-payout is not net profit and excludes cash-refund accounting.

## 12. Storage and reliability
Use Room/SQLite with foreign keys, indexes, transactions, stable IDs, duplicate protection, integrity checks, and local persistence across app restarts. Handle interrupted writes, low storage, corrupt database, incorrect period/customer selection, device clock drift, and restore failures.

## 13. Backup and restore
Manual local backup, encrypted export, version validation, integrity check before restore, and manual device-to-device transfer. No automatic cloud backup or sync.

Implemented phase-7 backup format:
- The user supplies and confirms a passphrase of at least 10 characters; the app derives a 256-bit key with PBKDF2-HMAC-SHA256 (210,000 rounds) and encrypts with AES-256-GCM using a fresh random salt and nonce for every backup.
- One archive contains the Room/SQLite database and local receipt images. On Android 10 and newer it is created directly in the device's Downloads/Lotto folder through MediaStore; older supported Android versions use the app-specific Documents directory. No cloud document picker or network upload is used for export.
- Restore authenticates/decrypts before extraction, rejects unsafe archive paths and oversized archives, checks SQLite integrity, foreign keys, schema version, and required Lotto tables, then replaces the local database and receipts with rollback protection.
- The backup passphrase is not stored by the app and cannot be recovered. Losing it makes that backup unreadable.
- Supported restore schema versions are 1 through 8, subject to the app's registered Room migrations. Restore never accepts a database newer than the app.
- Android Auto Backup is disabled in the manifest. Backup files are user-managed and must be stored securely.

## 14. Security
No in-app PIN, user accounts, staff roles, or per-module permissions. The device's Android screen lock is the device-level access protection. Internet access is permitted only for explicitly requested public GLO result retrieval; the app must never transmit private customer, sales, credit, payment, or receipt data. Core sales, customer, credit, report, and backup functions must work offline. Preserve immutable records and audit events. Use a passphrase-derived key for exported backup encryption.

Implemented protections:
- Application data remains inside the Android app sandbox; no cloud database, backend, account service, or synchronization.
- Android system backup is disabled (allowBackup=false).
- Exported backup data is encrypted with a portable passphrase-derived key and authenticated encryption; the passphrase is held only in memory during the operation and cleared afterwards.
- Restore validates contents before replacement and rolls back to the prior database/receipts if installation or Room re-open validation fails.
- In-app PIN enrollment, user accounts, roles, and per-module permissions are explicitly out of scope. The backup passphrase is separate from device screen-lock security.

## 15. Acceptance tests
Install/launch; create period; accept only TWO_TOP, TWO_BOTTOM, THREE_TOP, and THREE_TOAD sales with matching digit lengths; reject all unsupported categories and wrong digit lengths; preserve leading zeroes; enforce independent limits and blocked numbers; auto-label half-payout; cash/credit customers; partial/full payments; receipt generation; immutable confirmed bills; cancellation audit, duplicate-cancel rejection, cancelled-bill exclusion from limits/reports/credit/results; GLO result retrieval and validation; result caching and offline access; automatic rate snapshots; payout totals; reports; backup/restore; airplane-mode core operation; process death/restart; and failure recovery on a physical Android device.

Phase-7 automated acceptance coverage includes encryption/decryption round trip, wrong-passphrase rejection, tamper detection, and minimum passphrase length. Restore validation additionally checks archive paths, archive size, SQLite integrity, foreign keys, schema version, required tables, and rollback on replacement failure. Manual cross-device restore and physical-device interruption testing remain required.

## 16. Remaining implementation gates
1. Complete live real-network integration tests for the GLO API request/response contract.
2. Define and implement cash-refund accounting when a cash bill is cancelled after money has physically changed hands.
3. Verify payout totals and sales-after-payout report reconciliation end to end using representative sale/result fixtures.
4. Confirm permission to use GLO open-result data in a commercial workflow; the GLO Data Catalog currently lists a Creative Commons Non-Commercial license for the result dataset.

Any wagering, payout, or related functionality must undergo applicable legal/compliance review before operational release. Current payout summaries are deterministic calculations from saved results and rate snapshots, but must not be represented as net profit or a final cash ledger until cash-refund handling and legal/commercial-use review are complete.

## 17. Build and delivery
- GitHub Actions runs Android lint, Room/database unit tests, and debug APK assembly on the offline development branch.
- The workflow verifies that the APK exists and is non-empty, generates a SHA-256 checksum, and records the source commit, branch, and workflow run in build metadata.
- The APK, checksum, build metadata, and Room schema snapshots are uploaded as one short-retention test artifact.
- Debug APKs are test builds only. A signed production release, key custody, and release signing process are not enabled in this phase.
