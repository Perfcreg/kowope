> **DRAFT — not yet published.** See `docs/specs/memo-balance.md` for why (gh CLI not installed). Publish as a GitHub issue and delete this file once it's available.

# Spec: channels

## Problem Statement

RFP §4.10 requires web (and, later, mobile) access to the whole system. Without a presentation layer, users would otherwise need direct access to each backend service.

## Solution

`channels` is a Vite + React SPA (ADR-0010) that composes RBAC-scoped views from the 8 backend contexts via their REST APIs — Admin Dashboard and Branch/Back-office — authenticated through `shared-platform` and routed through the API Gateway (ADR-0004).

## User Stories

1. As any MBP user, I want to log in through channels with MFA, so RFP §4.3's MFA requirement is met at the UI I actually use.
2. As a CSM user, I want a view-only dashboard of memo balances and account details, so my RFP §3.7 role privilege is reflected in what the UI lets me do.
3. As a Recovery Team or Credit Admin user, I want full access — verification, liquidation tracking, reporting — from the same UI, so my broader privileges don't require a separate tool.
4. As a Transaction Services user, I want to edit account data and memo balances from channels, so my RFP §3.7 edit privilege has an actual UI surface.
5. As a Maxim Team user, I want to upload an Excel file and see the import outcome, so RFP §3.7's "view and manage Excel data integration" privilege is usable without a backend tool.
6. As any user, I want the centralized dashboard (RFP §3.6) — memo balances, loan exposure status, account updates — rendered in channels, so I don't need a separate reporting tool for day-to-day visibility.
7. As any user, I want channels to only ever call backend contexts through the API Gateway, so RFP §4.3's single-perimeter security model (ADR-0004) isn't bypassed by the UI talking to services directly.
8. As any user, I want my session to time out per RFP §4.3's inactivity rules, enforced consistently with what `shared-platform` enforces server-side, so the UI doesn't silently keep a stale session alive.
9. As a Credit Admin user, I want to see case-engagement's open cases and clearance-orchestration's clearance status for an account on the same screen as its memo balance, so I don't have to check three tools to understand one account.

## Implementation Decisions

- `channels/` — Vite + React + TypeScript SPA (ADR-0010), no server-side rendering.
- Talks only to the API Gateway (ADR-0004); no direct calls to any backend service's internal address.
- Auth via `shared-platform`'s authentication-service (OAuth2/OIDC, ADR-0007); the token/role drives what's rendered (RBAC-aware UI, not just RBAC-enforced backend).
- Mobile is explicitly out of scope (ADR-0010).

## Testing Decisions

- Seam: component/integration tests against a mocked API Gateway boundary — channels' own tests shouldn't need the whole monorepo running. Real end-to-end tests against the full stack are a separate, later concern once backend contexts exist to test against.

## Out of Scope

- Mobile access (ADR-0010) — a future spec once a mobile approach is decided.
- Any backend business logic — channels only composes and displays what backend REST APIs already decide.

## Further Notes

- channels' first real ticket can't land working screens until at least one backend context (`memo-balance`) has a real REST API to call — sequence accordingly in `to-tickets`/`wayfinder`.
