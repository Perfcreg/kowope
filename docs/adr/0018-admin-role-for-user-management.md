# ADR-0018: a distinct ADMIN role for the user-management control panel

## Status

Accepted

## Context

RFP §3.7 lists exactly five RBAC roles for business data access: CSM, Recovery Team, Transaction Services, Credit Admin, Maxim Team. RFP §4.5 separately says:

> "The solution must provide centralized user management with role-based access and permissions for teams, such as: ... Edit and management privileges for Transaction Services, Credit Admin, and **Admin teams**." (§4.5)
>
> "**Administrators** must be able to modify user roles and permissions via a centralized control panel." (§4.5)

"Admin teams" is listed as a third, separate item alongside "Transaction Services" and "Credit Admin" — not a restatement of Credit Admin under a different name. Read literally, the RFP treats system/user administration as its own persona, distinct from the five business-data roles §3.7 names.

## Decision

Add a sixth role, `ADMIN`, to `authentication-service`'s `Role` enum and the `roles` JWT claim vocabulary. `ADMIN` is a **system-administration** persona only: it authorizes `authentication-service`'s own `/admin/**` control-panel endpoints (create user, list users, change a user's roles) and carries **no** implicit privilege over any business data in any other context. No downstream service's `SecurityConfig`/`JwtRoleConverter` grants `ROLE_ADMIN` anything — an `ADMIN`-only user has zero access to memo balances, accounts, or any other business-data endpoint, matching least-privilege: managing identities is not the same capability as being allowed to touch the data those identities can see.

The admin panel is the first place `authentication-service` becomes a resource server for its own routes, not just a token issuer — `/admin/**` requires `hasRole("ADMIN")`, validated via the same RSA keypair `authentication-service` already signs tokens with (`SigningKeys`), decoded in-process rather than via its own JWKS endpoint over HTTP.

`DevUserSeeder` (ADR-0017) already iterates every `Role` value to seed one dev user per role — adding `ADMIN` to the enum means it now also seeds `admin.dev`, automatically becoming the bootstrap administrator who can create every subsequent real user through this panel. No seeder code change was needed for this.

A safeguard was added that the RFP doesn't explicitly ask for but the panel's own design makes newly possible: `AdminUserService.replaceRoles` refuses a role change that would leave the system with zero `ADMIN` users at all (a `409 LastAdminException`). Without it, the panel could accidentally lock every administrator out of itself with no recovery path short of a database edit — a cheap check against a real, self-inflicted failure mode.

**"Roles and permissions" are treated as one dimension, not two** (enterprise-review finding, 2026-09-13, Spec axis). Both RFP §4.5 quotes above say "roles and permissions" as if they're separate things, but this system — going back to `CONTEXT-MAP.md`'s original RBAC table from Architect Kickoff, not something newly decided in this ticket — has always modeled a "permission" as fully implied by role membership (e.g., "CSM: View-only access," "Recovery Team: Full privileges") with no independent per-role capability set underneath. This ADR makes that pre-existing, system-wide interpretation explicit rather than leaving it implicit: `Role` is a flat enum, `UpdateRolesRequest`/`CreateUserRequest` carry only `roles`, and there is no separate "permissions" concept anywhere in this codebase to modify. If a future requirement genuinely needs finer-grained permissions independent of role membership (e.g., two CSMs with different account-visibility scopes), that's new modeling work, not a gap in this ticket specifically.

## Consequences

- `CONTEXT-MAP.md`'s RBAC role table gains an `ADMIN` row, describing it as system-administration only, not a business-data role.
- Every downstream `JwtRoleConverter` is unaffected — they map whatever role strings appear in the claim generically; none of them will ever check for `ROLE_ADMIN` since nothing there is gated on it.
- User creation via the admin panel generates a real MFA secret per user (`TotpService.generateSecret()`), returned exactly once in the creation response — distinct from `DevUserSeeder`'s deterministic dev-only secrets, which exist only to bootstrap local testing.
- If a future ticket introduces a genuinely different "administrator" concept (e.g., a per-Country admin, once `reference-data-config` exists), this ADR's single global `ADMIN` role should be revisited — it currently has no scoping dimension at all.
- **The last-admin safeguard only guards against a race within a single JVM instance** (enterprise-review finding, 2026-09-13, Security & Compliance axis): `AdminUserService.replaceRoles` serializes its check-then-act with an in-process lock, not a database-level one. Two concurrent demotions of two different admins, submitted to two different instances of a horizontally-scaled deployment (ADR-0002's eventual target), could still both pass and leave zero admins. Accepted for now under ADR-0008's local-first, single-instance scope — the same class of assumption as the in-memory dedup stores flagged in ADR-0015/ADR-0016. A real multi-instance deployment needs a database-level lock (e.g. `SELECT ... FOR UPDATE`) instead.
- `AdminUserController`/`AdminUserService` validate that `username`, `password`, and `roles` are non-blank/non-empty, returning a clear `400` rather than an uncaught `500` (a missing `roles` field) or silently persisting an empty-string password (enterprise-review finding, 2026-09-13, Security & Compliance axis).

## Source

RFP §3.7, §4.5 (quoted above); Spec: shared-platform (issue #3) User Stories 4, 5; user decision (this session) resolving the "Admin teams"/"Administrators" ambiguity as a distinct 6th role rather than a Credit Admin alias, with full user-lifecycle scope (create/list/edit-roles) rather than role-edits-only.
