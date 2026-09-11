# ADR-0002: Containerized microservices on Kubernetes, horizontally scalable

## Status

Accepted

## Context

RFP §4.2 requires the solution to handle large and growing data volumes and user counts across multiple business units and geographies without performance degradation, and to allow future modules to be added with minimal infrastructure change. RFP §4.7 requires 99.9% uptime with robust backup/disaster-recovery. The MBP component diagram proposes the entire platform (Integration/ETL, Event Bus, Shared Services, Core Modules, Monitoring) as one containerized, horizontally scalable ingress running on Kubernetes.

## Decision

Every service in every bounded context is packaged as an independently deployable container and run on Kubernetes, scaled horizontally per service. The API Gateway/WAF (ADR-0004) is the only public ingress into the cluster.

## Consequences

- Each context can scale and deploy independently — a burst in reporting jobs doesn't require scaling `memo-balance`.
- Deployment topology, health checks, and rollout strategy become a shared platform concern; the specific K8s distribution/target (managed cloud vs. on-prem/OpenShift) is still open — see the Architect Kickoff decision log.
- 99.9% uptime and disaster recovery (RFP §4.7) are addressed at the platform level (replica counts, pod disruption budgets, multi-AZ where available), not re-solved per service.

## Source

RFP §4.2, §4.7; MBP component diagram, layer 5 ("MBP Ingress — containerized / Kubernetes, horizontally scalable").
