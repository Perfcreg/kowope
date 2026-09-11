## Agent skills

### Issue tracker

Issues and specs live as markdown files under `.scratch/<feature-slug>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Multi-context layout: 9 bounded contexts, see `CONTEXT-MAP.md` and `docs/agents/domain.md`.

### Bounded contexts

`integration`, `shared-platform`, `reference-data-config`, `memo-balance`, `account-verification`, `clearance-orchestration`, `case-engagement`, `reporting`, `channels`. See `CONTEXT-MAP.md` for what each owns and the root `docs/adr/` for system-wide decisions.

### Tech stack (locked at Architect Kickoff, see docs/adr/0007–0010)

Java 21 + Spring Boot 4.1.1, Gradle multi-module monorepo (`./gradlew build` at root), one private GitHub repo. `docker-compose` for local dev, Docker Desktop Kubernetes for local integration testing — production target deferred to the org. JUnit 5 + Mockito + Testcontainers. `channels` is a Vite + React + TypeScript SPA; mobile is deferred.

Repo layout: `services/<context>/<service>` — one Gradle module per deployable service; `libs/audit-trail-lib` — the shared audit-logging contract every service depends on; `channels/` — the web app, a separate npm project, not a Gradle module.

### Enterprise delivery pipeline

RFP → spec → tickets → implementation → a 5-gate enterprise review (Standards, Spec, Security & Compliance, Architecture Conformance, Integration Contract) → architect sign-off. See `docs/agents/pipeline.md`; the review gate is the `enterprise-review` skill.
