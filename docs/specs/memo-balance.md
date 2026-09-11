> **DRAFT — not yet published.** `gh` CLI isn't installed on the primary dev machine, so this couldn't go straight to a GitHub issue per `docs/agents/issue-tracker.md`. Once `gh auth login` works, publish this as an issue (`gh issue create --title "Spec: memo-balance" --body-file docs/specs/memo-balance.md --label ready-for-agent`) and delete this file — the issue becomes the source of truth, not this draft.

# Spec: memo-balance

## Problem Statement

UBA's current memo balance process runs on manual Excel tracking. Branch Operations, Transaction Services, and Credit Admin have no real-time, authoritative view of which accounts have been written off to memo, what they currently owe, or whether a balance has moved since the last check. Detection of write-offs depends on someone noticing a Finacle narration by hand; balance adjustments for partial payments and approved write-offs are applied manually, days after the fact; and payment discrepancies (unallocated payments, mismatched balances) surface only when someone happens to look. This delays liquidation updates, misstates real exposure, and blocks downstream processes (ICAD clearance, loan segment status) that depend on knowing precisely when a memo balance reaches zero.

## Solution

The Memo Ingestion & Balance Engine (`memo-balance` context) becomes the single system of record for memo accounts. It consumes detection events published by the `integration` context's adapters (Write-Off Detection Service scanning Finacle narrations, Vision ETL Connector, Excel Import Service), creates or updates the corresponding Memo record, calculates and adjusts the balance in real time as partial payments and approved write-offs occur, flags exceptions (unallocated payments, balance discrepancies) to Transaction Services, and publishes a liquidation event the moment a memo balance reaches zero so `account-verification` can transition the account from Owing to Paid-Off (ADR-0006). Every mutating action is recorded through the shared `audit-trail-lib` contract (ADR-0005).

## User Stories

1. As the Write-Off Detection Service, I want to publish a `MemoDetected` event when a Finacle narration matches "written off" (case-insensitive, configurable variants), so that memo-balance can create a Memo record without a human noticing it manually.
2. As the Memo Ingestion & Balance Engine, I want to create a new Memo record when a `MemoDetected` event references an account with no existing record, so that every write-off is tracked from the moment it happens.
3. As the Memo Ingestion & Balance Engine, I want to update the existing Memo record — never create a duplicate — when a `MemoDetected` event references an account that already has one, preserving the earliest transfer date, so repeated detections don't fragment the account's history.
4. As a Credit Admin user, I want every newly detected Memo account to appear with status "Imported — Pending Review", so I know which accounts need my attention.
5. As the Memo Ingestion & Balance Engine, I want to capture and store the account balance/outstanding from the detection event at the time of detection, so the starting balance for tracking is accurate.
6. As the Memo Ingestion & Balance Engine, I want to capture and store the effective transfer date/time from the Finacle posting, so reports (RFP §3.15) can correctly compute period movement.
7. As the Memo Ingestion & Balance Engine, I want to persist Account Number, Customer ID, Branch/SOL, Currency, Transaction/Posting Reference, and full Narration text with every Memo record, so Transaction Services and Credit Admin have full context without going back to Finacle.
8. As a Transaction Services user, I want the memo balance recalculated in real time whenever a partial payment is applied, so I always see the current outstanding, not a stale figure.
9. As a Transaction Services user, I want the memo balance recalculated in real time whenever a bank-approved write-off adjustment is applied, so approved write-offs are reflected immediately.
10. As the Memo Ingestion & Balance Engine, I want to leverage Vision's own balance-calculation algorithm rather than reimplementing it, so memo balances stay consistent with Vision's figures and don't drift.
11. As the Memo Ingestion & Balance Engine, I want to detect when a partial payment can't be fully allocated to a Memo account, so Transaction Services is alerted to an unallocated payment rather than it sitting unresolved.
12. As the Memo Ingestion & Balance Engine, I want to detect a discrepancy between the calculated memo balance and an incoming balance figure from Vision, so Transaction Services can investigate before it compounds.
13. As a Transaction Services user, I want every detected exception (unallocated payment, balance discrepancy) to appear as an actionable item, so nothing is silently dropped.
14. As the Memo Ingestion & Balance Engine, I want to publish a `MemoLiquidated` event the instant a memo balance reaches exactly zero, so `account-verification` can transition the account to Paid-Off (ADR-0006) without polling memo-balance.
15. As the Memo Ingestion & Balance Engine, I want to publish a `MemoBalanceAdjusted` event on every balance change (not only liquidation), so `reporting` (RFP §3.15) can compute period movement without recomputing it from raw transactions.
16. As a Credit Admin user, I want to see the full audit trail of every balance adjustment on a Memo account — trigger, before/after balance, timestamp — so I can investigate any account on demand.
17. As a Credit Admin user, I want to upload and retrieve documents related to a Memo account (non-indebtedness letters, verification records, memo file updates), so supporting evidence lives with the account it concerns.
18. As the Memo Ingestion & Balance Engine, I want document files stored in Blob storage (ADR-0003) with only metadata and pointers in Postgres, so the database isn't bloated with binary content.
19. As a Credit Admin user, I want to upload documents in multiple common file formats, so I'm not blocked by format restrictions.
20. As the Memo Ingestion & Balance Engine, I want to reject a `MemoDetected` event missing a required field (Account Number, Currency, Narration) rather than silently creating an incomplete record, so data-quality issues surface immediately instead of corrupting downstream reports.
21. As the Memo Ingestion & Balance Engine, I want every ingestion and balance-adjustment event to write an `AuditEvent` via `audit-trail-lib` (ADR-0005), so the system-wide audit trail (RFP §3.5) is complete without memo-balance inventing its own logging shape.
22. As a Recovery Team user, I want to view a Memo account's current balance and full liquidation history through the memo-balance REST API, so I can advise a customer accurately mid-call.
23. As the Memo Ingestion & Balance Engine, I want the Country/Region context (RFP §3.14, `reference-data-config`) applied to every Memo record, so GL mappings and base currency are correct for cross-border reporting.

