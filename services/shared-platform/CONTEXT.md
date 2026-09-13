# shared-platform

Two services every other context depends on but doesn't own: **authentication-service** (identity, MFA, RBAC token issuance/validation, session timeout — RFP §3.7, §4.3, §4.5) and **notification-service** (a single place every context sends an alert through, regardless of channel — RFP §3.4). authentication-service is built; notification-service isn't yet.

## Language

**Direct login** (not OAuth2/OIDC Authorization Code): authentication-service issues tokens via `POST /auth/login` → `POST /auth/mfa/verify`, not a redirect-based third-party-client flow. There are no third-party OAuth clients in this system — only the first-party `channels` SPA and internal services consume tokens — so a full Authorization Code + PKCE + consent flow would add real complexity (client registration, redirect-URI validation) with no requirement it serves. See ADR-0017.

**Session** (RFP §4.3 "session timeout for inactive users"): a Redis-backed opaque refresh token whose TTL slides forward on each successful `/auth/refresh` call. "Session timeout" here means a real gap in activity longer than the configured window (`auth.session.inactivity-timeout`, 30 minutes by default), not a fixed wall-clock expiry.

**Step-up / sensitive-action token** (RFP §4.3 "shorter timeouts... for sensitive data or transactions"): a short-lived (5 minute), non-refreshable access token from `POST /auth/step-up`, requiring a still-active session *and* a fresh TOTP code. Carries `session_class: "sensitive"` instead of the standard token's `session_class: "standard"`. **Issuing this token is built; no downstream endpoint checks the claim yet** — enforcing it on a specific sensitive action (e.g., `icad-integration-adapter`'s clearance push) is that context's own future work, same deferred-gap pattern as Country-scoping waiting on `reference-data-config`.

## Published REST contract (authentication-service)

- `POST /auth/login` (`username`, `password`) → `202 Accepted` `{pendingLoginId, mfaRequired: true}`. Never returns a token — MFA is mandatory for every account (RFP §4.3, no opt-out), so a password check alone is never sufficient.
- `POST /auth/mfa/verify` (`pendingLoginId`, `code`) → `200` `{accessToken, refreshToken, tokenType: "Bearer"}`. The `pendingLoginId` handle is single-use — consumed (deleted) whether the code is right or wrong, so a wrong guess forces a fresh `/auth/login`, not a retry on the same handle. A deliberate anti-bruteforce property, not a bug.
- `POST /auth/refresh` (`refreshToken`) → `200` `{accessToken, tokenType}`. Slides the session's Redis TTL forward. Does **not** rotate the refresh token itself — see Interim seams below.
- `POST /auth/step-up` (`refreshToken`, `code`) → `200` `{accessToken, tokenType}` with `session_class: "sensitive"`.
- `POST /auth/logout` (`refreshToken`) → `204 No Content`. Explicitly ends a session rather than waiting for its TTL to lapse; idempotent (logging out an already-expired or unknown token is not an error).
- `GET /.well-known/jwks.json` → the public JWK Set. Every downstream `JwtDecoder` fetches this instead of calling authentication-service per request (RFP §3.7 User Story 6). If authentication-service is unreachable when a downstream service needs to verify a token, that surfaces as a 401 on the first affected request (JWKS is fetched lazily, not at startup) — the downstream service itself still starts up fine.

**Access token claims**: `sub` (username), `roles` (JSON array of RFP role names — `CSM`, `RECOVERY_TEAM`, `TRANSACTION_SERVICES`, `CREDIT_ADMIN`, `MAXIM_TEAM`; this exact claim key/shape is what every downstream `JwtRoleConverter` already expects and is unchanged by this ticket), `amr` (`["pwd", "otp"]`, always — both factors are always used), `session_class` (`"standard"` or `"sensitive"`), `iat`, `exp`. Signed RS256; `kid`-tagged for JWKS lookup. **No Country/Region claim yet** — not just unenforced, not issued at all (see Interim seams below and ADR-0017).

## Dev-only seeded users

