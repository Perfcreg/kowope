> **DRAFT — not yet published.** See `docs/specs/memo-balance.md` for why (gh CLI not installed). Publish as a GitHub issue and delete this file once it's available.

# Spec: case-engagement

## Problem Statement

RFP §3.9 requires tracking pending action plans (liquidation updates, verification requests, ICAD escalations) to resolution, with overdue alerts. Today there's no single place tracking these across teams.

## Solution

`case-engagement` tracks action plans as cases, visible per RBAC role, with alerts for overdue items and a record of corrective actions taken.

## User Stories

1. As case-engagement, I want to create a case for a pending action plan (liquidation update, verification request, ICAD escalation), so RFP §3.9's tracking requirement is met regardless of which context raised the underlying need.
2. As case-engagement, I want to track a case's resolution status, so stakeholders know whether an action plan is still open.
3. As case-engagement, I want to alert stakeholders when a case is overdue against its defined timeline, so RFP §3.9's overdue-alert requirement is met via `shared-platform`'s notification-service.
4. As case-engagement, I want to record the corrective action taken to resolve a case, so RFP §3.9's "track corrective actions" requirement produces a real record, not just a closed flag.
5. As a CSM user, I want view-only access to cases relevant to accounts I support, so I can answer customer questions without editing rights.
6. As a Recovery Team or Credit Admin user, I want full access to create, update, and resolve cases, so my role's RFP §3.7 privileges are reflected in what I can do here.
7. As case-engagement, I want a case automatically opened when an account is flagged for review by Transaction Services or Credit Admin (RFP §3.4), so flags don't just sit as a notification with nothing tracking them to resolution.
8. As case-engagement, I want every case creation, update, and resolution recorded via `audit-trail-lib`, so case history is auditable.
9. As case-engagement, I want to query all open cases for a given account or customer, so a user handling that account sees the full open-item picture in one place.

## Implementation Decisions

- Gradle module `services/case-engagement` (ADR-0007). MongoDB for case documents (ADR-0003) — case shape varies more than the structured financial records elsewhere, matching Mongo's fit for less-uniform schemas.
- Consumes trigger events from other contexts (e.g., an account flagged for review) via Kafka; exposes a REST API for case CRUD, RBAC-scoped per `CONTEXT-MAP.md`'s role table.
- Depends on `libs/audit-trail-lib`.

## Testing Decisions

- Seam: the REST API and the Kafka consumer boundary (case-opening triggers). Testcontainers for MongoDB and Kafka (ADR-0009).

## Out of Scope

- What each other context does before raising a case-opening trigger — each context's own spec.

## Further Notes

- The overdue-alert timeline (how long before a case counts as overdue) needs a concrete default — flag this as an open question for the first ticket rather than inventing a number here.
