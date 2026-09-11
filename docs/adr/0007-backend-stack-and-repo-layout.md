# ADR-0007: Backend stack, build tool, and repo layout

## Status

Accepted (Architect Kickoff, 2026-09-11)

## Context

ADR-0002 fixed "containerized microservices on Kubernetes" but not the language, framework, or how the 8 backend contexts (see [CONTEXT-MAP.md](../../CONTEXT-MAP.md)) are laid out across repos. The factory pipeline's execution model ([docs/agents/pipeline.md](../agents/pipeline.md), Phase 6) runs implementer subagents in per-ticket worktrees, merged into a single PR — this only works cleanly against one repo.

## Decision

- **Language/framework**: Java 21 (LTS toolchain) + Spring Boot, single stack across all 8 backend contexts. Verified live against start.spring.io on 2026-09-11 (own knowledge was stale here — assumed Spring Boot 3.x, current stable is actually **Spring Boot 4.1.1** on **Gradle 9.7.1**): pin to Spring Boot 4.1.1 / Gradle 9.7.1 unless a later module setup finds a newer patch worth moving to. Spring for Apache Kafka is the event-bus client (pairs with ADR-0001), Spring Data JPA for PostgreSQL, Spring Data MongoDB for MongoDB, Spring Data Redis for caching/session state (pairs with ADR-0003).
- **Build tool**: Gradle (Kotlin DSL), not Maven — faster incremental builds matter once multiple implementer subagents compile concurrently. Wrapper (`gradlew`/`gradlew.bat`) committed at the repo root, harvested from a verified start.spring.io bootstrap rather than hand-written, so the wrapper jar is a genuine official artifact.
- **Repo layout**: one private GitHub repo, Gradle multi-module monorepo. Each bounded context is a Gradle module (`integration`, `shared-platform`, `reference-data-config`, `memo-balance`, `account-verification`, `clearance-orchestration`, `case-engagement`, `reporting`); the `channels` web app (ADR-0010) lives in the same repo as a separate top-level project, not a Gradle module.
- **API style**: REST + OpenAPI between services and from the gateway, consistent with how the RFP describes "API contracts" throughout §3. No gRPC unless a specific spec surfaces a real need.
- **AuthN/AuthZ**: OAuth2/OIDC via Spring Security, enforced at both the API Gateway (ADR-0004, coarse-grained) and per-context (fine-grained, per the RBAC role table in CONTEXT-MAP.md's shared vocabulary).

## Consequences

- Two previously-installed skills (`setup-ts-deep-modules`, `migrate-to-shoehorn`) are TypeScript-specific and don't apply to this stack. Left installed but dormant — harmless, no action needed.
- Java version and Spring Boot minor version are revisitable defaults, not hard commitments — reopen if the org's eventual deployment target (ADR-0008) mandates a specific certified version.
- Every context's `to-spec` output should assume Spring Boot conventions (controller/service/repository layering per module) unless a spec argues otherwise.

## Source

Architect Kickoff grilling session, 2026-09-11.