`DevUserSeeder` seeds one user per RFP role (`csm.dev`, `recovery-team.dev`, `transaction-services.dev`, `credit-admin.dev`, `maxim-team.dev`) on startup when `app.seed-dev-users=true` (the local default) — Ticket 02 (admin control panel) is what will let a real administrator create/manage users; until then this is how the service is actually exercisable. Password: `Dev-Only-Password-123!` (committed, dev-only, same disclosure pattern as ADR-0012's Fineract `mifos`/`password`). Each dev user's TOTP secret is deterministically derived from a fixed seed string (`DevSecrets.forRole`, RFC 4648 Base32 of `"mbp-dev-seed-" + roleName`) — reproducible across restarts, addable to a real authenticator app (Google Authenticator, Authy) for manual testing, and never used for a production-enrolled user's actual secret (those come from real enrollment via `TotpService.generateSecret()`).

## Interim seams / deferred gaps

- **No downstream `session_class` enforcement** (see Language above) — issuing the claim is done, checking it is each context's own future work.
- **No Country/Region claim at all** (RFP User Stories 2, 12) — blocked on `reference-data-config`'s Country model, which doesn't exist yet; there's nothing real to source the claim's value from. Same underlying block as `memo-balance`'s already-documented Country-scoping deferral, but distinct in kind: this is a missing claim, not just an unenforced one. See ADR-0017.
- **No refresh-token rotation**: the same opaque refresh token stays valid for its whole sliding 30-minute window, however many times it's used — no absolute session ceiling, and a leaked token is usable for as long as it keeps getting refreshed. `/auth/logout` at least allows explicit termination. Rotating the token on every `/auth/refresh` call would change that endpoint's response shape (it would need to return a new refresh token too) — deliberately deferred to its own pass rather than folded in here. See ADR-0017.
- **Signing key is ephemeral** (`SigningKeys`, generated fresh at startup, never persisted). Restarting authentication-service invalidates every previously-issued token; downstream services simply re-fetch the new JWKS and reject old tokens (fails closed). A real deployment needs a persisted/rotated key or an external KMS — out of scope per ADR-0008's local-first environment.
- **No admin-driven user/role management yet** (Ticket 02) — `DevUserSeeder` is the only way users exist today.
- **notification-service doesn't exist yet** (Ticket 03) — every other context's `NotificationClient` interim seam (a logging stand-in) still points at nothing real.

## Architecture

**ADR-0017**: direct MFA-gated login instead of a full OAuth2/OIDC Authorization Code flow; RS256 + JWKS instead of a shared symmetric secret (closing the `security.jwt.dev-secret` interim seam `memo-balance`, `excel-import-service`, and `icad-integration-adapter`'s own `SecurityConfig`s had documented — `write-off-detection-service` and `vision-etl-connector` have no REST endpoint and never had one); the deferred `session_class` enforcement gap.

**MFA**: RFC 6238 TOTP via `dev.samstevens.totp` — a real, verified implementation, not hand-rolled HMAC code (same rationale as this repo using Apache POI for xlsx and Camel for EIPs instead of reinventing them).

**Persistence**: Postgres for the `app_user`/`app_user_role` tables (Flyway-migrated); Redis for refresh-token sessions and pending-login handles (ADR-0003). Business logic (`AuthService`, `TokenService`, `TotpService`) is plain, Spring-independent orchestration over injected collaborators — the same "logic in beans, Spring only wires it" discipline used throughout `integration`.

**Audit trail (ADR-0005)**: every login/MFA/refresh/step-up/logout outcome is recorded via `AuditLogger`, including the rejection paths (an expired/reused pending-login handle, an invalid/expired refresh token) — an enterprise-review fix, 2026-09-13, since those are exactly the "someone is probing with a stale or stolen token" signals the audit trail exists to catch. A rejected refresh/step-up token is logged by a short SHA-256 fingerprint, never its raw value — the point is enough detail to correlate repeated rejections of the same token, not enough to leak or replay the live credential.
