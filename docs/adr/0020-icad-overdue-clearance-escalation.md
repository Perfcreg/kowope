# ADR-0020: escalate overdue ICAD clearances, not a generic action-item abstraction

## Status

Accepted

## Context

`shared-platform`'s spec (issue #3) User Story 8 asks for "an unresolved action item... escalate[d]... to the relevant stakeholder within a defined timeline" (RFP §3.4). RFP §3.9 ("Action Plan Tracking and Resolution") names three example action-plan types — "liquidation updates, verification requests, and ICAD escalations" — and requires alerting on overdue ones and escalating unresolved items to stakeholders within specified timelines.

Of those three, only **ICAD clearances** have a real pending/resolvable state machine in this codebase today: `icad-integration-adapter`'s `PendingClearanceStore` (ADR-0015) already tracks clearances pushed to ICAD but not yet resolved, polled every 30s by `IcadClearanceProcessor.pollForOutcomes()`. "Liquidation updates" (`memo-balance`) publish a one-shot completed event (`MemoLiquidatedEvent`), not a pending item with an open/resolved lifecycle. "Verification requests" belong to `account-verification`, which doesn't exist yet. Building a generic, multi-context "ActionItem opened/resolved" event contract now — with exactly one real publisher and no other context ready to use it — would be Speculative Generality (the same Fowler smell this repo's own enterprise reviews check for), not a solved problem.

RFP §3.8/§4.6 also give ICAD clearance a literal, quantified timeline: "clear customer names... within 24–48 hours after liquidation."

## Decision

Scope the escalation engine to **overdue ICAD clearances only**, built inside `icad-integration-adapter`'s existing poll cycle rather than as a new component in `notification-service`:

- `PendingClearance` gains a `pushedAt` (`Instant`) field, set when a clearance is first pushed.
- `IcadProperties.escalationSla` (`Duration`, default `PT48H` — the outer edge of the RFP's stated window, the correct reading of "overdue") is checked against a still-pending item's age on every poll cycle.
- A clearance crossing that threshold escalates exactly once (`IcadClearanceProcessor.escalatedReferences`, an in-memory `Set<String>`) via `notificationClient.escalate("CREDIT_ADMIN", ...)` — a new second method on this adapter's own `NotificationClient`/`HttpNotificationClient`, alongside the existing `alertOperations` (which pages internal ops, not business stakeholders). `notification-service`'s `POST /notifications` (built in Ticket 03) is reused unchanged, with the same `CREDIT_ADMIN` recipient group Ticket 03 already configured for ICAD discrepancies.
- The escalated-set entry is cleared the moment the clearance resolves, inside the same poll cycle's resolved branch.
- Every escalation is audited (`ICAD_CLEARANCE_ESCALATED`, actor `"system"`, affected record the account number, detail carrying the ICAD reference, how long it was pending, and the configured SLA).

This is a **different** trigger from Ticket 03's `IcadClearanceOutcomeListener` (which notifies `CREDIT_ADMIN` when ICAD *responds* with `DISCREPANCY`/`FAILED`/`UNKNOWN`). This ADR covers ICAD *not responding at all* within the acceptable window — the clearance is still `PENDING`.

- **A shared `"OPERATIONS_ALERT"` logger name covering both ops alerts and escalation failures** was a real, minor find (enterprise-review, 2026-09-14, Standards axis): `HttpNotificationClient`'s fallback-on-failure log line used one logger category for two distinct signals (an internal ops page vs. a business-stakeholder escalation failing to send), making them indistinguishable without parsing message text. Fixed with a second, distinctly-named logger (`ESCALATION_ALERT`) for the `escalate()` path. A companion test (`anUnreachableNotificationServiceDuringEscalationStillRecordsTheAuditEvent`, Security & Compliance axis) was added to prove end-to-end — with a real `HttpNotificationClient` against an unreachable notification-service, not a mock — that a swallowed HTTP failure during escalation never suppresses the `ICAD_CLEARANCE_ESCALATED` audit record.

## Consequences

- `escalatedReferences` is in-memory only, the same restart-loses-state nature as `PendingClearanceStore` itself (ADR-0015) — a restart re-arms escalation for anything still overdue. This is the safe failure direction (re-escalates rather than silently never escalating again), not a new risk category.
- RFP §3.9's "accounts flagged for review"-style bullets for `memo-balance`/`account-verification` remain unwired — same honest-gap treatment as ADR-0019's undischarged "flagged for review" trigger, not fabricated here either. When `account-verification` or `case-engagement` builds a real pending-item lifecycle, the same pattern (age-check inside the owning context's own poll/scan loop, escalate via `notification-service`'s existing REST contract) applies without needing a new shared abstraction.
- `IcadClearanceProcessor`'s constructor gained a new `IcadProperties` parameter; `PendingClearance`'s record signature changed (a 4th component) — both are internal to this adapter, no cross-context or REST-contract impact.

## Source

RFP §3.4, §3.8, §3.9, §4.6; Spec: shared-platform (issue #3) User Story 8; `services/integration/CONTEXT.md`'s ADR-0015 (pending-clearance in-memory state) and this repo's ADR-0019 (notification-service, whose `POST /notifications` and `CREDIT_ADMIN` recipient group this ADR reuses unchanged).
