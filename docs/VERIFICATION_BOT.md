# Verification Bot — Architecture and Operating Rules

## Status

Design baseline only. The current Android application is offline-first and declares no INTERNET permission. No scheduled web polling, cloud API, push notification channel, or website adapters are implemented by this document. Do not present the bot as operational until those components are implemented, configured, and tested end-to-end.

## Agreed operating schedule

All user-facing dates and times use `Asia/Bangkok`.

- Calendar verification: daily at 00:00 Thailand time.
- Result verification: on each scheduled draw date, from 15:00 through 17:00 Thailand time.
- Result polling interval: every 5 minutes during that window.
- At 17:00, record each source that has not published as `NOT_UPDATED`; do not misclassify it as a mismatch.
- Prepare a draw at least 7 calendar days before its currently confirmed draw date. If a reliable announcement establishes the date earlier, preparation may begin immediately.
- If the draw date changes, recalculate the preparation date and preserve the previous decision in audit history.

GitHub Actions cron is expressed in UTC: 00:00 Thailand = 17:00 UTC on the previous day; 15:00–17:00 Thailand = 08:00–10:00 UTC. GitHub scheduled workflows can be delayed and are not a precision timer. Production polling must use a scheduler that can meet the operational service level, or explicitly expose delayed/missed runs.

## Sources

The proposed four sources are:

1. Government Lottery Office (GLO) — official authority and primary reference.
2. Sanook — supplementary publisher.
3. Thairath — supplementary publisher.
4. Kapook — supplementary publisher.

Each source adapter must have its own canonical URL, retrieval timestamp, HTTP status, parser version, normalized payload, and evidence reference. Confirm the exact page/API endpoints and terms of use before enabling an adapter. Do not bypass CAPTCHA, authentication, rate limits, or other access controls.

The three supplementary publishers may syndicate or reuse GLO data; they are cross-check signals, not guaranteed independent witnesses.

## Consensus and safety policy

- Count only successfully retrieved, parse-valid, current data for a specific draw date.
- A source that is unavailable, stale, or not yet published is `UNAVAILABLE`, `STALE`, or `NOT_UPDATED`, not a vote for either date/result.
- A 3-of-4 agreement creates a `MAJORITY_CANDIDATE`, not an automatically authoritative result.
- A 2–2 split, fewer than three valid sources, or conflicting draw-date identity creates `UNRESOLVED` and requires another check/manual review.
- If GLO disagrees with a 3-source majority, raise a high-priority alert, retain both claims and all evidence, and do not silently overwrite the app's official calendar or result.
- The app's financial calculations, winning bills, and payout records must only consume an explicitly approved result. The verification bot must never write directly into financial or prize tables.
- Never infer an exceptional draw date from the normal 1st/16th pattern. Exceptional dates require published evidence.

## Data flow

1. Scheduled cloud worker starts a run with a unique run ID and Thailand-local draw date.
2. Independent source adapters fetch calendar or result pages/API resources.
3. Parsers normalize dates and every result category while preserving leading zeroes as strings.
4. Validation checks draw-date identity, category completeness, number length, duplicate values, freshness, and parser confidence.
5. Comparison engine produces per-field agreement, source status, majority candidate, and GLO conflict status.
6. Append-only audit storage records raw evidence metadata, normalized values, parser version, decision, and timestamps.
7. A secure backend API exposes read-only status/history to the Android app; notification delivery is a separate backend capability.
8. Android displays source status, timestamps, evidence links, conflicts, and review state. It does not treat an unapproved candidate as official.

## Required backend contract

The Android app currently has only an encrypted local Room database and no internet capability. A production implementation therefore requires a separately deployed backend/API and authentication design before adding Android networking. Do not put API secrets in the APK or commit credentials to the repository.

Minimum read endpoints:
- `GET /v1/verification/status`
- `GET /v1/verification/runs?limit=...`
- `GET /v1/verification/draws/{drawDate}`

Minimum write path is worker-to-backend only, authenticated with a server-side secret. The Android client must be read-only for verification evidence.

## Implementation gates

1. Verify official GLO API/resource contract and exact endpoints for Sanook, Thairath, and Kapook.
2. Implement parsers with fixture-based tests for current, historical, incomplete, stale, malformed, and changed-page cases.
3. Implement consensus and calendar preparation policies as pure tested logic.
4. Implement scheduled worker, durable audit store, retry/backoff, idempotency, and alert delivery.
5. Deploy authenticated backend API and connect Android read-only UI.
6. Run controlled end-to-end tests across normal draws, postponed draws, source outage, 3–1 conflict, 2–2 split, partial result publication, and 17:00 not-updated handling.

## Acceptance criteria

- No calendar date is promoted solely because it is the 1st or 16th.
- The preparation date is never later than draw date minus 7 days when the draw date is known.
- A 3-of-4 candidate is visible with its supporting sources and any GLO conflict clearly marked.
- Missing/stale sources are not counted as dissenting votes.
- Every conflict creates an auditable alert and never mutates approved financial data.
- Result polling records each 5-minute attempt and final 17:00 source states.
- All date/time comparisons use `Asia/Bangkok`; all numeric lottery values are stored as strings.
