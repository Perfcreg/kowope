# Enterprise Delivery Pipeline — The Architect-Bookended Factory

How MBP work moves from RFP to shipped, enterprise-grade code. Maps [Matt Pocock's 7 phases](https://www.aihero.dev/my-7-phases-of-ai-development) onto the skills installed in `.claude/skills/`, extended with an Architect role that opens and closes every effort, and a 5-gate quality chain that every ticket must clear before it counts as done.

Read [CONTEXT-MAP.md](../../CONTEXT-MAP.md) and the root [docs/adr/](../adr/) before starting any phase below.

## Roles

- **Architect** (persistent, bookends everything): owns `CONTEXT-MAP.md`, root ADRs, and cross-context contracts. Opens an effort by locking whatever's undecided (Phase 0); closes it by checking system-wide coherence across every gate report (Phase 8). One human or one agent session can hold this role, but it's a role, not a person — any session doing this work follows this section.
- **Context owner** (per bounded context): drives that context's spec and tickets.
- **Implementer subagent** (per ticket): does the work, in its own worktree, TDD-disciplined.
- **Gate reviewer** (per axis, ×5): a parallel sub-agent that can only fail a ticket on its own axis, never rerank across axes — same discipline as `code-review`'s existing two.

## Phase 0 — Architect Kickoff (new)

Before any context's spec work starts, lock what the RFP leaves open and the diagram doesn't decide: tech stack per service, monorepo tooling, deployment target, and the [ADR-0006](../adr/0006-loan-segment-status-ownership-PENDING.md) ownership question. Call the Skill tool for `grilling` and `domain-modeling` together. Output: ADRs, never assumptions.

## Phase 1–3 — Idea, Research, Prototype

- **Idea**: the RFP ([docs/rfp/](../rfp/)) is the seed; use `grilling` only to fill what it leaves open, not to re-litigate what it already states.
- **Research** (optional, per context): call `research` for anything needing a primary source outside this repo — Finacle narration formats, Vision's calculation algorithms, ICAD's actual API/file contract, Kafka schema registry conventions. One `RESEARCH.md` per context, refreshed when stale.
- **Prototype** (optional, per context): call `prototype` for the genuinely risky pieces before committing to an interface — the write-off narration-matching heuristic, the ICAD integration shape (webhook vs. polling), the RBAC authorization model.

## Phase 4 — Spec

One `to-spec` per bounded context (8 specs — see [CONTEXT-MAP.md](../../CONTEXT-MAP.md)), each synthesizing that context's RFP sections. Apply `ready-for-agent` per the skill's default.

## Phase 5 — Tickets

The whole RFP is too large for one session, so `wayfinder` holds the cross-context map: destination = "every bounded context has a spec and its tickets published," tickets = "produce context X's spec" (grilling type) then "break context X's spec into tickets" (task type, resolved via `to-tickets`). Each spec fans into tracer-bullet vertical slices with blocking edges, same as any other repo.

## Phase 6 — Execution

`implement-spec`'s pattern: a draft PR per context (or per ticket, for small contexts), implementer subagents in isolated worktrees working the frontier of unblocked tickets, `tdd`-disciplined, merged by a merger subagent as each completes.

## Phase 7 — Quality Gates (the factory floor)

Every ticket clears all 5 gates before it's marked done. Run them with `enterprise-review` (`.claude/skills/enterprise-review/SKILL.md`), which extends `code-review`'s two-axis mechanics to five:

| # | Gate | What it checks | Skill/mechanism |
|---|---|---|---|
| 1 | Standards | Repo conventions + Fowler smell baseline | `code-review`'s existing Standards axis |
| 2 | Spec | Diff matches the originating ticket/spec | `code-review`'s existing Spec axis |
| 3 | Security & Compliance | RBAC enforcement per the role table, encryption in transit/at rest, audit-log completeness, MFA, session timeouts, ISO 27001/GDPR/Basel/SOX considerations | `enterprise-review` axis 3, checklist sourced from RFP §4.3 |
| 4 | Architecture Conformance | Deep-module/seam discipline (`codebase-design` vocabulary), no bounded-context leakage, matches root ADRs | `enterprise-review` axis 4 |
| 5 | Integration Contract | Kafka event schema and REST contract diffs match what the ADRs/specs agreed | `enterprise-review` axis 5, checked against ADR-0001 and the context's spec |

A failed gate spawns a fix ticket back into that context's frontier (same mechanism `to-tickets` already uses) — never a silent patch on top of a failed review.

## Phase 8 — Architect Sign-off (new)

The Architect reads the aggregate gate reports across every context touched, checks for system-wide coherence (do two contexts now disagree about something an ADR settled?), updates `CONTEXT-MAP.md`/ADRs if the work changed the model, and closes the relevant `wayfinder` ticket.

## State machine, one ticket

```
ready-for-agent → in progress (implementer subagent, TDD)
                → gates 1-5 (enterprise-review, parallel)
                     any gate fails → fix ticket → back to "in progress"
                     all gates pass → merged → Architect sign-off → done
```
