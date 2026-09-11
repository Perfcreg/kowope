# ADR-0009: Testing stack — JUnit 5, Mockito, Testcontainers

## Status

Accepted (Architect Kickoff, 2026-09-11)

## Context

The installed `tdd` skill requires tests to verify behavior through public interfaces/seams, not mocked internals, and flags implementation-coupled mocking as an anti-pattern. Every backend context (ADR-0007: Java 21 + Spring Boot) touches at least one of Postgres, MongoDB, Redis, or Kafka (ADR-0001, ADR-0003).

## Decision

JUnit 5 + Mockito for unit tests; **Testcontainers** for integration tests that need a real Postgres, MongoDB, Redis, or Kafka instance rather than a mock standing in for one. Mockito is reserved for collaborators genuinely worth isolating (per `tdd`'s mocking guidelines), not for the datastore/broker seams themselves.

## Consequences

- Integration tests are slower than pure-mock tests but verify real behavior against the real engines the diagram specifies — directly supports the `tdd` skill's seam discipline.
- CI (once the GitHub repo exists) needs Docker available to run Testcontainers-based tests.

## Source

Architect Kickoff grilling session, 2026-09-11; `.claude/skills/tdd/SKILL.md`.
