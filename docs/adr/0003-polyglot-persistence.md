# ADR-0003: Polyglot persistence — Postgres / Mongo / Redis / Blob storage

## Status

Accepted

## Context

The system has data of genuinely different shapes: structured financial records with strong consistency needs (accounts, balances, GL postings), semi-structured case/document metadata, ephemeral session/cache state, and binary documents (RFP §3.11: non-indebtedness letters, verification records, memo file updates). The MBP component diagram proposes four datastore types rather than forcing everything into one engine.

## Decision

- **PostgreSQL** is the system of record for structured, transactional data: memo balances, account/loan verification data, GL/reporting config tables (SOL → Region → Directorate mappings).
- **MongoDB** holds case and document metadata for `case-engagement` and parts of `reporting` where the schema is less uniform.
- **Redis** is used for caching and session state (supports the session-timeout requirements in RFP §4.3).
- **Blob storage** holds the actual document files (RFP §3.11); Postgres/Mongo hold only metadata and pointers into blob storage, never the binaries themselves.

## Consequences

- No service reaches into another service's datastore directly — each store is owned by the context(s) that model it, accessed by others only through that context's published interface or events, consistent with the [codebase-design](../../.claude/skills/codebase-design/SKILL.md) module discipline.
- Backup/DR (RFP §4.7) and the 7-year report retention requirement (RFP §3.15) must be solved per store, not assumed to be uniform.
- Which specific managed offering (e.g. cloud-managed Postgres/Mongo/Redis vs. self-hosted) is still open — see the Architect Kickoff decision log.

## Source

RFP §3.11, §3.15, §4.3; MBP component diagram, layer 10.
