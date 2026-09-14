# reference-data-config

Country/GL-mapping reference data (RFP §3.14): every other context that needs a Country's base currency or GL write-off/recovery codes reads it from here instead of guessing or duplicating it. `memo-balance` is the first real consumer, closing its `CountryConfigLookup` interim seam.

## Language

**Country mapping is versioned, not a flat current value** (spec User Story 6): editing a Country's mapping never overwrites a row in place — it closes the currently-effective row (`effectiveTo = now`) and opens a new one (`effectiveFrom = now`). A lookup for "now" is simple (the one row per country with `effectiveTo IS NULL`); a lookup for a past instant (once `reporting` needs it, per RFP §3.15's "reports must use the effective mapping for the period") is the row whose `[effectiveFrom, effectiveTo)` window contains that instant — not built yet since nothing calls it, but the schema doesn't need to change when something does.

## Published REST contract

**Read path** (spec User Stories 3, 7 — Country/GL mapping only, not the SOL → Region → Directorate lookup User Story 5 asks for; see Interim seams below) — deliberately **not RBAC-gated**, same trust model as `notification-service`'s `POST /notifications` (ADR-0019): called service-to-service by other backend contexts, not a human or the `channels` SPA.

- `GET /countries/{code}` → `200` `{countryCode, region, baseCurrency, glWriteOffCode, glRecoveryCode, effectiveFrom}` (the currently-effective row only). `404` if no current mapping exists for that code.
- `GET /countries` → `200` array of the same shape, one entry per country with a currently-effective row.

**Admin-write path** (spec User Story 1) — requires `Authorization: Bearer <token>` with `roles` containing `ADMIN` (ADR-0018), verified via authentication-service's JWKS endpoint (ADR-0017). `401` with no token, `403` with a valid non-admin token.

- `POST /countries` (`countryCode`, `region`, `baseCurrency`, `glWriteOffCode`, `glRecoveryCode`) → `201 Created`. `409` if a currently-effective mapping already exists for that code (use `PUT` to change it, not a second `POST`).
- `PUT /countries/{code}` (`region`, `baseCurrency`, `glWriteOffCode`, `glRecoveryCode`) → `200` with the new currently-effective row. `404` if the country doesn't exist yet.
- A blank required field on either endpoint → `400`.

Every create/update is audited (`COUNTRY_CONFIG_CREATED`, `COUNTRY_CONFIG_UPDATED`) with the *acting* admin as actor — taken from their own verified token's `sub` claim, never a request field (spec User Story 9).

## Interim seams / deferred gaps

- **Only Country + GL mapping are real** — RFP §3.14 also asks for date format, holiday calendar, an admin UI, and "interface with core banking for all countries to monitor inflows." None of these have any anticipating code anywhere in this repo (confirmed by search before this ticket started) — genuinely out of scope here, not fabricated, left for a later ticket.
- **Spec User Story 5 (reporting's "SOL → Region → Directorate mapping table for a given period," RFP §3.15) is not built** — corrected after an enterprise-review finding: an earlier version of this doc and ADR-0021 listed US5 as covered, which was wrong. Only Country-level mapping exists; no SOL or Directorate concept exists anywhere in this schema. The close-and-insert versioning pattern here is reusable for a future SOL/Directorate table, but that table itself needs to be built from scratch once `reporting` is real — same "zero anticipating code, zero real consumer" reasoning as the bullet above, not special-cased differently.
- **`authentication-service`'s Country/Region JWT claim is still not issued** (ADR-0017's gap) — this ticket makes that *possible* (a real Country model now exists to source a value from) but doesn't make it *real*: `authentication-service`'s own `User` domain has no Country field yet, and the admin panel has no way to set one. That's a `shared-platform` change, a separate future ticket.
- **No effective-dated (as-of-a-past-instant) lookup endpoint yet** — the schema supports it (see Language above), but nothing calls it since `reporting` doesn't exist yet. Add the endpoint when a real caller needs it, not speculatively.

## Architecture

**ADR-0021**: the versioned-mapping design (close-and-insert, never mutate-in-place); the unauthenticated internal read API (ADR-0019 precedent); this service's own dedicated Postgres schema (`reference_data_config`) rather than the default `public` schema `memo-balance` and `authentication-service` both use unqualified against the same local database — a genuine pre-existing collision this service doesn't repeat (documented, not retrofitted into those two already-merged services).

**Persistence**: Postgres, `country_config` table, a partial unique index (`WHERE effective_to IS NULL`) enforcing at most one currently-effective row per country at the database level, not just in application logic.

**RBAC**: the fifth copy of this repo's `SecurityConfig`/`JwtRoleConverter` resource-server pattern (`memo-balance`, `excel-import-service`, `icad-integration-adapter`, `authentication-service`'s own admin panel being the other four) — no shared library, per ADR-0001's event/HTTP-contract-only coupling between contexts.
