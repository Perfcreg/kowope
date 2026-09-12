package com.uba.mbp.integration.icad.clearance;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ADR-0015: an in-memory implementation is a known, accepted-for-now
 * limitation, not a hidden one — a pod restart between a push and its
 * resolution silently drops that clearance, with no reconciliation against
 * ICAD to recover it. Nothing here "re-derives" a lost entry; ADR-0015
 * documents this honestly and names the seam (this interface) a durable
 * (e.g. Redis, per ADR-0003) implementation would fill.
 */
@Component
public class InMemoryPendingClearanceStore implements PendingClearanceStore {

    private final ConcurrentHashMap<String, PendingClearance> pending = new ConcurrentHashMap<>();

    @Override
    public void add(PendingClearance clearance) {
        pending.put(clearance.icadReference(), clearance);
    }

    @Override
    public Collection<PendingClearance> all() {
        return pending.values();
    }

    @Override
    public void remove(String icadReference) {
        pending.remove(icadReference);
    }
}
