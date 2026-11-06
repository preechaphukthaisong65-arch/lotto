# LOTTO — Software Requirements Specification (SRS)

Version: 0.4  
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
- Network access is restricted to public draw-result retrieval and public draw-calendar page retrieval from LotteryChecks and HuayReview. The app does not connect to GLO; the user may open GLO externally for manual verification. No private transaction data is transmitted.
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
- Draw dates are not assumed to follow a fixed calendar pattern. The upcoming draw date must be checked against both LotteryChecks and HuayReview calendar information.
- A draw date is confirmed only when both sources provide a valid upcoming date for the same draw period and the dates match. A single-source date is provisional; a disagreement or unavailable/malformed calendar source is unconfirmed. The app must never infer or invent a date from historical patterns.
- Period transitions are evaluated in Asia/Bangkok time. A foreground check and a network-independent periodic WorkManager catch-up worker advance a due OPEN/PREPARED period while the app is backgrounded; Android may defer exact execution, so the worker catches up at its next run.
- The app must aim to notify the user at least 7 calendar days before each confirmed draw date. If a date first becomes confirmed fewer than 7 days before the draw, notify immediately and state the remaining lead time; do not imply that the 7-day notice was achieved.
- Recheck upcoming calendar information at least once per day when network access is available. Calendar checks are user-data-free and must not transmit customer, sales, credit, payment, or receipt data. Cached calendar information remains viewable offline but cannot be treated as a fresh check.
- If both sources later agree on a changed draw date, update the upcoming period date with an auditable change record and notify the user of the previous and new dates. Never silently change a confirmed date. If sources disagree about a change, retain the last confirmed date as historical information, mark the current schedule unconfirmed, and notify the user to verify it.
- Notification delivery depends on Android notification permission and device scheduling constraints. If notifications are unavailable or denied, show the reminder and its status in the app. Calendar source access must use documented/publicly accessible information and comply with each source's terms; do not assume the result API provides future calendar dates.
- Public draw-calendar retrieval uses the public LotteryChecks draw-calendar page and HuayReview annual calendar pages as a module separate from result checks. It must never upload transaction data.


### 4.1 Upcoming draw calendar and advance reminders
- Treat LotteryChecks and HuayReview as independent calendar sources, separate from their result-data checks. Do not use a result endpoint as a future-calendar endpoint unless its documented contract explicitly supports future draw dates.
- For each upcoming draw period, store the draw date, each source's reported date, source reference/URL, retrieval timestamp, validation status, and confirmation status locally.
- Calendar confirmation requires both sources to identify the same draw period and the same valid future date. If only one source has a usable date, label it provisional. If dates differ, a source is unavailable, or data is malformed/ambiguous, label the schedule unconfirmed and show the discrepancy or failure reason.
- Do not create or shift a draw period from an inferred recurring pattern. Do not silently overwrite a previously confirmed date.
- Schedule a reminder for 7 calendar days before the confirmed draw date. If confirmation occurs inside that 7-day window, issue an immediate notice stating the actual remaining time. A later confirmed date change must cancel/recalculate the pending reminder and issue a change notice that identifies both dates.
- When the sources disagree about a changed date, do not automatically choose either date. Preserve the last confirmed date and its audit history, mark the current schedule unconfirmed, and alert the user that manual checking against the official GLO announcement is required. The app does not retrieve GLO data.
- Refresh calendar sources at least daily when online, subject to source rate limits and Android background-work constraints. Provide a manual refresh action. Never promise exact delivery time when Android has deferred background work or notifications are disabled.
- Offline mode may display the last cached calendar and its last-checked timestamp. Offline data must be visibly marked as cached/stale and must not be represented as a fresh two-source confirmation. The app must check validated network connectivity before presenting any calendar comparison as currently confirmed.
- Calendar retrieval is strictly public-data-only. No private app data may be transmitted. Source parsing must be isolated from result parsing, validated, auditable, and disabled if compliant public access is unavailable.

## 5. Sales categories and entry
Four sale categories are supported: THREE_TOP (last 3 digits of the published first-prize number), THREE_TOAD (permutation of THREE_TOP), TWO_TOP (last 2 digits of the published first-prize number), and TWO_BOTTOM (published last-two result). Numbers are text, with exact category digit length and leading zeroes preserved. GLO front-three and last-three prize categories are never used.
Support equal-price and per-number variable-amount entry. Amounts are entered numerically, totals are calculated locally, and the app must validate before commit. A successful save creates a unique immutable document identifier and must not create duplicates on repeated taps.

