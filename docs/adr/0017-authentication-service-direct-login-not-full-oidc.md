# ADR-0017: authentication-service issues tokens via direct login, not a full OAuth2/OIDC Authorization Code flow

## Status

Accepted

## Context

The shared-platform spec's Implementation Decisions say "OAuth2/OIDC via Spring Security" (Spec: shared-platform, issue #3). A full OIDC Authorization Code flow (with PKCE, client registration, redirect URIs, a consent screen) is built for the case where a third-party client needs to obtain a token on a user's behalf without ever seeing their password. This system has no such client — only the first-party `channels` SPA and internal backend services ever consume a token, and there are no external parties integrating against this identity provider.

RFP §4.3 requires MFA for all users and RBAC-scoped, locally-verifiable tokens (User Stories 1, 2, 3, 6); it does not require an Authorization Code flow specifically. Building one anyway would add real complexity (client registration storage, redirect-URI validation, consent UI) that serves no actual requirement here.

## Decision

`authentication-service` exposes a direct, Resource-Owner-style login: `POST /auth/login` (username/password) → `POST /auth/mfa/verify` (TOTP code) → a signed access token + an opaque refresh token. MFA is mandatory for every user — a password check alone never yields a token. Tokens are RS256-signed JWTs; downstream services verify them locally via `GET /.well-known/jwks.json` (User Story 6 — "validate locally... not by calling authentication-service on every request"), not a shared symmetric secret. This closes the dev-secret interim seam that `memo-balance`, `excel-import-service`, and `icad-integration-adapter`'s own `SecurityConfig`s had documented since they were first built.

Session timeout (RFP §4.3, "session timeout for inactive users") is realized as a Redis-backed refresh-token TTL that slides forward on each successful `/auth/refresh` call — a gap in activity longer than the TTL (30 minutes, `auth.session.inactivity-timeout`) forces re-login, not a fixed wall-clock expiry.

"Shorter timeouts... for sensitive data or transactions" is realized as a separate step-up token: `POST /auth/step-up` requires a still-active session *and* a fresh TOTP code, and issues a short-lived (5 minute) token carrying `session_class: "sensitive"` (alongside the standard `roles` claim and an `amr: ["pwd", "otp"]` claim recording that both factors were used). **This is a deliberately partial fix, not a completed one**: `icad-integration-adapter`'s `SecurityConfig` had already flagged (2026-09-12 enterprise-review) that no `amr`/`acr`-style claim convention existed yet to gate a sensitive action like pushing an ICAD clearance. That claim convention now exists, but no downstream endpoint checks it yet — enforcing `session_class: "sensitive"` on a specific sensitive action is each context's own future work, the same deferred-gap pattern already used for Country-scoping (blocked on `reference-data-config`).

## Consequences

- Downstream services keep their existing `roles`-claim contract unchanged (`JwtRoleConverter` in each service is untouched); only each `SecurityConfig.jwtDecoder()` bean and its `application.yml` `security.jwt.*` property changed, from a symmetric dev secret to a JWKS URI. No test files needed to change — every downstream test injects auth via `SecurityMockMvcRequestPostProcessors.jwt()`, which bypasses the decoder bean entirely.
- `write-off-detection-service` and `vision-etl-connector` have no REST endpoint and never had a `SecurityConfig` — nothing to migrate there.
- The RSA signing key is generated fresh at `authentication-service` startup, not persisted (see `SigningKeys`). Restarting it invalidates every previously-issued token; downstream services simply re-fetch the new JWKS and reject old tokens as unverifiable (fails closed, just inconvenient). A real deployment needs a persisted/rotated key or an external KMS — out of scope per ADR-0008's local-first environment.
- No downstream endpoint enforces `session_class: "sensitive"` yet — a standard-session token still authorizes every currently-built sensitive action (e.g., `icad-integration-adapter`'s clearance push). This is flagged, not silently treated as solved.
- If this system ever needs to let a genuine third-party client obtain tokens on a user's behalf, this ADR's decision should be revisited — a direct-login-only IdP cannot safely support that without exposing user passwords to the third party.

## Source

Spec: shared-platform (issue #3) Implementation Decisions and User Stories 1, 2, 3, 6; RFP §3.7, §4.3; `icad-integration-adapter`'s `SecurityConfig` Javadoc (2026-09-12 enterprise-review finding, flagging the missing claim convention this ADR closes).
