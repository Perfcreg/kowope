---
name: enterprise-review
description: "Five-axis enterprise review of the diff between HEAD and a fixed point, for this repo's MBP factory pipeline: Standards, Spec, Security & Compliance, Architecture Conformance, and Integration Contract. Runs all five in parallel sub-agents and reports them side by side, never reranked. Use as the Phase 7 quality gate in docs/agents/pipeline.md before any ticket is marked done."
---

# Enterprise Review

Five-axis review of the diff between `HEAD` and a fixed point the user supplies. Extends the installed `code-review` skill's two axes (Standards, Spec) with three more this repo's enterprise/regulatory context demands: Security & Compliance, Architecture Conformance, Integration Contract. Uses the same mechanics as `code-review` — same fixed-point pinning, same parallel-sub-agent isolation, same no-reranking-across-axes rule — just with five sub-agents instead of two.

Read [CONTEXT-MAP.md](../../../CONTEXT-MAP.md) and the root [docs/adr/](../../../docs/adr/) before running this; every gate below checks against them.

## Process

### 1. Pin the fixed point

Whatever the user said is the fixed point (a commit SHA, branch name, tag, `main`, `HEAD~5`, etc.). If they didn't specify one, ask for it.

Capture `git diff <fixed-point>...HEAD` (three-dot) and `git log <fixed-point>..HEAD --oneline`. Confirm the fixed point resolves and the diff is non-empty before spawning anything.

### 2. Identify the ticket/spec and the bounded context

Same discovery order as `code-review` (issue references in commits, a path argument, a spec file under `docs/`/`.scratch/`). Additionally: identify which context(s) in [CONTEXT-MAP.md](../../../CONTEXT-MAP.md) the diff touches — axes 4 and 5 need this to know which `CONTEXT.md`/ADRs are in scope.

### 3. Spawn all five sub-agents in parallel

**Axis 1 — Standards.** Identical to `code-review`'s Standards sub-agent: repo-documented standards plus the Fowler smell baseline (Mysterious Name, Duplicated Code, Feature Envy, Data Clumps, Primitive Obsession, Repeated Switches, Shotgun Surgery, Divergent Change, Speculative Generality, Message Chains, Middle Man, Refused Bequest). Under 400 words.

**Axis 2 — Spec.** Identical to `code-review`'s Spec sub-agent: missing/partial requirements, scope creep, wrong-looking implementations, each finding quoting the spec line. Under 400 words. Skip with a note if no spec is found.

**Axis 3 — Security & Compliance.** Brief: "Check this diff against RFP §4.3 and the RBAC role table in CONTEXT-MAP.md's Shared vocabulary. Report, per file/hunk: (a) any RBAC check missing or too permissive for the role(s) that touch this code path — quote the role table; (b) any customer/loan data written or transmitted without encryption at rest or in transit; (c) any audit-log entry the ADR-0005 logging contract requires (timestamp, actor, action, affected record) that this diff fails to emit; (d) any MFA bypass on a sensitive action; (e) any session-timeout or inactivity handling this diff should apply but doesn't; (f) anything that looks like it'd fail an ISO 27001/GDPR/Basel/SOX control mentioned in RFP §4.3 or §5. Cite the RFP section for each finding. Under 400 words."

**Axis 4 — Architecture Conformance.** Brief: "Using the `codebase-design` vocabulary (module, interface, depth, seam, adapter — call the Skill tool with codebase-design if you need the definitions), check this diff against CONTEXT-MAP.md and the context's own CONTEXT.md/ADRs (if they exist). Report: (a) any place code from one bounded context reaches directly into another context's data or internals instead of going through its published interface or an event (a bounded-context leak); (b) any shallow module introduced where the diff's own complexity suggests a deep one was warranted; (c) any contradiction with a root ADR (ADR-0001 through ADR-0006) or a context-scoped ADR — quote the ADR. Under 400 words."

**Axis 5 — Integration Contract.** Brief: "Check this diff's Kafka event schemas and REST contracts against ADR-0001 (event-driven integration) and the touched context's spec. Report: (a) any published event whose schema isn't documented anywhere a consuming context can find it; (b) any breaking change to an existing event or REST contract without a corresponding version/migration strategy; (c) any consumer that assumes fields or ordering the producer doesn't actually guarantee. Under 400 words."

### 4. Aggregate

Present all five reports under `## Standards`, `## Spec`, `## Security & Compliance`, `## Architecture Conformance`, `## Integration Contract` headings, verbatim or lightly cleaned. Do **not** merge, rerank, or pick a single worst issue across axes — that's exactly the cross-axis reranking `code-review`'s own "why two axes" section warns against, now with five.

End with a one-line summary per axis (finding count, worst issue within that axis if any) and a single **Gate verdict**: `PASS` only if all five axes report zero hard findings; otherwise `FAIL`, listing which axes failed. A `FAIL` here is what spawns the fix ticket per [docs/agents/pipeline.md](../../../docs/agents/pipeline.md) Phase 7 — don't soften it into a suggestion.

## Why five axes, not two

`code-review`'s own reasoning still holds for axes 1–2: standards-clean code can implement the wrong thing, and spec-correct code can violate conventions. The same separation-of-concerns argument extends to this repo's other real failure modes: code can be standards-clean, spec-correct, *and* still leak customer data, violate a context boundary, or silently break another team's Kafka consumer. Each of those is a distinct way to fail that the others can't see, so each gets its own sub-agent and its own verdict.