## 6. Limits and half-payout markers
Limits are configured independently for each exact number in TWO_TOP, TWO_BOTTOM, THREE_TOP, and THREE_TOAD. A number's limit in one category never consumes or blocks capacity in the other category. Explicitly blocked numbers are managed separately from monetary limits. Half-payout status is configured per number/category in the period's limit rule; the sales screen has no manual full/half selector. Posting resolves and stores the status automatically. The receipt, history, reports, and settlement read the stored status. If no half-payout rule applies, the line is full payout.

Approved half-payout formula: keep the full actual stake and apply 50% of the configured normal payout rate to that line. Example: 100 baht at 70x uses 35x, producing 3,500 baht. The displayed stake must remain 100 baht; do not halve the recorded stake.

## 7. Customers and credit
Cash customers may be anonymous or use a generic label. Credit customers require a complete identity record as configured before a credit sale can be saved. Credit balances are derived from immutable sale and payment ledger entries. Support full and partial payments with timestamps and per-customer history.

## 8. Documents and immutability
After the user confirms and saves a sale document, its source document and lines cannot be edited or deleted. If incorrect, the user may cancel the entire bill; the app must retain the original bill and write a separate immutable cancellation record containing the document ID, original amount, cancellation timestamp, reason, and actual cash refunded (in satang). For cash bills, the user records the amount physically returned to the customer, from zero through the original bill amount; zero means no cash was actually returned/received. This refund amount is not inferred from the cancelled bill amount. Credit bills cannot record a cash refund, and bills with posted credit payments require a separate payment-reversal workflow. The cancelled bill is visibly marked in history and excluded from active sales, limits, reports, credit balances, and result matching. A replacement purchase must be entered as a completely new bill; editing the original or silently rewriting its lines is prohibited. A period with a recorded result/settlement cannot be cancelled. Database-level protections prevent update/delete of posted source rows and cancellation records.

## 9. Receipts
After a successful sale commit, generate a receipt image locally and store it on-device, linked to its source document. Include document ID, period, timestamp, number/category/amount lines, total, and half-payout labels where applicable. Receipt generation failure must be recoverable from the source document without duplicating the sale.

## 10. Results and settlement
- The app checks exactly two public result sources: LotteryChecks (primary) and HuayReview (secondary). GLO is not connected to the app and no GLO API, endpoint, scraping, or automated GLO retrieval is permitted.
- Both sources must return a validated result for the exact configured draw date. Validate the six-digit first-prize number and two-digit last-two result as text, preserving leading zeroes.
- Compare the complete first-prize number and official last-two number from both sources. If both values match, the app silently confirms the result and derives THREE_TOP from the last three digits of first prize, TWO_TOP from the last two digits of first prize, THREE_TOAD by permutation, and TWO_BOTTOM from the last-two result. Do not display an edit/override control or interrupt the user when the sources match.
- If the two sources disagree, immediately show both source values separately, alert the user, and block payout calculation, settlement snapshots, and sales-after-payout summaries. Offer manual adjudication only in this mismatch state. The user opens the GLO website independently, checks the official result, and enters the six-digit first prize and two-digit last-two result. Store the manually confirmed result, timestamp, source-check references, and audit event. Never silently choose one source.
- If either source is unavailable, returns malformed data, or has no result for the configured draw date, show that verification is incomplete and block payout calculation and settlement. A cached result from an older check must not be treated as a fresh successful comparison.
- Store every source check, including failures, as an append-only local record with source ID, draw date, first-prize number/last-two values when available, source URL, retrieval timestamp, status, and failure reason. A later source check invalidates an earlier confirmation until the latest checks are reconciled again.
- LotteryChecks public API attribution/backlink and rate-limit conditions must be observed. HuayReview dataset is CC BY 4.0 and requires attribution, license link, and indication of changes. The app must preserve attribution in the result screen and project documentation.
- Public source results are cached locally and remain inspectable offline. Offline access does not create a new verification; only a fresh, successful pair of source checks can automatically confirm a result.
- Payout rates are independent for all four sale categories. A period snapshot stores the applicable rates and configuration version. A snapshot may be created only after two-source matching or a completed manual adjudication.
- Approved half-payout formula: retain the full actual stake and apply 50% of the normal configured payout rate to that line. Example: 100 baht at 70x uses 35x and pays 3,500 baht. Do not reduce the recorded stake. Payout is calculated per winning line and rounded HALF_UP to the nearest satang before line payouts are summed. Sales after payout = active gross sales minus total winning payouts; this is not net profit and does not include cash refunds for cancelled cash bills. Actual refunds are shown separately in period reports and CSV exports.

