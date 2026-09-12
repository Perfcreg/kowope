# ADR-0011: Apache Camel as the integration framework for the `integration` context

## Status

Accepted

## Context

All four `integration` adapters share the same shape: poll or receive from an external system, translate into a domain event, publish to Kafka (ADR-0001), and retry transient failures before giving up (integration spec, "Further Notes" and User Story 11-12). A hand-rolled `@Scheduled` poller plus manual `RestTemplate`/`KafkaTemplate` calls reimplements, per adapter, exactly what Enterprise Integration Patterns already name and Apache Camel already implements: Polling Consumer, Content-Based Router, Message Translator, Dead Letter Channel.

## Decision

Apache Camel (`camel-spring-boot-starter` + `camel-http`, `camel-kafka`, `camel-jackson`) is the integration framework for every adapter in the `integration` context. Each adapter's business logic (matching rules, event construction) lives in plain Java beans; Camel routes handle the EIP wiring (polling/triggering, splitting, translating, publishing, error handling/redelivery).

The dead-letter-channel error handler is how ticket 5's "retry a transient failure before giving up" requirement is met per adapter, rather than a separate hand-rolled retry layer.

## Consequences

- A new dependency and DSL for the team to know, scoped to this one bounded context — not imposed on the rest of the monorepo.
- Retry/redelivery policy is configured per route (declarative), not hand-written per adapter.
- Camel's testing support (`camel-test-spring-junit5`, `AdviceWithRouteBuilder`) is the pattern for testing routes; external HTTP dependencies (Fineract, Vision, ICAD) are stubbed with WireMock rather than run as Testcontainers, since they're third-party systems this monorepo doesn't own — unlike Postgres/Kafka (ADR-0009), which are.

## Source

Integration spec (issue #2) User Stories 11-12; ADR-0001.
