> **DRAFT — not yet published.** See `docs/specs/memo-balance.md` for why (gh CLI not installed). Publish as a GitHub issue and delete this file once it's available.

# Spec: account-verification

## Problem Statement

RFP §3.2/§3.3 require verifying a customer's accounts against Vision/ICAD/Memo data and tracking whether each account is Owing or Paid-Off. Today's manual process makes cross-account verification slow, and loan-status drift goes undetected until someone notices.

## Solution

`account-verification` cross-references a customer's accounts against Vision and Memo data, and is the system of record for loan segment status (Owing/Paid-Off — ADR-0006), transitioning an account to Paid-Off automatically when it consumes a `MemoLiquidated` event from `memo-balance`.

## User Stories

1. As account-verification, I want to verify a customer account against Vision data, so RFP §3.2's automated account-verification requirement is met without manual lookup.
2. As account-verification, I want to cross-reference every account associated with a customer, so no related account is missed during verification (RFP §3.2).
3. As account-verification, I want to categorize each account into "Owing" or "Paid-Off", so RFP §3.3's segment tracking is available system-wide.
4. As account-verification, I want to consume a `MemoLiquidated` event from memo-balance and transition the referenced account from Owing to Paid-Off, so the transition happens automatically the instant liquidation completes (ADR-0006), without Credit Admin doing it by hand.
5. As a Credit Admin user, I want to be notified (via `shared-platform`'s notification-service) when a loan-status discrepancy is detected, so I can resolve it promptly (RFP §3.3).
6. As account-verification, I want to detect a discrepancy — e.g., a `MemoLiquidated` event for an account already marked Paid-Off, or an Owing account with no corresponding Memo record — so RFP §3.3's escalation requirement has something concrete to trigger on.
7. As a Recovery Team or Credit Admin user, I want to query an account's current segment and full transition history, so I can see exactly when and why it changed.
8. As account-verification, I want every segment transition (and every detected discrepancy) recorded via `audit-trail-lib`, so the transition history is auditable end to end.
9. As account-verification, I want to verify accounts against Memo files as well as Vision, so accounts that only exist in the Memo system (not yet reflected in Vision) are still verifiable (RFP §3.2).

## Implementation Decisions

- Gradle module `services/account-verification` (ADR-0007), PostgreSQL for account/loan segment state (ADR-0003) — the system of record per ADR-0006.
- Kafka consumer for `MemoLiquidated` (from memo-balance); REST API for querying verification/segment state, RBAC-scoped per `CONTEXT-MAP.md`'s role table.
- Depends on `libs/audit-trail-lib`.

## Testing Decisions

- Seam: the REST query API and the Kafka consumer boundary. Testcontainers for Postgres and Kafka (ADR-0009) — the Owing→Paid-Off transition must be tested against a real consumer, not a mocked one.

## Out of Scope

- How memo-balance decides a balance has reached zero — memo-balance's own spec; account-verification only reacts to the event.
- ICAD clearance — `clearance-orchestration`'s spec.

## Further Notes

- This spec is the consumer half of the `MemoLiquidated` contract that `memo-balance`'s spec defines as the producer half — the schema must be agreed before tickets are cut.
