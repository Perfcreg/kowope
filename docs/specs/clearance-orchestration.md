> **DRAFT — not yet published.** See `docs/specs/memo-balance.md` for why (gh CLI not installed). Publish as a GitHub issue and delete this file once it's available.

# Spec: clearance-orchestration

## Problem Statement

RFP §3.8/§4.6 require clearing a customer's name from ICAD within 24–48 hours of liquidation. Today this is done manually, causing delay and no visibility into where a clearance stands.

## Solution

`clearance-orchestration` is a narrow workflow — per the architect's delta in `CONTEXT-MAP.md`, ICAD clearance only, no general exception handling — that starts when an account is liquidated, drives the ICAD clearance through `integration`'s ICAD Integration Adapter, tracks the SLA, and escalates if it's at risk or fails.

## User Stories

1. As clearance-orchestration, I want to start a clearance workflow when memo-balance publishes a `MemoLiquidated` event, so ICAD clearance begins automatically rather than waiting for someone to notice liquidation happened.
2. As clearance-orchestration, I want to request name clearance from ICAD through the ICAD Integration Adapter, so I don't need to know ICAD's own API/file shape.
3. As clearance-orchestration, I want to track elapsed time against the 24–48 hour SLA (RFP §3.8), so I know which clearances are on track and which are at risk.
4. As clearance-orchestration, I want to notify stakeholders (via `shared-platform`'s notification-service) when a clearance is overdue, so nobody has to check manually.
5. As clearance-orchestration, I want to notify stakeholders when ICAD reports a discrepancy during clearance, so RFP §4.6's discrepancy-notification requirement is met.
6. As a Credit Admin or Recovery Team user, I want to query the clearance status of any liquidated account, so I can answer a customer's question about their ICAD status without checking ICAD directly.
7. As clearance-orchestration, I want to retry a failed clearance request before escalating, so a transient ICAD outage doesn't immediately become a human escalation.
8. As clearance-orchestration, I want every clearance request, retry, and outcome recorded via `audit-trail-lib`, so the full clearance history for an account is auditable.
9. As clearance-orchestration, I want to measure the actual time taken for ICAD clearance after liquidation, so process metrics (RFP §3.12) can be computed from real data rather than estimated.

## Implementation Decisions

- Gradle module `services/clearance-orchestration` (ADR-0007). Consumes `MemoLiquidated` from memo-balance (Kafka, ADR-0001); calls `icad-integration-adapter` (the `integration` context) — either via a request/response event pair or a thin REST call the adapter exposes — and consumes the adapter's clearance-outcome event.
- Redis for SLA/timer state (ADR-0003) — tracking how long a clearance has been pending is exactly the ephemeral, fast-lookup state Redis suits.
- Depends on `libs/audit-trail-lib`.

## Testing Decisions

- Seam: the Kafka consumer/producer boundary (in: `MemoLiquidated`, ICAD outcome events; out: escalation triggers to notification-service) and the REST query API. Testcontainers for Kafka and Redis (ADR-0009).

## Out of Scope

- General balance/payment exception handling — moved to `memo-balance` per the architect's delta; not this context's job.
- The ICAD Integration Adapter's own implementation — `integration`'s spec.

## Further Notes

- This is the narrowed context per the architect's delta in `CONTEXT-MAP.md`. If a future spec adds non-ICAD exception logic here, that's a sign the boundary is drifting back toward the diagram's original combined orchestrator — flag it rather than let it happen silently.
