> **DRAFT — not yet published.** See `docs/specs/memo-balance.md` for why (gh CLI not installed). Publish as a GitHub issue and delete this file once it's available.

# Spec: reference-data-config

## Problem Statement

RFP §3.14 requires Region/Country scoping, GL mappings, holiday calendars, and per-country configuration, but nothing in the proposed diagram owns this data (see the architect's delta in `CONTEXT-MAP.md`) — without a home, it either gets duplicated per context or silently dropped.

## Solution

A dedicated `reference-data-config` service holds Country/Region definitions, GL mappings (write-off, recovery), date formats, and holiday calendars, exposed via REST and an admin UI, consumed read-only by every other context that needs it.

## User Stories

1. As an Administrator, I want to add or edit a Country and its mappings through an admin UI without a code change, so RFP §3.14's "without code" requirement is met.
2. As an Administrator, I want Region to default to Africa & Nigeria with Country including Nigeria and other subsidiaries, so the default matches RFP §3.14 out of the box.
3. As reference-data-config, I want each Country to carry its own base currency, GL mappings (write-off, recovery), date format, and holiday calendar, so every other context can look up country-specific rules from one place.
4. As authentication-service, I want to scope a user to a Country by default, with cross-country roles configurable, so RFP §3.14's scoping rule is enforced consistently (reference-data-config is the source of truth for which Countries exist; `shared-platform` enforces the scoping).
5. As reporting, I want to look up the SOL → Region → Directorate mapping table for a given period, so §3.15's aggregation and sub-totals use the correct, versioned mapping in effect for that period.
6. As reference-data-config, I want every mapping table to be versioned, so a report run against a past period uses the mapping that was in effect then, not today's.
7. As memo-balance, I want to look up an account's Country to apply the correct GL write-off/recovery code, so balance postings use the right GL mapping without memo-balance owning that data itself.
8. As reference-data-config, I want to interface with core banking for all Countries to monitor inflows, so cross-country inflow monitoring (RFP §3.14) doesn't require a separate integration per country.
9. As an Administrator, I want a change to a mapping table to be audited (who changed what, when), so configuration drift is traceable.

## Implementation Decisions

- Gradle module `services/reference-data-config` (ADR-0007), PostgreSQL for the config tables (ADR-0003) — system of record for versioned mappings.
- Read-only REST API for other contexts; the admin write API is separately RBAC-gated (Admin role only).
- Every write goes through `audit-trail-lib`.

## Testing Decisions

- Seam: the REST API, both the admin-write and cross-context read paths. Testcontainers-backed Postgres for anything touching versioned mapping lookups (ADR-0009).

## Out of Scope

- How each consuming context applies the mapping (e.g., how memo-balance uses a GL code) — each consuming context's own spec.

## Further Notes

- Every context whose spec mentions Country/Region/GL mapping should treat reference-data-config as the read path, not maintain its own copy.
