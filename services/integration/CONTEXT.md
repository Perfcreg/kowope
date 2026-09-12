# integration

The anti-corruption layer against Finacle, Vision, ICAD, and Excel (RFP §3.2, §3.13(bis), §4.1) — four adapters translating what each external system reports into domain events on Kafka (ADR-0001). Three adapters built so far (Write-Off Detection Service, Vision ETL Connector, Excel Import Service); ICAD Integration Adapter follows the same shape but has no real substitute available (see Architecture below).

## Language

**Write-off detection**:
Continuously scanning core-banking transaction narrations for a phrase match ("written off", case-insensitive, with variants — RFP §3.13(bis)) to find accounts that should move to memo, without a human reading narrations.
_Avoid_: Write-off scan (the scan is the mechanism; detection is the outcome).

**Balance sync**:
Pulling an external system's authoritative balance figure for an account so `memo-balance` can reconcile against it (RFP §3.1). Distinct from detection — a sync doesn't find new memo candidates, it validates existing ones.

**Excel import**:
A manual detection path for the Maxim team: an uploaded workbook is validated against the same required fields as an automated detection, row by row, before anything reaches Kafka (RFP §3.7, §4.1, integration spec User Stories 8-10). A rejected row never publishes; the uploader sees which rows failed and why in the same HTTP response — and, per an enterprise-review fix, that response is only computed *after* the accepted rows' Kafka publish attempts complete, not before: a row that validates but whose publish permanently fails is reported to the user as rejected (reason: failed to publish, contact support), never as a false "accepted".

**Excel import's `Balance` and `Transfer Date` columns**: `Balance` must be the account's real current balance (matching the meaning `WriteOffScanner` uses for the same field, not any other spreadsheet figure the Maxim team might otherwise put there — there is no code-level cross-check, since the workbook is the only source of truth for a manual upload). `Transfer Date` is parsed as an ISO date and always rendered as UTC midnight (`atStartOfDay(ZoneOffset.UTC)`) — a real limitation, since RFP §3.13(bis)/User Story 2 ask for date/**time**; a spreadsheet has no reliable time-of-day component to carry, so this is an accepted precision loss for this detection path specifically (the automated Write-Off Detection Service does carry a real date from Fineract, without this limitation).

**Excel import's REST contract**: `POST /excel-imports` (multipart `file` field, `MAXIM_TEAM` role required) returns `ImportOutcome` — `fileName`, `totalRows`, `accepted`, `rejected`, `rows: RowOutcome[]` (`rowNumber`, `accountNumber`, `accepted`, `reason`) — as its 200 body. A malformed upload (bad headers, unparseable rows, wrong file format) returns 400 with a plain-text message; an unexpected failure returns a generic 500 that never echoes internal exception detail to the caller.

**Finacle substitute** / **Vision substitute**:
Apache Fineract, standing in for both real Finacle and real Vision in local/dev (ADR-0012, ADR-0013) — an already-running instance from an unrelated project (`mfb-stack`), not infrastructure this repo owns or provisions. The two adapters point at the same physical instance independently; that's a limitation of not having two real systems, not a design choice to treat Finacle and Vision as one thing.

## Published events

**`mbp.integration.memo-detected`** (`MemoDetectedEvent`), from both the Write-Off Detection Service and the Excel Import Service (tagged `source="write-off-detection-service"` or `source="excel-import-service"` respectively) — the producer half of the contract `memo-balance` already consumes (see [services/memo-balance/CONTEXT.md](../memo-balance/CONTEXT.md#consumed-events) for the authoritative field list). Every detection path publishes the identical event shape (integration spec User Story 9) so `memo-balance` treats them uniformly.

**`mbp.integration.vision-balance-synced`** (`VisionBalanceSyncedEvent`), from the Vision ETL Connector — same pattern: `memo-balance`'s copy is authoritative, this context's copy must stay in sync (`accountNumber`, `balance`, `syncedAt`).

Each adapter owns its own copy of the event record it publishes — no shared library for events, per ADR-0001's event-driven (not shared-code) coupling.

## Interim seams (swap the implementation, not the interface, once the real context exists)

- **Notifications**: `NotificationClient` (one per adapter) — a logging stand-in until `shared-platform`'s notification-service exists (integration spec User Story 12).
- **Auth**: each adapter carries its own copy of `SecurityConfig`/`JwtRoleConverter` (a symmetric dev-secret JWT decoder) — same interim seam as `memo-balance`'s, until `shared-platform`'s authentication-service exists.

## Architecture

**ADR-0011**: Apache Camel is this context's integration framework — Polling Consumer (timer), Splitter, Message Translator, and a Dead Letter Channel error handler (satisfying the spec's retry requirement, User Story 11) are used instead of hand-rolled scheduling/retry code. Business logic (matching/scanning rules, event construction) lives in plain Java beans, independently testable without Camel; routes only wire the EIPs together. The Excel Import Service varies this shape slightly: it's REST-triggered (a `ProducerTemplate` call from the upload controller into `direct:importExcelFile`), not timer-polled, since there's no external system to poll — the trigger is the Maxim team's own upload action.

**ADR-0012 / ADR-0013**: Fineract's REST API is real, verified infrastructure (a running instance, not a mock) used for manual/local verification of both the Finacle and Vision substitutions, but automated tests stub its HTTP surface with WireMock rather than depending on `mfb-stack` being up — this context's test suite doesn't couple to another project's container lifecycle.

**Excel Import Service's test environment**: unlike Finacle/Vision, there's no live external "Excel system" to reuse — the external dependency here is the OOXML file format itself. Its tests use real Apache POI on both ends: fixture workbooks are built with `XSSFWorkbook` and written to real `.xlsx` bytes in-test (`ExcelFixtures`), then read back through the same `ExcelWorkbookParser` production code — a genuine binary OOXML roundtrip, not a mocked reader, without an opaque checked-in binary asset.

**Excel Import Service's publish path** (enterprise-review fix): unlike the timer-polled adapters' Camel `Splitter`, this adapter publishes each accepted event through `direct:publishMemoDetected` via a plain Java loop (`MemoDetectedPublisher`), not a Splitter+AggregationStrategy — correlating "did *this specific row's* publish succeed" back into a synchronous HTTP response is what a human is waiting on (User Story 10), and that correlation is far more fragile to express as a Splitter aggregation than as a loop over `ProducerTemplate` sends. The per-event route itself is still fully Camel-EIP-driven (Message Translator, Kafka Producer, Dead Letter Channel) — ADR-0011 is not bypassed, only the outer "for each accepted row" iteration is imperative instead of declarative. A permanently-failed publish now lands on `mbp.integration.memo-detected.dlq` and is audited (`MEMO_DETECTED_PUBLISH_FAILED`), not just alerted-and-logged.

**ICAD Integration Adapter** (not yet built): no real ICAD sandbox is available to this project the way Fineract stands in for Finacle/Vision — its tests will be WireMock-only for both manual and automated verification, a real limitation flagged to the architect rather than silently treated as equivalent to the other three adapters' real-infrastructure verification.
