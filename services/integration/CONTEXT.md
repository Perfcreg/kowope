# integration

The anti-corruption layer against Finacle, Vision, ICAD, and Excel (RFP §3.2, §3.13(bis), §4.1) — four adapters translating what each external system reports into domain events on Kafka (ADR-0001). Two adapters built so far (Write-Off Detection Service, Vision ETL Connector); Excel Import Service and ICAD Integration Adapter follow the same shape.

## Language

**Write-off detection**:
Continuously scanning core-banking transaction narrations for a phrase match ("written off", case-insensitive, with variants — RFP §3.13(bis)) to find accounts that should move to memo, without a human reading narrations.
_Avoid_: Write-off scan (the scan is the mechanism; detection is the outcome).

**Balance sync**:
Pulling an external system's authoritative balance figure for an account so `memo-balance` can reconcile against it (RFP §3.1). Distinct from detection — a sync doesn't find new memo candidates, it validates existing ones. Unlike write-off detection's one-time narration match, a sync deliberately republishes every active account's balance on every cycle — integration spec User Story 5 asks for "an event per account per sync... a defined trigger rather than polling Vision itself," meaning each cycle IS the trigger. `syncedAt` reflects when the cycle ran, not when the balance last changed — a consumer should read "VisionBalanceSynced" as "we polled," not "the balance changed."

**Finacle substitute** / **Vision substitute**:
Apache Fineract, standing in for both real Finacle and real Vision in local/dev (ADR-0012, ADR-0013) — an already-running instance from an unrelated project (`mfb-stack`), not infrastructure this repo owns or provisions. The two adapters point at the same physical instance independently; that's a limitation of not having two real systems, not a design choice to treat Finacle and Vision as one thing.

## Published events

**`mbp.integration.memo-detected`** (`MemoDetectedEvent`), from the Write-Off Detection Service — the producer half of the contract `memo-balance` already consumes (see [services/memo-balance/CONTEXT.md](../memo-balance/CONTEXT.md#consumed-events) for the authoritative field list).

**`mbp.integration.vision-balance-synced`** (`VisionBalanceSyncedEvent`), from the Vision ETL Connector — same pattern: `memo-balance`'s copy is authoritative, this context's copy must stay in sync (`accountNumber`, `balance`, `syncedAt`). A permanently-failed publish lands on `mbp.integration.vision-balance-synced.dlq` rather than being silently dropped. An account with no `accountNo` from Fineract is skipped and alerted on, never published with an empty key.

Each adapter owns its own copy of the event record it publishes — no shared library for events, per ADR-0001's event-driven (not shared-code) coupling.

## Interim seams (swap the implementation, not the interface, once the real context exists)

- **Notifications**: `NotificationClient` (one per adapter) — a logging stand-in until `shared-platform`'s notification-service exists (integration spec User Story 12).

## Architecture

**ADR-0011**: Apache Camel is this context's integration framework — Polling Consumer (timer), Splitter, Message Translator, and a Dead Letter Channel error handler (satisfying the spec's retry requirement, User Story 11) are used instead of hand-rolled scheduling/retry code. Business logic (matching/scanning rules, event construction) lives in plain Java beans, independently testable without Camel; routes only wire the EIPs together.

**ADR-0012 / ADR-0013**: Fineract's REST API is real, verified infrastructure (a running instance, not a mock) used for manual/local verification of both the Finacle and Vision substitutions, but automated tests stub its HTTP surface with WireMock rather than depending on `mfb-stack` being up — this context's test suite doesn't couple to another project's container lifecycle. Each adapter's `FineractClient` is an interface (`FineractHttpClient` its implementation), so ADR-0012/0013's "swap the implementation later" promise has an actual seam, not just a config value. ADR-0013 also flags a real, deliberately-unfixed gap: this adapter's Kafka publish carries account numbers and balances with no encryption/SASL configured — a system-wide issue, not scoped to one adapter.