## 11. Reports
Reports are derived from source ledgers. Cancelled bills remain visible in sales history and period cancellation summaries but are excluded from active sales, limits, credit balances, and result matching. Actual cash refunds attached to cancellations are summed and displayed separately; they are not silently netted against gross sales or winning payouts. Period/day reports include active sales, category totals (TWO_TOP, TWO_BOTTOM, THREE_TOP, THREE_TOAD), cash/credit breakdown, receivables, payments, outstanding balances, half-payout entries, winning payouts, and sales-after-payout totals when a rate snapshot exists. Cancelled bill history is inspectable in sales history and period summaries with its reason, timestamp, and original amount. CSV export includes active sales and a separate cancellation summary. Sales-after-payout is not net profit and excludes cash-refund accounting.

## 12. Storage and reliability
Use Room/SQLite with foreign keys, indexes, transactions, stable IDs, duplicate protection, integrity checks, and local persistence across app restarts. Handle interrupted writes, low storage, corrupt database, incorrect period/customer selection, device clock drift, and restore failures.

## 13. Backup and restore
Manual local backup, encrypted export, version validation, integrity check before restore, and manual device-to-device transfer. No automatic cloud backup or sync.

Implemented phase-7 backup format:
- The user supplies and confirms a passphrase of at least 10 characters; the app derives a 256-bit key with PBKDF2-HMAC-SHA256 (210,000 rounds) and encrypts with AES-256-GCM using a fresh random salt and nonce for every backup.
- One archive contains the Room/SQLite database and local receipt images. On Android 10 and newer it is created directly in the device's Downloads/Lotto folder through MediaStore; older supported Android versions use the app-specific Documents directory. No cloud document picker or network upload is used for export.
- Restore authenticates/decrypts before extraction, rejects unsafe archive paths and oversized archives, checks SQLite integrity, foreign keys, schema version, and required Lotto tables, then replaces the local database and receipts with rollback protection.
- The backup passphrase is not stored by the app and cannot be recovered. Losing it makes that backup unreadable.
- Supported restore schema versions are 1 through 11, subject to the app's registered Room migrations. Restore never accepts a database newer than the app.
- Android Auto Backup is disabled in the manifest. Backup files are user-managed and must be stored securely.

## 14. Security
No in-app PIN, user accounts, staff roles, or per-module permissions. The device's Android screen lock is the device-level access protection. Internet access is used only to retrieve public results and published calendar pages from LotteryChecks and HuayReview; the app does not connect to GLO and must never transmit private customer, sales, credit, payment, or receipt data. Core sales, customer, credit, report, and backup functions must work offline. Preserve immutable records and audit events. Use a passphrase-derived key for exported backup encryption.

Implemented protections:
- Application data remains inside the Android app sandbox; no cloud database, backend, account service, or synchronization. Calendar audit records are stored locally in an append-only private app file.
- Android system backup is disabled (allowBackup=false).
- Exported backup data is encrypted with a portable passphrase-derived key and authenticated encryption; the passphrase is held only in memory during the operation and cleared afterwards.
- Restore validates contents before replacement and rolls back to the prior database/receipts if installation or Room re-open validation fails.
- In-app PIN enrollment, user accounts, roles, and per-module permissions are explicitly out of scope. The backup passphrase is separate from device screen-lock security.

## 15. Acceptance tests
Install/launch; fetch LotteryChecks draw-calendar and HuayReview annual calendar pages; parse future Thai dates; compare ordered upcoming dates; immediately notify on date disagreement; deduplicate repeated mismatch alerts; show both source dates and manual date confirmation; append source-check and manual-decision audit records; invalidate a manual decision when the source-date signature changes; cached calendar stale labeling; daily WorkManager refresh under network constraint; reminder targeted for seven days before draw; immediate late-confirmation reminder when first confirmed after the seven-day target; confirmed schedule-change notice; Android notification permission denied fallback; create period; upcoming calendar retrieval from both sources; same-date confirmation; one-source provisional state; date disagreement; missing/malformed/unavailable calendar source; no inferred date creation; daily refresh and rate-limit handling; 7-day reminder; late confirmation inside the 7-day window; confirmed date-change audit and reminder rescheduling; notification permission denied/in-app fallback; cached calendar visibility and stale labeling offline; create period; accept only TWO_TOP, TWO_BOTTOM, THREE_TOP, and THREE_TOAD sales with matching digit lengths; reject all unsupported categories and wrong digit lengths; preserve leading zeroes; enforce independent limits and blocked numbers; auto-label half-payout; cash/credit customers; partial/full payments; receipt generation; immutable confirmed bills; cancellation audit, duplicate-cancel rejection, cancelled-bill exclusion from limits/reports/credit/results; LotteryChecks and HuayReview result retrieval and exact draw-date validation; matching, mismatch, and unavailable-source states; manual GLO verification only after mismatch; append-only source audit; result caching and offline access; settlement gating; payout totals; reports; backup/restore; airplane-mode core operation; process death/restart; and failure recovery on a physical Android device.

