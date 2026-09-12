# integration

The anti-corruption layer against Finacle, Vision, ICAD, and Excel (RFP §3.2, §3.13(bis), §4.1) — four adapters translating what each external system reports into domain events on Kafka (ADR-0001). Ticket 01 (Write-Off Detection Service) is the first built; the other three (Vision ETL Connector, Excel Import Service, ICAD Integration Adapter) follow the same shape.

## Language

**Write-off detection**:
Continuously scanning core-banking transaction narrations for a phrase match ("written off", case-insensitive, with variants — RFP §3.13(bis)) to find accounts that should move to memo, without a human reading narrations.
_Avoid_: Write-off scan (the scan is the mechanism; detection is the outcome).

**Finacle substitute**:
Apache Fineract, standing in for real Finacle in local/dev (ADR-0012) — an already-running instance from an unrelated project (`mfb-stack`), not infrastructure this repo owns or provisions.

## Published events

**`mbp.integration.memo-detected`** (`MemoDetectedEvent`), from the Write-Off Detection Service — the producer half of the contract `memo-balance` already consumes (see [services/memo-balance/CONTEXT.md](../memo-balance/CONTEXT.md#consumed-events) for the authoritative field list). Field names and types must stay in sync between the two; this context's copy is in `write-off-detection-service/.../event/MemoDetectedEvent.java`.

## Interim seams (swap the implementation, not the interface, once the real context exists)

- **Notifications**: `NotificationClient` — `LoggingNotificationClient` logs at ERROR instead of paging anyone, until `shared-platform`'s notification-service exists (integration spec User Story 12).

## Architecture

**ADR-0011**: Apache Camel is this context's integration framework — Polling Consumer (timer), Splitter, Message Translator, and a Dead Letter Channel error handler (satisfying the spec's retry requirement, User Story 11) are used instead of hand-rolled scheduling/retry code. Business logic (matching rules, event construction) lives in plain Java beans (e.g. `WriteOffScanner`), independently testable without Camel; routes (e.g. `WriteOffDetectionRoute`) only wire the EIPs together.

**ADR-0012**: Fineract's REST API is real, verified infrastructure (a running instance, not a mock) used for manual/local verification, but automated tests stub its HTTP surface with WireMock rather than depending on `mfb-stack` being up — this context's test suite doesn't couple to another project's container lifecycle.
