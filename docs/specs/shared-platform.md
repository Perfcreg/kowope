> **DRAFT — not yet published.** See `docs/specs/memo-balance.md` for why (gh CLI not installed). Publish as a GitHub issue and delete this file once it's available.

# Spec: shared-platform

## Problem Statement

Every context needs to know who's calling and whether they're allowed to do what they're asking, and every context needs a way to notify a human when something needs attention. Neither exists today, so RBAC and alerting would otherwise be reinvented per service.

## Solution

`shared-platform` provides two services: **authentication-service** (identity, MFA, RBAC token issuance/validation, session timeout) and **notification-service** (a single place every context sends an alert or notification through, regardless of channel).

## User Stories

1. As any MBP user, I want to authenticate with multi-factor authentication, so my access meets RFP §4.3's MFA requirement.
2. As authentication-service, I want to issue a token carrying the user's role (CSM/Recovery Team/Transaction Services/Credit Admin/Maxim Team) and Country/Region scope, so downstream services can enforce RBAC without re-authenticating the user themselves.
3. As authentication-service, I want to enforce session timeouts for inactive users, with a shorter timeout for sensitive data or transactions, so RFP §4.3's session-timeout requirement holds even for high-risk actions.
4. As an administrator, I want to modify a user's role and permissions through a centralized control panel, so RBAC changes don't require a deployment.
5. As authentication-service, I want to log every change to a user's role or permissions, so RFP §3.7's "log any changes to user roles" requirement is met.
6. As any downstream service, I want to validate an incoming token's role/scope claims locally (not by calling authentication-service on every request), so RBAC enforcement doesn't become a bottleneck.
7. As notification-service, I want to accept a notification request from any context (memo balance updates following liquidation, accounts flagged for review, overdue ICAD updates), so RFP §3.4's automated-notification requirement is satisfied from one place.
8. As notification-service, I want to escalate an unresolved action item to the relevant stakeholder within a defined timeline, so RFP §3.4's and §3.9's escalation requirements are met consistently across every context that raises action items.
9. As notification-service, I want to deliver a notification via email (and, later, other channels) without the calling context needing to know the delivery mechanism, so contexts stay decoupled from notification infrastructure.
10. As a Credit Admin user, I want to receive a notification when a loan-status discrepancy is escalated to me (RFP §3.3), so I don't have to poll for it.
11. As notification-service, I want to record every notification sent (recipient, trigger, timestamp) via `audit-trail-lib`, so there's a trail of who was told what and when.
12. As authentication-service, I want to scope a user to their Country by default, with cross-country access only when explicitly configured, so RFP §3.14's country-scoping requirement is enforced at the identity layer, not re-implemented per context.

## Implementation Decisions

- Two Gradle modules: `services/shared-platform/authentication-service`, `services/shared-platform/notification-service` (ADR-0007).
- authentication-service: OAuth2/OIDC via Spring Security (ADR-0007), Redis for session state (ADR-0003).
- notification-service: consumes notification-trigger events from Kafka (ADR-0001) where async fits, exposes a REST endpoint for synchronous notification requests; sends via email initially.
- Both depend on `libs/audit-trail-lib`.
- RBAC role table and Country model are sourced from `CONTEXT-MAP.md`'s shared vocabulary and `reference-data-config`, not redefined here.

## Testing Decisions

- Seam: authentication-service's token-issuance/validation REST API; notification-service's notification-request REST API and Kafka consumer.
- Testcontainers for Redis (authentication-service) and Kafka (notification-service), per ADR-0009.

## Out of Scope

- The Country/GL data model itself — `reference-data-config`'s spec.
- What triggers a notification in each business context — each context's own spec defines when it calls notification-service; this spec only defines the contract it's called through.

## Further Notes

- Every other context's spec should reference `shared-platform`'s notification contract rather than re-describe notification delivery.
