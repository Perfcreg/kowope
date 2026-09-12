# ADR-0014: ICAD Integration Adapter models a push/fetch contract with no real substitute

## Status

Accepted

## Context

Unlike Finacle and Vision, there's no already-running system on this machine that can stand in for ICAD the way Fineract does for the other two (ADR-0012, ADR-0013). Per the user's domain knowledge: ICAD (the Industry Customer Accounts Database) is NIBSS's real platform for financial institutions to upload and manage customer account data so that account information lookup works across multiple banks. Its real operational shape is two capabilities:

- **pushAccount** — a bank pushes a customer account record (here, a written-off account being cleared) into the industry database.
- **fetchAccount** — a bank fetches an account record back, to look up or confirm status.

RFP §3.8/§4.6 describe the *business* requirement (clear a customer's name from ICAD within 24-48 hours of liquidation, notify on discrepancy) but not ICAD's actual API shape — no field names, no endpoint paths, no auth scheme. No NIBSS ICAD sandbox or API documentation is accessible to this project.

## Decision

`IcadClient` models a push/fetch REST contract shaped by the description above: `POST /icad/v1/accounts` (pushAccount) returns a reference and a PENDING status; `GET /icad/v1/accounts/{reference}` (fetchAccount) returns the current status (PENDING/CLEARED/DISCREPANCY/FAILED) and, once resolved, ICAD's detail text. Because real clearance takes 24-48 hours (RFP §3.8), the adapter is push-then-poll: `IcadClearanceController` exposes a synchronous `POST /icad/clearance-requests` capability (integration spec User Story 6) that pushes and returns a PENDING reference immediately; a Camel Polling Consumer (ADR-0011) periodically fetches every still-pending reference and publishes an `IcadClearanceOutcomeEvent` once ICAD resolves it (User Story 7), carrying ICAD's raw detail for `clearance-orchestration` to escalate with (User Story 14).

This is the one adapter of the four with **zero real-system verification** — every environment it has ever run against, including manual local testing, is a WireMock stub of the contract above, not a real ICAD environment. That is a materially different risk profile than ADR-0012/0013's Fineract substitution, where the REST client is verified against a real running system even though the client is scoped narrowly. Here, both the client *and* the contract it targets are unverified assumptions.

## Consequences

- If real ICAD's actual API differs in field names, status vocabulary, auth scheme, or synchronicity (e.g., if pushAccount is itself synchronous with no fetch step, or is file-based rather than REST), `IcadClient` and this ADR's modeled contract need to change — but the clearance domain logic (`IcadClearanceProcessor`, `PendingClearanceStore`) and the `IcadClearanceOutcomeEvent` contract downstream consumers build against should not need to.
- `clearance-orchestration`'s own spec (not yet written) should treat `IcadClearanceOutcomeEvent`'s exact shape as provisional until real ICAD access is confirmed, not as a locked contract the way `MemoDetectedEvent` is.
- The user should flag if real NIBSS ICAD sandbox credentials or API documentation become available — that would upgrade this adapter to the same verification tier as Fineract's adapters, not just improve its tests.
- **Security note added 2026-09-12 (enterprise-review)**: `pushAccount` sends `customerName` and BVN (Bank Verification Number — sensitive PII under Nigeria's NDPR/GDPR-equivalent regime) in the request body. `icad.base-url` has no scheme enforcement — it will happily point at a plaintext `http://` endpoint, which is what every environment currently does (a local WireMock stub). This is the same deferred-TLS situation ADR-0012/0013's Fineract adapters already have, made explicit here because this is the first adapter whose external call carries RFP §4.3-sensitive PII rather than just account/balance figures. This repo has no TLS-terminating perimeter yet (ADR-0004's API Gateway/WAF isn't built) — before pointing `icad.base-url` at anything other than a local stub, that perimeter (or direct TLS on the ICAD endpoint) must exist first. Not fixed in code here: there is no profile/environment mechanism anywhere in this repo yet to safely gate a "reject http:// outside local dev" check without inventing one speculatively.

## Source

User request, 2026-09-12 (real NIBSS ICAD push/fetch operational description); RFP §3.8, §4.6; integration spec (issue #2) User Stories 6-7, 14.
