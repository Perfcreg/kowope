# ADR-0012: Apache Fineract as the local/dev substitute for Finacle

## Status

Accepted

## Context

The RFP's detection mechanism (§3.13(bis)) is specific to Finacle: scan transaction narrations for the phrase "written off". Real Finacle access isn't available to this project. A separate, already-running project on this machine (`mfb-stack`, an unrelated H2H banking demo at `C:\Users\Microsoft\Desktop\Fineract\mfb-stack`) runs Apache Fineract 1.15 with real seed data, reachable at `http://localhost:8444` (tenant `default`, `FINERACT_SERVER_SSL_ENABLED=false`).

Verified live against that instance: Fineract's savings account transactions (`GET /fineract-provider/api/v1/savingsaccounts/{id}?associations=transactions`) carry a free-text `note` field — this is the narration-equivalent the detection logic needs. No loan data exists in the seeded dataset, so Fineract's native "Loan Write-off" transaction type isn't usable here even though it exists in Fineract's domain model.

## Decision

The Write-Off Detection Service polls Fineract's savings-account transaction `note` field for the RFP's phrase match, exactly as it would poll Finacle's narration field. The detection *algorithm* (case-insensitive phrase match, configurable variants) is written against the RFP's spec, not against any Fineract-specific concept — only the REST client is Fineract-shaped. `FineractClient` is an interface for exactly this reason (enterprise-review fix, 2026-09-12: it started as a concrete class, which meant this promise had no actual seam to swap at); `FineractHttpClient` is its Fineract-shaped implementation. Swapping to real Finacle later means writing a new implementation of `FineractClient`, not touching the detection logic or the `MemoDetected` event contract. The Vision ETL Connector applies the identical fix independently for its own `FineractClient`/`FineractHttpClient` pair (ADR-0013) — the two adapters don't share a client, but they do share the same shape.

Credentials (`mifos`/`password`, tenant `default`) are `mfb-stack`'s own committed dev-only values (its `.env` says so explicitly) — not a secret introduced by this project.

## Consequences

- This adapter depends on a container from an unrelated project being up (`docker compose up -d db fineract` in `mfb-stack`) for local development and manual verification. Not wired into this repo's own `docker-compose.yml` — it's borrowed infrastructure, not owned infrastructure.
- Automated tests stub Fineract's HTTP surface with WireMock (ADR-0011) rather than depending on `mfb-stack` being up, so the test suite doesn't couple to another project's lifecycle.
- The polling shape (client → accounts → transactions, three REST calls deep) fits this demo-scale dataset (10 clients). Real Finacle integration will very likely look different (a narration-search endpoint, a CDC feed, or a file extract) — this shape is not assumed to carry over, only the detection algorithm and event contract are.
- **Security note added 2026-09-12 (enterprise-review)**: the Fineract HTTP calls are plaintext (`http://localhost:8444`, loopback-only, `mfb-stack`'s own dev setup) — an accepted local-dev risk. Separately and more significantly, **the Kafka publish of `MemoDetectedEvent`/`VisionBalanceSyncedEvent` has no encryption or SASL configured at all** (`kafka.bootstrap-servers: localhost:9092`, plaintext), carrying full customer PII, narration text, and balance figures into Kafka's on-disk log segments unencrypted — an RFP §4.3 at-rest concern this ADR did not originally cover. Not fixed in code here: this repo has no TLS/SASL-configured Kafka broker anywhere yet (`docker-compose.yml`'s own broker is also plaintext), so securing just one adapter's producer would be inconsistent with every other adapter and memo-balance's own consumer. This is a system-wide gap for a future ADR (likely alongside ADR-0004's still-unbuilt perimeter), not a one-adapter fix. The same note is repeated in ADR-0013 for the Vision ETL Connector's identical gap, and applies equally to the Excel Import Service's `MemoDetectedEvent` publish.

## Source

User request; live verification against `mfb-stack`'s Fineract instance, 2026-09-12.
