# integration

The anti-corruption layer against Finacle, Vision, ICAD, and Excel (RFP §3.2, §3.13(bis), §4.1) — four adapters translating what each external system reports into domain events on Kafka (ADR-0001). Ticket 01 (Write-Off Detection Service) is the first built; the other three (Vision ETL Connector, Excel Import Service, ICAD Integration Adapter) follow the same shape.

## Language

**Write-off detection**:
Continuously scanning core-banking transaction narrations for a phrase match ("written off", case-insensitive, with variants — RFP §3.13(bis)) to find accounts that should move to memo, without a human reading narrations. In business/domain conversation, call this "detection," not "the scan" — the scan is the internal mechanism (and `WriteOffScanner`/`scan()` remain fine as implementation-level names for that mechanism), detection is the outcome the rest of the system cares about.

**Finacle substitute**:
Apache Fineract, standing in for real Finacle in local/dev (ADR-0012) — an already-running instance from an unrelated project (`mfb-stack`), not infrastructure this repo owns or provisions.

## Published events

**`mbp.integration.memo-detected`** (`MemoDetectedEvent`), from the Write-Off Detection Service — the producer half of the contract `memo-balance` already consumes (see [services/memo-balance/CONTEXT.md](../memo-balance/CONTEXT.md#consumed-events) for the authoritative field list). Field names and types must stay in sync between the two; this context's copy is in `write-off-detection-service/.../event/MemoDetectedEvent.java`. Value-mapping notes (enterprise-review fixes, 2026-09-12): `balance` is the account's real current balance (Fineract's `summary.accountBalance`), not the triggering transaction's own amount; `postingReference` is Fineract's raw transaction id, not a string carrying "FINERACT" branding into the published contract; `country` is deliberately `null` (not a hardcoded guess) since `reference-data-config` — not this adapter — owns Country resolution (RFP §3.14). **Known remaining gap, not yet fixed**: `branchSol` is Fineract's `officeName` (a display name like "Abuja Branch"), not a Finacle-style SOL/branch code — Fineract has no closer equivalent field, and this is the best available substitute, but it should not be read as a real SOL code.

**Dedup state**: `DetectedWriteOffStore` (in-memory, ADR-0013) prevents the timer's from-scratch rescan from re-publishing and re-auditing the same write-off forever — required for User Story 3's "continuous, not one-off batch" detection to actually mean *new* movements.

## Interim seams (swap the implementation, not the interface, once the real context exists)

- **Notifications**: `NotificationClient` — `LoggingNotificationClient` logs at ERROR instead of paging anyone, until `shared-platform`'s notification-service exists (integration spec User Story 12).

## Architecture

**ADR-0011**: Apache Camel is this context's integration framework — Polling Consumer (timer), Splitter, Message Translator, and a Dead Letter Channel error handler (satisfying the spec's retry requirement, User Story 11) are used instead of hand-rolled scheduling/retry code. Business logic (matching rules, event construction) lives in plain Java beans (e.g. `WriteOffScanner`), independently testable without Camel; routes (e.g. `WriteOffDetectionRoute`) only wire the EIPs together.

**ADR-0012**: Fineract's REST API is real, verified infrastructure (a running instance, not a mock) used for manual/local verification, but automated tests stub its HTTP surface with WireMock rather than depending on `mfb-stack` being up — this context's test suite doesn't couple to another project's container lifecycle. `FineractClient` is an interface (`FineractHttpClient` its implementation) so a real-Finacle implementation can actually substitute in later, per the ADR's own swap promise. ADR-0012 also now flags a real gap: this adapter's Kafka publish carries full customer PII with no encryption/SASL configured — a system-wide gap (this repo has no secured Kafka broker anywhere yet), not fixed per-adapter here.

**ADR-0013**: `DetectedWriteOffStore`'s in-memory dedup state is a known, accepted-for-now limitation — a restart can cause one round of duplicate re-detection, which `memo-balance`'s own consumer-side dedup (Ticket 02) absorbs safely rather than corrupting state.