Phase-7 automated acceptance coverage includes encryption/decryption round trip, wrong-passphrase rejection, tamper detection, and minimum passphrase length. Restore validation additionally checks archive paths, archive size, SQLite integrity, foreign keys, schema version, required tables, and rollback on replacement failure. Cancellation records also persist actual cash refunded as a separate immutable amount. Manual cross-device restore and physical-device interruption testing remain required.

## 16. Remaining implementation gates
1. Complete device-level tests for WorkManager delivery, Android notification permission, OEM battery restrictions, and period/calendar schedule changes. Android may defer background execution and notifications.
2. Validate parser behavior against live HTML changes and source disagreements; fail closed if either public page no longer yields validated future dates.
3. Complete calendar acceptance tests for matching, mismatch, missing dates, malformed data, outages, date changes, stale/offline cache, late confirmation, and notification scheduling.
4. Complete live real-network integration tests for the LotteryChecks and HuayReview request/response contracts, including missing draws, malformed data, rate limits, and source outages.
5. Verify cash-refund accounting against representative cancellation cases, including zero, partial, and full cash refunds.
6. Verify payout totals and sales-after-payout report reconciliation end to end using representative sale/result fixtures.
7. Preserve LotteryChecks attribution/backlinks and API rate limits, and HuayReview CC BY 4.0 attribution/license/change notices. Review all applicable legal and commercial-use requirements before distribution.

This project is a non-commercial research prototype. It retrieves public results from LotteryChecks and HuayReview under their published reuse terms and attribution requirements. The app does not retrieve GLO data directly; the user independently consults the GLO website when a manual check is required. Any wagering, payout, or related functionality must undergo applicable legal/compliance review before real-world operational use, regardless of commercial intent. Current payout summaries are deterministic calculations from saved results and rate snapshots, but must not be represented as net profit or a final cash ledger until all cash received, cash refunds, and other cash movements are fully reconciled. Commercial distribution or use is outside the current project scope and requires a separate license review.

## 17. Build and delivery
- GitHub Actions runs Android lint, Room/database unit tests, and debug APK assembly on the offline development branch.
- The workflow verifies that the APK exists and is non-empty, generates a SHA-256 checksum, and records the source commit, branch, and workflow run in build metadata.
- The APK, checksum, build metadata, and Room schema snapshots are uploaded as one short-retention test artifact.
- Debug APKs are test builds only. A signed production release, key custody, and release signing process are not enabled in this phase.


## 18. Draw-calendar implementation notes (0.4)
- The app fetches `https://lotterychecks.com/draw-calendar` and HuayReview annual pages `https://huayreview.net/calendar-<Buddhist Era year>/` for the current and following year. These are public HTML calendar pages; the app does not treat the LotteryChecks result API or HuayReview historical-results dataset as a future-calendar API.
- Calendar checks are stored in an append-only private app file (`draw_calendar_checks.log`) with source ID, status, ordered ISO dates, source URL, retrieval timestamp, and failure reason. No customer or transaction data is included.
- The app confirms a draw date automatically only when both current, successful sources agree in ordered upcoming-date sequence. A disagreement immediately triggers a deduplicated local notification showing the first upcoming date from each source. The calendar screen provides a manual date-entry and confirmation flow; the user must independently verify the date before confirming. Manual decisions are stored separately in an append-only local audit file with timestamp, selected date, and a signature of both source date lists. A manual decision applies only while that exact disagreement signature remains unchanged; a changed source schedule requires a new decision.
- Missing/invalid calendar data, source failure, or stale checks never produce automatic confirmation. Cached records are viewable but expire for confirmation after 36 hours. A manual confirmation is explicitly labeled as user-confirmed and is not represented as agreement between the two websites.
- WorkManager performs a network-constrained daily refresh. The target reminder is the date exactly seven calendar days before the draw. If a date is first confirmed after that target date, the app sends an immediate late-confirmation notification stating the remaining days. If background execution is deferred by Android/OEM policy, actual delivery can occur later; the app cannot guarantee exact-time delivery.
- Notifications require Android notification permission. If denied, the in-app calendar still displays source data and status; background notification delivery is not guaranteed when Android/OEM battery policies defer work.
- Calendar date confirmation is separate from draw-result verification and does not automatically create or alter sales periods. The user must review and create/adjust a period using the confirmed calendar date.
