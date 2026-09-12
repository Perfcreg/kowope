# ADR-0015: ICAD pending-clearance state is in-memory only — an accepted, flagged risk

## Status

Accepted

## Context

The integration spec permits "no context-specific persistence beyond each adapter's own retry/dedup state" — the ICAD Integration Adapter's `PendingClearanceStore` is exactly that: the worklist of clearances pushed to ICAD (via `pushAccount`) but not yet resolved (via `fetchAccount`). An enterprise-review pass (2026-09-12, `feature/integration-icad-adapter`) found that an earlier version of this component was a concrete `ConcurrentHashMap`-backed class whose own Javadoc claimed a restart "just means the next poll cycle re-derives nothing lost" — that claim was false. Nothing re-derives the worklist from ICAD; there is no reconciliation endpoint in the modeled contract (ADR-0014) to rebuild it. A restart between a push and its resolution silently and permanently drops that clearance: `clearance-orchestration`'s 24-48 hour SLA (RFP §3.8) would never close, with no error and no alert distinguishing this from a normal in-flight clearance.

## Decision

`PendingClearanceStore` is now an interface; `InMemoryPendingClearanceStore` is its only implementation, still backed by a `ConcurrentHashMap`, and still loses state on restart. This ADR does not fix that risk — Under Kubernetes (ADR-0002), pod restarts are routine, so it is a live, not theoretical, risk — it documents it honestly and names the seam a future fix would use. Building a durable-store implementation (Redis, per ADR-0003) now would be speculative: no other context in this repo has stood up Redis yet, and doing so for this one adapter ahead of a real need would be infrastructure this ticket didn't ask for.

Two things are fixed now, independent of persistence: `IcadClearanceProcessor.pollForOutcomes()` no longer removes a resolved item from the store until the Camel route confirms the outcome was actually published to Kafka (previously it removed on resolution, before publish — a publish failure after exhausted retries silently dropped the event even without a restart). And the publish route's Dead Letter Channel now routes a permanently-failed publish to a `.dlq` topic instead of only logging it, so the *publish-failure* half of this risk has a durable record even though the *restart-while-pending* half still does not.

## Consequences

- The restart-while-pending gap remains open. If this adapter needs real durability before a Redis-backed (or equivalent) implementation is built, the interim mitigation is operational: avoid restarting `icad-integration-adapter` while clearances are known to be pending, and monitor the gap between "pushed" and "resolved" audit events for entries that never resolve.
- `clearance-orchestration` (not yet built) should not assume `IcadClearanceOutcomeEvent` is guaranteed to eventually arrive for every accepted `POST /icad/clearance-requests` call — it should track its own SLA timer independently and treat a missing outcome after 48 hours as an incident to investigate, not solely rely on this adapter's event to close the loop.
- Swapping in a durable `PendingClearanceStore` implementation later requires no change to `IcadClearanceProcessor` or the route — that was the point of extracting the interface.

## Source

Enterprise-review findings (Architecture Conformance, Integration Contract, Security & Compliance axes), 2026-09-12, `feature/integration-icad-adapter`; ADR-0002 (Kubernetes), ADR-0003 (polyglot persistence), ADR-0014.