## Implementation Decisions

- Module: `services/memo-balance` (Spring Boot, Java 21 — ADR-0007).
- **Inbound**: Kafka consumer for `MemoDetected` events published by `integration`'s adapters (ADR-0001). The consumer is idempotent on Account Number — it de-duplicates rather than creating a second Memo record, preserving the earliest transfer date (RFP §3.13(bis)).
- **Outbound**: Kafka producer publishes `MemoBalanceAdjusted` on every balance change and `MemoLiquidated` when the balance reaches zero (consumed by `account-verification` per ADR-0006).
- **Persistence**: PostgreSQL is the system of record (ADR-0003) for Memo records and balance history; Blob storage holds the actual document files from §3.11, with only metadata/pointers in Postgres.
- Balance calculation delegates to Vision's algorithm via whatever contract `integration`'s Vision ETL Connector exposes, rather than reimplementing it (RFP §3.1).
- Every mutation (detection, adjustment, liquidation, exception raised) calls `AuditLogger.record()` from `libs/audit-trail-lib` (ADR-0005).
- Exposes a REST API (OpenAPI — ADR-0007) for querying Memo accounts, balance, and history, RBAC-scoped per the role table in `CONTEXT-MAP.md` (view-only for CSM, edit for Transaction Services, full for Credit Admin/Recovery Team).
- Balance/payment exception handling (RFP §3.10) lives in this context, not `clearance-orchestration` — this is the architect's delta from the original diagram (see `CONTEXT-MAP.md`).

## Testing Decisions

- Test only through the two public seams: the **REST API** and the **Kafka consumer/producer boundary** — never against internal persistence or calculation internals (`tdd` skill).
- Integration tests use Testcontainers for Postgres and Kafka (ADR-0009), not mocks, for anything crossing those seams.
- No prior art yet in this repo — this is the first context spec, so its test shape (Testcontainers-backed Postgres/Kafka, `MockMvc`/`WebTestClient` for REST) sets the pattern later contexts should follow.

## Out of Scope

- The `integration` context's own detection logic (narration scanning) — that context's own spec.
- ICAD clearance workflow — `clearance-orchestration`'s spec.
- Loan segment status transition logic — `account-verification`'s spec (memo-balance only publishes the triggering event, per ADR-0006).
- Reporting/aggregation of memo data into scheduled reports — `reporting`'s spec (RFP §3.15).
- Country/GL mapping administration — `reference-data-config`'s spec (RFP §3.14); memo-balance only consumes it.

## Further Notes

- RFP §3.13 appears twice in the source document with unrelated content; this spec draws on the second occurrence, labelled §3.13(bis) in `docs/rfp/2025-09-uba-memo-balance-rfp.md`.
- The `MemoDetected` / `MemoBalanceAdjusted` / `MemoLiquidated` event schemas need to be agreed jointly with `integration` (producer) and `account-verification` (consumer) before implementation tickets are cut — sequence this first in `to-tickets`. This is exactly what the Integration Contract gate (`enterprise-review` axis 5) checks later.
