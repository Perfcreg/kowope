# ADR-0001: Event-driven integration via Kafka between adapters and core modules

## Status

Accepted

## Context

The Integration & ETL adapters (`integration` context) pull data from four external systems — Finacle, Vision, ICAD, and Excel — on different cadences (real-time narration scans, scheduled batch ETL, manual upload). The Core Modules (`memo-balance`, `account-verification`, `clearance-orchestration`, `case-engagement`) and Shared Services (`shared-platform`) all need to react to the same underlying facts (a memo detected, a balance changed, a clearance completed) without polling each other or being directly wired together.

This is the architecture Heirs Technologies proposed to UBA in the MBP component diagram, and it's a hard-to-reverse choice: every context's ingestion and consumption code is built against it.

## Decision

Adapters in the `integration` context publish domain events onto a Kafka event bus. Core Modules and Shared Services consume from Kafka rather than calling adapters (or each other) synchronously for these facts. REST is used only for synchronous, request/response interactions (e.g. Shared Services' auth checks), not for propagating state changes between contexts.

## Consequences

- Contexts stay decoupled: `memo-balance` doesn't need to know which adapter produced a detection event, only the event's shape.
- Event schemas become a first-class integration contract — every schema change is cross-context and needs the same rigor as an API change (see the Integration Contract review gate in [docs/agents/pipeline.md](../agents/pipeline.md)).
- Eventual consistency is the default between contexts; any place that needs strict read-after-write consistency across a context boundary needs an explicit design decision, not an assumption.

## Source

RFP §4.1 (real-time data synchronization, automated workflows); MBP component diagram, layer 6.
