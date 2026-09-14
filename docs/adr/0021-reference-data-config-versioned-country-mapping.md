# ADR-0021: reference-data-config — versioned Country mapping, an unauthenticated internal read API, its own Postgres schema

## Status

Accepted

## Context

`reference-data-config` (RFP §3.14, spec issue #4) is the last piece every other implemented context has been quietly blocked on: `memo-balance`'s `CountryConfigLookup` interface (`services/memo-balance/src/main/java/com/uba/mbp/memobalance/referencedata/`) was constructor-injected into `MemoIngestionService` and backed by `StaticCountryConfigLookup`, a fixed in-memory map with exactly one entry (`NG`), whose own Javadoc said "swap the implementation, not this interface or its callers, once that context is real." `authentication-service`'s missing Country/Region JWT claim (ADR-0017) and `integration`'s deliberately-null `country` field on `MemoDetectedEvent` are the same underlying block, already documented as real, honest gaps.

RFP §3.14 is larger than that one ready seam — it also asks for date format, holiday calendar, an admin UI, and "interface with core banking for all countries to monitor inflows." A repo-wide search turned up zero anticipating code (no TODO, stub, or comment) for any of those four. Building them now would mean guessing at requirements nothing currently depends on.

Researching this also surfaced an unrelated, real, pre-existing defect: `memo-balance` and `authentication-service` both run Flyway migrations against the same local Postgres database (`mbp`) with no schema isolation configured (no `spring.flyway.schemas`, no Hibernate `default_schema`, in either service's `application.yml`). Both default to the `public` schema and therefore the same `flyway_schema_history` table. Both have a `V1` migration with different content — against a real shared local Postgres (not each service's own isolated Testcontainers instance, which is all that's ever exercised in the test suite), the second service to start would hit a Flyway checksum-mismatch validation error on version 1 and fail to start.

## Decision

**Scope this ticket to Country + GL-mapping data only** — the part with a real, ready-made consumer (`memo-balance`) and a second real consumer this ticket adds (the admin-write API, spec User Stories 1, 9). Date format, holiday calendar, the admin UI itself, and the core-banking inflow interface are explicitly out of scope, documented here rather than silently dropped or guessed at.

**Country mapping is versioned/effective-dated from day one**, per spec User Story 6 ("a report run against a past period uses the mapping that was in effect then, not today's"). `CountryConfigService.update(...)` never mutates a row in place: it closes the current row (`effectiveTo = now`) and inserts a new one (`effectiveFrom = now`), inside one transaction, with `saveAndFlush` on the close so the database's partial unique index (`country_config (country_code) WHERE effective_to IS NULL`) — which enforces "at most one currently-effective row per country" at the database level, not just in application code — never briefly sees two open rows for the same country in one transaction. Retrofitting versioning later, once `reporting` (RFP §3.15's "SOL → Region → Directorate... versioned and auditable" mapping tables) actually needs it, would be a real migration; designing it in now is cheap.

**The read API (`GET /countries/{code}`, `GET /countries`) is deliberately not RBAC-gated** — the same trust-model precedent `notification-service`'s `POST /notifications` already established (ADR-0019): called service-to-service by other backend contexts (`memo-balance` today), not by a human or the `channels` SPA, trusted network behind ADR-0004's single external ingress. **The admin-write API (`POST`/`PUT /countries`) requires `hasRole("ADMIN")`** (ADR-0018) via the same `SecurityConfig`/`JwtRoleConverter` resource-server pattern already used identically in `memo-balance`, `excel-import-service`, and `icad-integration-adapter` — the fifth copy of that pattern, no shared library, per ADR-0001.

**`reference-data-config` gets its own dedicated Postgres schema** (`reference_data_config`, via `spring.flyway.schemas` + Hibernate `hibernate.default_schema`) so it doesn't inherit or worsen the `memo-balance`/`authentication-service` Flyway collision described above. That collision is **not** retrofitted here — fixing it would mean touching two already-merged, already-reviewed services outside this ticket's own bounded context — but it is now documented explicitly rather than silently repeated a third time.

**`memo-balance`'s seam is closed in this same ticket**, matching the established "close every real downstream consumer in the ticket that builds the real thing" precedent (Ticket 01 auth, Ticket 03 notification): `HttpCountryConfigLookup` replaces `StaticCountryConfigLookup` (deleted). A null, unrecognized, or unreachable-service response all fall back to the same NG default the old stand-in always returned — ingestion (RFP §3.13(bis)) must never fail just because Country resolution did.

## Consequences

- `CONTEXT-MAP.md`'s `reference-data-config` row moves to 🔶 (partially implemented) — Country/GL mapping is real; date format, holiday calendar, the admin UI, and the core-banking interface remain open, tracked in `services/reference-data-config/CONTEXT.md`'s own interim-seams section.
- `memo-balance` and `authentication-service`'s shared-schema Flyway collision is a known, real, currently-invisible-in-tests defect (every service's test suite uses its own isolated Testcontainers Postgres, which never exercises this) — worth a dedicated follow-up fix (give each existing service its own schema too) whenever those two are next touched, not bundled into this ticket.
- If `authentication-service` later gains a Country field on its `User` domain (closing ADR-0017's JWT-claim gap for real), it would read from this service's `GET /countries` the same way `memo-balance` does now — the seam this ticket built is reusable, not `memo-balance`-specific.
- If `reporting` later needs an as-of-a-past-instant lookup, the schema already supports it (`effectiveFrom`/`effectiveTo` windows) — only a new query/endpoint is needed, not a data-model migration.

## Source

RFP §3.14, §3.15 (SOL→Region→Directorate/GL-mapping versioning language); Spec: reference-data-config (issue #4) User Stories 1, 3, 5, 6, 7, 9; `services/memo-balance/CONTEXT.md`'s now-closed `CountryConfigLookup` interim seam; ADR-0017 (authentication-service's Country/Region claim gap, still open); ADR-0019 (the unauthenticated-internal-endpoint precedent this reuses); ADR-0009 (Testcontainers testing standard, which is why the Flyway schema collision above was invisible until now).
