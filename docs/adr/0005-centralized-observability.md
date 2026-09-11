# ADR-0005: Centralized observability via ELK + Prometheus + Grafana

## Status

Accepted

## Context

RFP §3.12 requires monitoring of process metrics (average response time for memo balance inquiries, ICAD clearance turnaround) and exception reports for process deviations. RFP §4.7 requires 99.9% uptime with disruption-minimizing maintenance. With 8 bounded contexts each running as independent services (ADR-0002), per-service ad hoc logging/metrics would make cross-context process metrics (like end-to-end ICAD clearance time, which spans `integration` → `clearance-orchestration`) impossible to compute reliably.

## Decision

Every service ships logs to a central ELK stack and metrics to Prometheus, visualized in Grafana. Process-level metrics required by RFP §3.12 (inquiry response time, ICAD clearance turnaround) are computed as cross-service traces/dashboards in this stack, not reinvented per service.

## Consequences

- Audit-trail-lib's log schema (see the "audit-trail-lib" note in [CONTEXT-MAP.md](../../CONTEXT-MAP.md)) should be structured so ELK can index it consistently across every service — this is a system-wide logging contract, not a per-context concern.
- Alerting thresholds for the RFP's overdue-action-plan and ICAD-escalation notifications (§3.4, §3.9) can be built on Prometheus alerting rather than each context polling its own database.
- The specific hosting of this stack (managed vs. self-hosted, retention windows) is still open — see the Architect Kickoff decision log.

## Source

RFP §3.12, §4.7; MBP component diagram, layer 9.
