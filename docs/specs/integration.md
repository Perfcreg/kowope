> **DRAFT — not yet published.** See `docs/specs/memo-balance.md` for why (gh CLI not installed). Publish as a GitHub issue and delete this file once it's available.

# Spec: integration

## Problem Statement

Four external systems — Finacle, Vision, ICAD, and Excel/Maxim files — currently require manual, per-system checking to know when an account should move to memo, what its balance is, or what ICAD needs. There's no automated bridge translating what these systems report into events the rest of MBP can react to.

## Solution

The `integration` context provides four adapters, each an anti-corruption layer against one external system, normalizing what it observes into domain events published on Kafka (ADR-0001) for the rest of MBP to consume, and exposing whatever synchronous capability its external system doesn't support asynchronously (e.g., ICAD name-clearance calls).

## User Stories

1. As the Write-Off Detection Service, I want to scan Finacle transaction narrations for the phrase "written off" (case-insensitive, with configurable variants like "write-off"/"write off"), so memo candidates are found without a human reading narrations.
2. As the Write-Off Detection Service, I want to publish a `MemoDetected` event carrying Account Number, Customer ID, Branch/SOL, Currency, Transaction/Posting Reference, full Narration text, current balance, and transfer date/time, so memo-balance has everything RFP §3.13(bis) requires without a follow-up call to Finacle.
3. As the Write-Off Detection Service, I want detection to run continuously against new Finacle movements, not as a one-off batch, so newly written-off accounts are found close to real time.
4. As the Vision ETL Connector, I want to pull Vision's balance-calculation results on a scheduled batch/real-time extract, so memo-balance can cross-check its own balance against Vision's authoritative figure (RFP §3.1).
5. As the Vision ETL Connector, I want to publish a `VisionBalanceSynced` event per account per sync, so memo-balance's discrepancy detection has a defined trigger rather than polling Vision itself.
6. As the ICAD Integration Adapter, I want to expose a capability to request customer-name clearance from the ICAD portal, so `clearance-orchestration` doesn't need to know ICAD's own API/file shape.
7. As the ICAD Integration Adapter, I want to publish an event when ICAD confirms a clearance (or reports a failure/discrepancy), so `clearance-orchestration` can track the 24–48 hour SLA without polling ICAD itself.
8. As the Excel Import Service, I want to validate an uploaded Excel file from the Maxim team against the same required fields as `MemoDetected` before publishing anything, so malformed manual uploads don't reach memo-balance as if they were automated detections.
9. As the Excel Import Service, I want to publish the same `MemoDetected` event shape as the Write-Off Detection Service (tagged with its own source), so memo-balance treats every detection path uniformly.
10. As a Maxim Team user, I want to see the outcome of my Excel upload (rows accepted, rows rejected with reasons), so I know whether the import reached the rest of the system.
11. As the `integration` context, I want every adapter to retry a transient failure against its external system before giving up, so temporary Finacle/Vision/ICAD unavailability doesn't silently drop a detection.
12. As the `integration` context, I want a failed detection/sync (after retries exhausted) to raise an operational alert via `shared-platform`'s notification-service, so nobody has to notice a missing memo account by accident.
13. As the `integration` context, I want every adapter to write an `AuditEvent` (ADR-0005) for every detection, sync, and ICAD call, so there's a complete trail of what each external system reported and when.
14. As the ICAD Integration Adapter, I want to expose the raw ICAD response/discrepancy detail alongside its own event, so `clearance-orchestration` can escalate with the actual ICAD-reported reason (RFP §4.6).

## Implementation Decisions

- Four Gradle modules under `services/integration/`: `write-off-detection-service`, `vision-etl-connector`, `icad-integration-adapter`, `excel-import-service` (ADR-0007) — matches the diagram's own layer 4.
- All four publish/consume via Kafka (ADR-0001); REST is used only where ICAD's own protocol demands a synchronous call, and `icad-integration-adapter` fronts that as a thin synchronous wrapper, still publishing an event afterward.
- Excel Import Service validates uploads server-side before publishing — a rejected row never reaches Kafka.
- No context-specific persistence beyond each adapter's own retry/dedup state; the Memo record itself belongs to `memo-balance`, not duplicated here.
- Every adapter depends on `libs/audit-trail-lib`.

## Testing Decisions

- Seam: each adapter's own public boundary — its Kafka output topic (given a known external-system fixture, assert the exact event published) and, for Excel Import Service, its file-upload REST endpoint.
- Testcontainers for Kafka (ADR-0009); external systems (Finacle/Vision/ICAD) are faked at the adapter's own client interface, not mocked at the Kafka boundary — the adapter's translation logic is what's under test.

## Out of Scope

- What memo-balance does with `MemoDetected` — memo-balance's own spec.
- The ICAD clearance SLA/escalation workflow itself — `clearance-orchestration`'s spec.
- Country/GL mapping used to interpret Finacle postings — `reference-data-config`'s spec; integration only consumes it.

## Further Notes

- The `MemoDetected` and ICAD-outcome event schemas here are exactly what `memo-balance`'s and `clearance-orchestration`'s specs assume they receive — these specs must agree on field names before tickets are cut, which is exactly what the Integration Contract gate (`enterprise-review` axis 5) checks later.
