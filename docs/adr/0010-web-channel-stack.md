# ADR-0010: Web channel stack; mobile deferred

## Status

Accepted (Architect Kickoff, 2026-09-11)

## Context

RFP §4.10 requires web and mobile access. The diagram's Channels layer (Admin Dashboard, Branch/Back-office, UBA Customer Channel) sits inside the MBP solution boundary. These are internal, authenticated tools reached only through the API Gateway (ADR-0004) — not public-facing, no SEO requirement.

## Decision

Web channels are in scope for this build, as the `channels` context (added to [CONTEXT-MAP.md](../../CONTEXT-MAP.md)): a **Vite + React + TypeScript SPA**, no server-side rendering framework. Mobile access is explicitly **deferred** — out of scope until a later decision names a specific approach (native, React Native, or wrapped web).

## Consequences

- `channels` has no persisted domain data of its own; it composes views from the 8 backend contexts via their REST APIs (ADR-0007), scoped by the RBAC role table.
- RFP §4.10's mobile requirement is a known gap, not silently dropped — tracked here until a future ADR closes it.

## Source

Architect Kickoff grilling session, 2026-09-11; RFP §4.10.
