# ADR-0013: Apache Fineract also substitutes for Vision

## Status

Accepted

## Context

The Vision ETL Connector's job (integration spec User Stories 4-5) is to pull an external system's authoritative balance figure so `memo-balance` can reconcile against it. Real Vision access isn't available, same as Finacle (ADR-0012). Fineract's savings account listing (`GET /clients/{id}/accounts`) already returns `accountBalance` directly — a real, live, authoritative balance figure from a real system, not a mock.

## Decision

The Vision ETL Connector polls the same Fineract instance ADR-0012 already established as the Finacle substitute, reading `accountBalance` as the "Vision-reported" figure. This is a second, independent adapter (separate Camel route, separate module) — it happens to point at the same physical instance, not because Fineract genuinely models both core banking and a separate balance-calculation engine, but because it's the only real system available to poll, and its balance figure is a legitimate real number to reconcile against.

## Consequences

- In this local/dev setup, the "memo balance" and the "Vision balance" ultimately derive from the same underlying Fineract ledger, so a real discrepancy (per `memo-balance`'s `checkForVisionDiscrepancy`) will only arise from a genuine calculation difference between the two adapters' logic — not from two independent systems disagreeing. That's a real limitation of not having two real, independent systems; it doesn't invalidate the adapter's own correctness.
- Both adapters point at `fineract.base-url` independently (their own `FineractProperties`), so pointing one at a real Vision system later and leaving the other on Fineract is a one-line config change per adapter, not a shared-client refactor. `FineractClient` here is an interface (`FineractHttpClient` its implementation) so that swap has an actual seam to land on, not just a config value to change.
- **Security note added 2026-09-12 (enterprise-review)**: same gap as write-off-detection-service's own ADR-0012 note — the Fineract HTTP calls are plaintext loopback (accepted local-dev risk), and this adapter's Kafka publish has no encryption or SASL configured at all, carrying account numbers and balance figures into Kafka's on-disk log segments unencrypted (RFP §4.3). Not fixed in code here, for the same reason: no adapter in this repo secures its Kafka producer yet, and this repo's own `docker-compose.yml` Kafka broker is plaintext-only — a system-wide gap, not a one-adapter fix.

## Source

Integration spec (issue #2) User Stories 4-5; ADR-0012.
