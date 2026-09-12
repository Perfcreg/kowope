# integration

The anti-corruption layer against Finacle, Vision, ICAD, and Excel (RFP §3.2, §3.13(bis), §4.1) — four adapters translating what each external system reports into domain events on Kafka (ADR-0001). All four are now built (Write-Off Detection Service, Vision ETL Connector, Excel Import Service, ICAD Integration Adapter); the last has a materially weaker verification story than the other three — see Architecture below.

## Language

**Write-off detection**:
Continuously scanning core-banking transaction narrations for a phrase match ("written off", case-insensitive, with variants — RFP §3.13(bis)) to find accounts that should move to memo, without a human reading narrations.
_Avoid_: Write-off scan (the scan is the mechanism; detection is the outcome).

**Balance sync**:
Pulling an external system's authoritative balance figure for an account so `memo-balance` can reconcile against it (RFP §3.1). Distinct from detection — a sync doesn't find new memo candidates, it validates existing ones.

**Excel import**:
A manual detection path for the Maxim team: an uploaded workbook is validated against the same required fields as an automated detection, row by row, before anything reaches Kafka (RFP §3.7, §4.1, integration spec User Stories 8-10). A rejected row never publishes; the uploader sees which rows failed and why in the same HTTP response.

**ICAD clearance**:
Requesting that NIBSS's Industry Customer Accounts Database (ICAD) update its record of a customer so their name is cleared following memo liquidation (RFP §3.8). Two real ICAD operations underlie this: **pushAccount** (submit the updated record) and **fetchAccount** (look it up/confirm status) — see ADR-0014. Distinct from detection/sync: this adapter *writes* to an external system, not just reads from one, and the result isn't known synchronously (real clearance takes 24-48 hours).

**Finacle substitute** / **Vision substitute**:
Apache Fineract, standing in for both real Finacle and real Vision in local/dev (ADR-0012, ADR-0013) — an already-running instance from an unrelated project (`mfb-stack`), not infrastructure this repo owns or provisions. The two adapters point at the same physical instance independently; that's a limitation of not having two real systems, not a design choice to treat Finacle and Vision as one thing.

## Published events

**`mbp.integration.memo-detected`** (`MemoDetectedEvent`), from both the Write-Off Detection Service and the Excel Import Service (tagged `source="write-off-detection-service"` or `source="excel-import-service"` respectively) — the producer half of the contract `memo-balance` already consumes (see [services/memo-balance/CONTEXT.md](../memo-balance/CONTEXT.md#consumed-events) for the authoritative field list). Every detection path publishes the identical event shape (integration spec User Story 9) so `memo-balance` treats them uniformly.

**`mbp.integration.vision-balance-synced`** (`VisionBalanceSyncedEvent`), from the Vision ETL Connector — same pattern: `memo-balance`'s copy is authoritative, this context's copy must stay in sync (`accountNumber`, `balance`, `syncedAt`).

**`mbp.integration.icad-clearance-outcome`** (`IcadClearanceOutcomeEvent`), from the ICAD Integration Adapter — `accountNumber`, `customerId`, `icadReference`, `status` (CLEARED/DISCREPANCY/FAILED), `detail` (ICAD's raw response/discrepancy text, User Story 14), `resolvedAt`, `source`. Provisional per ADR-0014 — `clearance-orchestration` (not yet built) should treat this shape as unverified until real ICAD access is confirmed.

Each adapter owns its own copy of the event record it publishes — no shared library for events, per ADR-0001's event-driven (not shared-code) coupling.

## Interim seams (swap the implementation, not the interface, once the real context exists)

- **Notifications**: `NotificationClient` (one per adapter) — a logging stand-in until `shared-platform`'s notification-service exists (integration spec User Story 12).
- **Auth**: each adapter carries its own copy of `SecurityConfig`/`JwtRoleConverter` (a symmetric dev-secret JWT decoder) — same interim seam as `memo-balance`'s, until `shared-platform`'s authentication-service exists.

## Architecture

**ADR-0011**: Apache Camel is this context's integration framework — Polling Consumer (timer), Splitter, Message Translator, and a Dead Letter Channel error handler (satisfying the spec's retry requirement, User Story 11) are used instead of hand-rolled scheduling/retry code. Business logic (matching/scanning rules, event construction) lives in plain Java beans, independently testable without Camel; routes only wire the EIPs together. The Excel Import Service and ICAD Integration Adapter vary this shape: Excel is REST-triggered (a Maxim team upload) rather than timer-polled; ICAD is both — a synchronous REST push plus a separate timer-polled fetch, since real clearance resolves asynchronously.

**ADR-0012 / ADR-0013**: Fineract's REST API is real, verified infrastructure (a running instance, not a mock) used for manual/local verification of both the Finacle and Vision substitutions, but automated tests stub its HTTP surface with WireMock rather than depending on `mfb-stack` being up — this context's test suite doesn't couple to another project's container lifecycle.

**Excel Import Service's test environment**: unlike Finacle/Vision, there's no live external "Excel system" to reuse — the external dependency here is the OOXML file format itself. Its tests use real Apache POI on both ends: fixture workbooks are built with `XSSFWorkbook` and written to real `.xlsx` bytes in-test (`ExcelFixtures`), then read back through the same `ExcelWorkbookParser` production code — a genuine binary OOXML roundtrip, not a mocked reader, without an opaque checked-in binary asset.

**ADR-0014 (ICAD)**: no real NIBSS ICAD sandbox is reachable from this project — `IcadClient`'s pushAccount/fetchAccount contract is modeled from real ICAD's described operational shape, not verified against a live system the way Fineract is. Every test (and every manual/local run) targets a WireMock stub of that modeled contract. This is a real, flagged gap: if real ICAD access becomes available, `IcadClient` and the modeled JSON contract are expected to change; the clearance domain logic and `IcadClearanceOutcomeEvent` shape are written to be swap-safe, but are unverified against reality until then.
