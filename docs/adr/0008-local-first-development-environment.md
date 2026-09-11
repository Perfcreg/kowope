# ADR-0008: Local-first development environment; production target deferred

## Status

Accepted (Architect Kickoff, 2026-09-11)

## Context

ADR-0002 commits to Kubernetes for the real deployment but doesn't fix an environment for building and proving the system out. The user is developing on a personal Windows machine for now; the production hosting decision (on-prem/UBA-hosted vs. cloud vs. Heirs' existing stack — the options raised during Architect Kickoff) is explicitly deferred to the org once there's a working system to evaluate.

## Decision

- **Inner dev loop**: `docker-compose` brings up Postgres, MongoDB, Redis, and Kafka locally; each Spring Boot service runs directly (not containerized) against them for fast TDD cycles.
- **Cross-service integration testing**: once a piece of work needs the event bus or gateway exercised end-to-end, stand up local Kubernetes via **Docker Desktop's built-in Kubernetes** (already available on this Windows machine once Docker Desktop is installed for the compose step above).
- **Production target**: explicitly **not decided yet**. Options raised (UBA on-prem/private-cloud, Nigeria/Africa-region public cloud, Heirs' existing hosting) stay open until the org reviews a working system. Package for portability (Helm charts, not environment-specific manifests) so the eventual choice doesn't force a rewrite.

## Consequences

- No production infrastructure work happens until this ADR is revisited and closed with a real target.
- Helm charts (not raw manifests, not a specific cloud provider's IaC) are the packaging format from the start, so local Docker Desktop k8s and whatever production target gets chosen later both consume the same charts.
- CI (once the GitHub repo exists) should run the `docker-compose`-based tests, not assume a Kubernetes cluster is available in CI.

## Source

Architect Kickoff grilling session, 2026-09-11.
