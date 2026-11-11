# Verification Bot — Architecture, Access Policy, and Operating Rules

## Current implementation status

The Android app must not connect to lottery websites or their APIs. The bot is a separate scheduled process. Its approved output is intended to be transferred into the app as a signed verification export; the app consumes only bot-approved data.

The legacy 1st/16th date generator, manual calendar editing screen, manual result-entry screen, and Home result-entry prompt have been removed from the active app flow. Existing draw dates and historical results remain archived so sales, credit, winning bills, payout records, and financial reports are not destroyed.

Operational calendar queries now return only rows with source `BOT_VERIFIED`. Sale windows are calculated only from these bot-verified dates. The result persistence path rejects results without `BOT_VERIFIED`, an explicit `APPROVED_3_OF_4` or `MANUAL_REVIEW_APPROVED` decision, a verification run ID, and an approval timestamp inside the 15:00–17:00 Thailand window.

**The bot is not operational yet.** Source adapters, scheduler, durable bot audit storage, signed export generation, import UI, signature verification, and end-to-end tests remain to be implemented. Until those gates pass, the app must fail closed: no bot-approved date means no sale window; no bot-approved result means no new payout calculation.

## Source access policy — mandatory

Do not start an automated fetcher merely because a page is publicly viewable. Check the publisher's terms, robots policy, API licence, and written permissions first. Never bypass CAPTCHA, login, rate limits, IP restrictions, or other access controls.

| Source | Finding | Bot status |
|---|---|---|
| Government Lottery Office (GLO) Open Data | The official data catalog lists the lottery results dataset as public data under Creative Commons Non-Commercial (Any) and points to an API guide. The exact API contract and allowed use for this lottery-sales application still need review. | `BLOCKED_LICENSE_REVIEW` until API contract and commercial-use permission are confirmed |
| Sanook | Its Terms of Service prohibit systematic or automated data collection, scraping, and crawling without prior written consent. | `BLOCKED_WRITTEN_PERMISSION` |
| Thairath | Its terms restrict copying, republishing, modifying, and exploiting site content without prior written permission. Automated crawling permission is not established by the terms reviewed. | `BLOCKED_WRITTEN_PERMISSION` |
| Kapook | Its disclaimer states that site text, images, logos, databases, and other materials are protected intellectual property and restricts use to personal lawful purposes. It does not clearly grant automated extraction rights. | `BLOCKED_WRITTEN_PERMISSION` |

Primary policy references:
- GLO Data Catalog: https://gdcatalog.glo.or.th/dataset/dataset_c4-9_01
- Sanook Terms of Service: https://www.sanook.com/termservice/
- Thairath Terms and Conditions: https://www.thairath.co.th/terms-condition
- Kapook Disclaimer: https://www.kapook.com/disclaimer/

Until written permission or an explicitly permitted API/feed is documented for Sanook, Thairath, and Kapook, do not fetch them automatically. Do not count blocked sources as votes. With fewer than three valid sources, the result must remain `UNRESOLVED`. GLO Open Data's non-commercial licence must not be assumed to cover a business workflow that records lottery sales; obtain clarification or permission before activating it for that purpose.

## Operating schedule

All dates and times use `Asia/Bangkok`.

- Calendar verification: daily at 00:00 Thailand time.
- Result verification: on each confirmed draw date, from 15:00 through 17:00 Thailand time.
- Result polling target: every 5 minutes during that window, only for sources whose access has been approved.
- At 17:00, record sources that have not published as `NOT_UPDATED`; do not misclassify them as a mismatch.
- Prepare a draw at least 7 calendar days before its confirmed draw date. If a reliable announcement establishes the date earlier, prepare immediately.
- If the date changes, recalculate the preparation date and preserve the prior decision in audit history.

GitHub Actions cron uses UTC: 00:00 Thailand = 17:00 UTC on the previous day; 15:00–17:00 Thailand = 08:00–10:00 UTC. GitHub scheduled workflows can be delayed and are not a precision timer. The schedule must record missed or delayed runs.

## Consensus and safety policy

- Count only successfully retrieved, parse-valid, current data from an approved source for the exact draw date.
- `BLOCKED_*`, unavailable, stale, or not-yet-published sources are not votes.
- A 3-of-4 agreement creates a `MAJORITY_CANDIDATE`, not an automatically authoritative result.
- A 2–2 split, fewer than three valid sources, or conflicting draw-date identity creates `UNRESOLVED`.
- If GLO disagrees with a 3-source majority, raise a high-priority alert, retain both claims and all evidence, and do not silently overwrite the calendar or result.
- Supplementary publishers may syndicate GLO data; they are not guaranteed independent witnesses.
- Never infer an exceptional draw date from the regular 1st/16th pattern.
- The bot must never write directly into sales, credit, winning-bill, payout, or profit tables.
- Existing approved results must not be silently replaced. Corrections require a separately audited approval flow.

## Bot-to-app transfer contract

The app is offline with respect to external lottery sources. The bot produces an export file; the operator transfers that file to the device and imports it locally. No website credentials, API keys, or bot secrets may be embedded in the APK.

The future export format must include:
- schema version, unique run ID, generation timestamp, and `Asia/Bangkok` timezone;
- draw date and data kind (`CALENDAR` or `RESULT`);
- decision status and approval status;
- one record per source, including source ID, access status, canonical URL, checked timestamp, HTTP status where applicable, parser version, and evidence digest;
- normalized date/result values, with lottery numbers stored as strings to preserve leading zeroes;
- a cryptographic signature generated by a private key held only by the bot; the Android app verifies it using a public key bundled with the app.

The app must reject unsigned, invalidly signed, unsupported-version, stale, incomplete, or non-approved exports. `MAJORITY_CANDIDATE` alone is not sufficient; the export must carry `APPROVED_3_OF_4` or `MANUAL_REVIEW_APPROVED`. Import must be idempotent by run ID and draw date. Calendar import may mark a row `BOT_VERIFIED`; result import must call the bot-provenance-only result repository method.

## Required implementation gates

1. Obtain documented permission or permitted API/feed terms for each source. Keep blocked adapters disabled.
2. Confirm the GLO Open Data API contract and licence scope for the intended business use.
3. Implement source adapters only for approved sources, with independent parsers and fixture tests.
4. Implement calendar preparation, result normalization, validation, and 3-of-4 consensus as pure tested logic.
5. Implement scheduled runs, retry/backoff, idempotency, append-only audit storage, and alerts.
6. Implement signed export generation and offline Android import with signature verification.
7. Run end-to-end tests for normal draws, postponed draws, source outage, 3–1 conflict, 2–2 split, partial result publication, invalid signatures, replayed files, and 17:00 not-updated handling.

## Acceptance criteria

- No date is promoted because it is the 1st or 16th.
- Only bot-verified dates drive sale windows and the calendar screen.
- Only results with bot provenance can enter the new result/payout pipeline.
- Missing or blocked sources are not counted as dissenting votes.
- Every conflict creates an auditable alert and never mutates approved financial data.
- Existing historical sales, credit, draw results, winning bills, and payout records remain intact.
- The Android app makes no direct request to the four websites or their APIs.
- The bot is not labelled operational until the scheduled fetch, audit, signed export, offline import, and app-side verification all pass.
