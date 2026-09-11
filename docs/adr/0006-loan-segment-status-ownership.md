# ADR-0006: Ownership of loan segment status ("Owing" / "Paid-Off")

## Status

Accepted (closed during Architect Kickoff, 2026-09-11)

## Context

RFP §3.3 requires the system to categorize accounts into "Owing" and "Paid-Off" segments and to move an account from Owing to Paid-Off automatically upon full liquidation of its memo balance. Two contexts have a legitimate claim on this data:

- `account-verification` (Account and Loan Verification Engine) is where the diagram places account/loan-level state.
- `memo-balance` (Memo Ingestion & Balance Engine) is the context that actually detects liquidation, since it owns balance calculation and adjustment.

Letting this drift — both contexts independently tracking "is this account paid off" — would create exactly the kind of duplicated, inconsistent state the RFP's current Excel-based process already suffers from.

## Decision

`account-verification` is the system of record for loan segment status. `memo-balance` publishes a liquidation event (over the Kafka bus, per ADR-0001) when a memo balance reaches zero; `account-verification` consumes that event and performs the Owing → Paid-Off transition, escalating any discrepancy to Credit Admin per RFP §3.3.

Consistency requirement: eventual consistency via Kafka is acceptable for this transition. No read-after-write guarantee was requested for the Credit Admin-facing view; revisit as its own decision if a future spec surfaces a real need for one.

## Consequences

- `account-verification`'s spec (Phase 4) must define the liquidation-event schema it consumes and the discrepancy-escalation path to Credit Admin.
- `memo-balance`'s spec must define exactly when a liquidation event is published (the zero-balance condition) — this is the Integration Contract gate's (enterprise-review axis 5) reference point for this event.

## Source

RFP §3.3.
