# ADR-0004: API Gateway + WAF as the single ingress/perimeter

## Status

Accepted

## Context

RFP §4.3 requires MFA, RBAC for all users, end-to-end encryption, comprehensive audit logging, and enforced session timeouts. RFP §4.10 requires access from both web and mobile channels. The MBP component diagram routes all three channels (Admin Dashboard, Branch/Back Office, UBA Customer Channel) through a single API Gateway / Load Balancer / WAF before anything reaches the cluster.

## Decision

All external traffic, from every channel, terminates at a single API Gateway with an integrated WAF and load balancer. No channel or downstream service is reachable directly from outside the perimeter. Authentication (MFA), coarse-grained rate limiting/WAF rules, and TLS termination live at this layer; fine-grained RBAC per RFP §3.7's role table is enforced downstream by `shared-platform`'s authentication-service and by each context's own authorization checks.

## Consequences

- One place to reason about the external attack surface, satisfying the "single ingress" half of RFP §4.3's security requirements.
- The gateway becomes a hard dependency for every channel — its availability targets need to be at least as strict as the platform's overall 99.9% (RFP §4.7).
- Fine-grained RBAC is still each context's own responsibility; the gateway does not replace per-context authorization checks (the Security & Compliance review gate in [docs/agents/pipeline.md](../agents/pipeline.md) verifies both layers).

## Source

RFP §4.3, §4.10; MBP component diagram, layer 2.
