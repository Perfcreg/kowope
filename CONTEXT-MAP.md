# Context Map — Memo Balance Portal (MBP)

This repo is multi-context. Each row below is a bounded context; its `CONTEXT.md` is created lazily (on the first resolved term) at the path shown — most don't exist yet, `memo-balance`'s is the first. System-wide decisions that don't belong to any single context live in root [docs/adr/](docs/adr/).

Physical layout (per ADR-0007): backend contexts live under `services/`, one Gradle module per deployable service (a context with several diagram-named services, like `integration` or `shared-platform`, gets one subfolder per service); the audit-trail-lib contract lives at `libs/audit-trail-lib/`, not under any context; `channels` is a standalone Vite/React app, not a Gradle module.

Source material: [docs/rfp/2025-09-uba-memo-balance-rfp.md](docs/rfp/2025-09-uba-memo-balance-rfp.md), [docs/architecture/mbp-component-diagram.md](docs/architecture/mbp-component-diagram.md).

## Contexts

| Context | Path | Services (Gradle modules) | Destination | RFP source |
|---|---|---|---|---|
| `integration` 🔶 | `services/integration/CONTEXT.md` | `write-off-detection-service` ✅, `vision-etl-connector` ✅, `icad-integration-adapter`, `excel-import-service` | Anti-corruption layer against Finacle, Vision, ICAD, and Excel | §3.2, §3.13(bis), §4.1 |
| `shared-platform` | `services/shared-platform/CONTEXT.md` | `authentication-service`, `notification-service` | Cross-service identity and alerting | §3.7, §4.5 |
| `reference-data-config` | `services/reference-data-config/CONTEXT.md` | `reference-data-config` | Region/Country model, GL mappings, holiday calendar, admin config UI | §3.14 |
| `memo-balance` ✅ | `services/memo-balance/CONTEXT.md` | `memo-balance` | Memo Ingestion & Balance Engine — detection, balance capture, adjustment, balance/payment exception handling, memo document management | §3.1, §3.10, §3.11, §3.13(bis) |
| `account-verification` | `services/account-verification/CONTEXT.md` | `account-verification` | Account and Loan Verification Engine — cross-account verification, system-of-record for loan segment status | §3.2, §3.3 |
| `clearance-orchestration` | `services/clearance-orchestration/CONTEXT.md` | `clearance-orchestration` | ICAD clearance workflow only (narrowed from the diagram's combined orchestrator) | §3.8, §4.6 |
| `case-engagement` | `services/case-engagement/CONTEXT.md` | `case-engagement` | Case Engagement Service — action-plan tracking/resolution, RBAC-scoped case visibility | §3.4, §3.9 |
| `reporting` | `services/reporting/CONTEXT.md` | `reporting` | Full reporting subsystem — scheduled jobs, reconciliation, retention, lineage — plus notification/report distribution | §3.6, §3.12, §3.15 |
| `channels` | `channels/CONTEXT.md` | (Vite/React app, not a Gradle module) | Web presentation layer (Admin Dashboard, Branch/Back-office) — composes views from the 8 backend contexts, no domain data of its own. Mobile deferred. | §4.10 |

Cross-cutting, not a context: `libs/audit-trail-lib` — the shared audit-logging contract (`AuditEvent`/`AuditLogger`), depended on by every service module. See ADR-0005.

✅ = implemented, 🔶 = partially implemented. `memo-balance` (Tickets 01–08, PR #10) is fully implemented; `integration` has two adapters so far (write-off-detection-service and vision-etl-connector, both using Apache Camel — ADR-0011 — against a Fineract stand-in for Finacle and Vision respectively — ADR-0012/ADR-0013). Each implemented context's `CONTEXT.md` exists for real, including published/consumed event schemas other contexts should build against instead of reading its Java source.

## Relationships

Event flows between contexts, established as each gets built (per ADR-0001 — Kafka, not direct calls):

- **`integration` → `memo-balance`**: publishes `MemoDetected` (a write-off narration match) and `VisionBalanceSynced` (a balance reconciliation point). Both are currently defined by `memo-balance` itself, as the target shape — `integration` doesn't exist yet. See [services/memo-balance/CONTEXT.md](services/memo-balance/CONTEXT.md#consumed-events).
- **`memo-balance` → `account-verification`**: publishes `MemoLiquidated` when a balance reaches exactly zero; `account-verification` consumes it to drive the Owing → Paid-Off transition (ADR-0006).
- **`memo-balance` → `clearance-orchestration`**: the same `MemoLiquidated` event starts the ICAD clearance workflow.
- **`memo-balance` → `reporting`**: publishes `MemoBalanceAdjusted` on every balance change, for period-movement computation without recomputing from raw transactions.

See [services/memo-balance/CONTEXT.md](services/memo-balance/CONTEXT.md#published-events) for the full field-level schemas.

## Shared vocabulary

Terms genuinely used across every context. Transcribed directly from the RFP (not grilled — these are already unambiguous in the source); context-specific nuance still belongs in that context's own `CONTEXT.md`.

**Loan segments**: an account is either **Owing** or **Paid-Off**. An account moves from Owing to Paid-Off automatically upon full liquidation of its memo balance (RFP §3.3). Ownership: `account-verification` is system-of-record; `memo-balance` publishes the triggering event — see [ADR-0006](docs/adr/0006-loan-segment-status-ownership.md).

**RBAC roles** (RFP §3.7, §4.5):

| Role | Privilege |
|---|---|
| CSM | View-only access to memo balances and account details |
| Recovery Team | Full privileges — verification, liquidation tracking, reporting |
| Transaction Services | Edit/update privileges for account data and memo balances |
| Credit Admin | Full privileges — verification, liquidation tracking, reporting |
| Maxim Team | View and manage Excel data integration processes |

**Region / Country model** (RFP §3.14): Region defaults to Africa & Nigeria; Country includes Nigeria and other subsidiaries. Each Country carries its own base currency, GL mappings, date format, and holiday calendar. Users are Country-scoped by default; cross-country roles are configurable.

**Memo account**: an account flagged because a Finacle transaction narration matched the phrase "written off" (case-insensitive, configurable variants), tracked in the Memo database with its balance, transfer date, and metadata (RFP §3.13(bis)).

## Architect's deltas from the proposed diagram

The diagram groups things slightly differently than the contexts above. See the full reasoning in [docs/architecture/mbp-component-diagram.md](docs/architecture/mbp-component-diagram.md#architects-deltas-from-this-diagram):

1. Clearance and Exception Orchestrator → split: ICAD clearance stays its own context; exception handling moves to `memo-balance`.
2. `audit-trail-lib` → not a context; it's a shared logging contract, documented at the system level, not per-context.
3. RFP §3.14 has no diagram box → given its own context, `reference-data-config`.
4. `reporting` scoped to the full §3.15 subsystem, not just the diagram's thin distribution layer.

## Locked architecture decisions (Architect Kickoff, 2026-09-11)

| ADR | Decision |
|---|---|
| [0001](docs/adr/0001-event-driven-integration-via-kafka.md) | Event-driven integration via Kafka |
| [0002](docs/adr/0002-containerized-microservices-on-kubernetes.md) | Containerized microservices on Kubernetes |
| [0003](docs/adr/0003-polyglot-persistence.md) | Polyglot persistence: Postgres / Mongo / Redis / Blob |
| [0004](docs/adr/0004-api-gateway-waf-perimeter.md) | API Gateway + WAF as the single ingress |
| [0005](docs/adr/0005-centralized-observability.md) | Centralized observability: ELK + Prometheus + Grafana |
| [0006](docs/adr/0006-loan-segment-status-ownership.md) | `account-verification` owns loan segment status; `memo-balance` publishes the triggering event |
| [0007](docs/adr/0007-backend-stack-and-repo-layout.md) | Java 21 + Spring Boot 4.1.1, Gradle multi-module monorepo, one private GitHub repo |
| [0008](docs/adr/0008-local-first-development-environment.md) | `docker-compose` inner loop, Docker Desktop Kubernetes for integration testing; production target deferred to the org |
| [0009](docs/adr/0009-testing-stack.md) | JUnit 5 + Mockito + Testcontainers |
| [0010](docs/adr/0010-web-channel-stack.md) | Vite + React + TypeScript SPA for `channels`; mobile deferred |
| [0011](docs/adr/0011-apache-camel-for-integration-context.md) | Apache Camel is the integration framework for every `integration` adapter |
| [0012](docs/adr/0012-fineract-as-finacle-substitute.md) | Apache Fineract substitutes for Finacle in local/dev — the detection algorithm stays Finacle-faithful, only the REST client is Fineract-specific |
| [0013](docs/adr/0013-fineract-as-vision-substitute.md) | Apache Fineract also substitutes for Vision — same instance, independent adapter |
| [0016](docs/adr/0016-write-off-detection-dedup-state-is-in-memory-only.md) | Write-off detection dedup state is in-memory only — accepted, flagged restart risk |

**Still open**: production deployment target (ADR-0008), mobile channel approach (ADR-0010).
