# ADR-0013: write-off detection dedup state is in-memory only — an accepted, flagged risk

## Status

Accepted

## Context

An enterprise-review pass (2026-09-12, `feature/integration-write-off-detection`) found that the original scanner had no dedup state at all: the 30-second timer re-evaluated every active client's transactions from scratch on every cycle, so an already-detected write-off was re-published to Kafka and re-audited forever. Integration spec User Story 3 asks for detection to run "continuously against new Finacle movements, not as a one-off batch" — a design that republishes every historical match on every tick is a repeating batch, not a continuous new-movement feed. The integration spec's own Implementation Decisions section anticipates this: "No context-specific persistence beyond each adapter's own retry/dedup state" — dedup state was always meant to live here, in the adapter, not be assumed away.

## Decision

`WriteOffScanner` now checks each transaction against `DetectedWriteOffStore` (keyed by savings-account-id + transaction-id, both stable Fineract identifiers) before publishing or auditing it, and marks it detected immediately after. `DetectedWriteOffStore` is an interface — `InMemoryDetectedWriteOffStore` its only implementation — so a durable implementation can be substituted later without touching `WriteOffScanner`. (Note: the ICAD Integration Adapter's own branch independently applied the identical interface-plus-in-memory-implementation pattern for its own pending-clearance state, in its own ADR numbered separately on that branch — the two fixes arrived at the same shape independently, not by sharing a locally-resolvable ADR reference; each branch's `docs/adr/` only contains what that branch itself added.)

`InMemoryDetectedWriteOffStore` loses its state on restart — an accepted risk, not a solved one. The consequence here is milder than an unbounded silent loss would be: a restart causes at most one extra re-publish-and-re-audit round of whatever write-offs were already detected before the restart (memo-balance's own ticket-02 dedup logic, per `services/memo-balance/CONTEXT.md`, is the actual safety net against a duplicate `MemoDetected` event corrupting state). Building a durable store now, ahead of an actual operational need, would be speculative infrastructure this repo hasn't stood up anywhere yet.

## Consequences

- A restart of `write-off-detection-service` may cause a short burst of duplicate `MemoDetected` events for write-offs detected shortly before the restart. `memo-balance`'s consumer-side dedup (Ticket 02) is the system's actual defense against this, not this adapter's dedup cache — this cache only stops *infinite* re-publishing during normal (non-restart) operation.
- Swapping in a durable `DetectedWriteOffStore` implementation later requires no change to `WriteOffScanner` — that was the point of extracting the interface.
- If a future review finds memo-balance's consumer-side dedup insufficient to absorb this adapter's restart-window duplicates, revisit this ADR rather than assuming the current mitigation is permanent.

## Source

Enterprise-review findings (Spec, Architecture Conformance, Integration Contract axes), 2026-09-12, `feature/integration-write-off-detection`; integration spec (issue #2) User Story 3 and Implementation Decisions.
