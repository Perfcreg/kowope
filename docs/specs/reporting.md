> **DRAFT — not yet published.** See `docs/specs/memo-balance.md` for why (gh CLI not installed). Publish as a GitHub issue and delete this file once it's available.

# Spec: reporting

## Problem Statement

RFP §3.6/§3.12/§3.15 require dashboards, process metrics, and a full scheduled-reporting subsystem (weekly/monthly/quarterly, GL reconciliation, retention, lineage). Today this is built ad hoc in Excel, with no reconciliation or audit trail on the numbers themselves.

## Solution

`reporting` is a first-class subsystem — per the architect's delta in `CONTEXT-MAP.md`, not just "email + dashboard" — that generates scheduled reports from the other contexts' published events and query APIs, validates them against GL totals, and distributes them per role-based access.

## User Stories

1. As reporting, I want to generate Weekly, Monthly, and Quarterly reports using closed periods (Weekly Mon–Sun, Monthly calendar month, Quarterly fiscal/calendar quarter, all configurable), so RFP §3.15's frequency requirement is met.
2. As reporting, I want report jobs to run on a configurable schedule (default 08:00 WAT on the first business day after period end), with retry and rerun-on-demand, so a scheduling failure doesn't require a manual re-trigger from scratch.
3. As a report consumer, I want to filter any report by Country, Region, Directorate, SOL/Branch, Product/Segment, Currency, Amount band, and Date range, so I can scope the view to what's relevant to me.
4. As reporting, I want to produce a "New Memo Accounts" report per period (count and list of newly flagged accounts, with Account No, Customer ID, SOL, Region, Directorate, First Memo Date/Time, Currency), so RFP §3.15's specific report shape is met exactly.
5. As reporting, I want to produce an "Account Balances – All" report per period (closing balances as-of period end, In-Memo Y/N, Currency, SOL, Region, Directorate), so both memo and non-memo accounts are visible in one place.
6. As reporting, I want to produce a "Memo Account Balances – In-Memo Only" report showing period movement (opening, inflows, outflows, closing), so memo-specific exposure trends are visible without wading through the all-accounts report.
7. As reporting, I want to aggregate write-off/sweep-to-P&L postings per period, grouped by SOL/Region/Directorate, so RFP §3.15's Sweep-to-P&L report is available.
8. As reporting, I want every report to provide sub-totals by SOL/Region/Directorate and grand totals, with drill-down to underlying records, so users aren't stuck at the aggregate level.
9. As reporting, I want every report to show an As-of timestamp and amounts in both local and (if configured) group currency using the period-end FX rate, so cross-currency comparisons are meaningful.
10. As reporting, I want reports distributable via email in XLSX/CSV/PDF, with filenames including report type/period/timestamp, and large files zipped, so RFP §3.15's delivery requirement is met.
11. As reporting, I want recipient lists configurable per report/frequency and role-based, so users only see rows for their permitted Country/Region/Directorate/SOL.
12. As reporting, I want each report run to log job ID, period, filters, row counts, totals, and data-extract version, exportable, so RFP §3.15's data-lineage requirement produces a real audit trail.
13. As reporting, I want to validate that Memo totals reconcile to the Write-Off Register/GL sweep totals for the period, flagging discrepancies above a configurable threshold in the report header, so numbers aren't trusted blindly.
14. As reporting, I want generated reports and their source snapshots retained for a configurable period (default 7 years) with secure storage/retrieval, so RFP §3.15's retention requirement is met.
15. As reporting, I want to use the effective SOL→Region→Directorate and GL-code mapping for the period being reported (from `reference-data-config`), so a report generated today about a past period doesn't silently use today's mapping.
16. As reporting, I want report generation to support at least 100k rows per period within a configurable SLA, with a partial failure blocking publication and alerting the owner rather than publishing incomplete data, so RFP §3.15's performance requirement is met without ever shipping a silently-truncated report.
17. As a stakeholder, I want a centralized dashboard showing memo balances, loan exposure status, and customer account updates in real time, so RFP §3.6's dashboard requirement is met without waiting for a scheduled report.
18. As reporting, I want to compute process metrics (average response time for memo balance inquiries, time taken for ICAD clearance after liquidation) from the other contexts' own events, so RFP §3.12 doesn't require reporting to reinvent that instrumentation.
19. As a stakeholder, I want exception reports for process deviations, with Management Information (MI) reports including corrective actions, so RFP §3.12's exception-reporting requirement is met.

## Implementation Decisions

- Gradle module `services/reporting` (ADR-0007). Reads from PostgreSQL (structured balances/GL data) and MongoDB (case data where relevant) — ADR-0003; doesn't own its own copy of source-of-record data, only the generated report artifacts and their snapshots.
- Report generation is a scheduled job, not request/response — needs a job scheduler; the specific mechanism (Spring `@Scheduled` + a distributed lock, or Quartz) is an implementation-ticket decision, not a spec-level one.
- Report files are stored in Blob storage (ADR-0003), consistent with how `memo-balance` stores documents.
- Consumes `MemoBalanceAdjusted`/`MemoLiquidated` (from memo-balance) and clearance-outcome events (from clearance-orchestration) to compute process metrics without querying those services' internal state directly.
- Delivery via `shared-platform`'s notification-service (email) rather than reporting owning its own mail capability.
- Depends on `libs/audit-trail-lib` for job-run logging (distinct from, but consistent with, the data-lineage log RFP §3.15 itself requires).

## Testing Decisions

- Seam: the report-generation job's own trigger/output (given a fixture of source events/data, assert the exact report content and reconciliation outcome) and the REST/dashboard query API. Testcontainers for Postgres/Mongo/Kafka (ADR-0009) — reconciliation logic especially must be tested against real aggregated data, not a hand-mocked total.

## Out of Scope

- The underlying balance/case/clearance data itself — each producing context's own spec.
- The specific job-scheduling library/mechanism — an implementation-ticket decision.

## Further Notes

- This is the largest spec in scope, matching the architect's delta that the diagram under-represents reporting's real complexity. Expect `to-tickets` to produce more tickets here than for any other context, and expect some to be wide/cross-cutting — the reconciliation logic touches every context that posts a GL entry.
