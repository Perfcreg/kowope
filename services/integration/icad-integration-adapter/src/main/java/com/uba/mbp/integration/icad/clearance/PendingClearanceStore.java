package com.uba.mbp.integration.icad.clearance;

import java.util.Collection;

/**
 * The adapter's own retry/dedup worklist of clearances pushed to ICAD but not
 * yet resolved, per the integration spec's Implementation Decisions: "No
 * context-specific persistence beyond each adapter's own retry/dedup state."
 * A real interface (ADR-0015), not just the in-memory implementation's shape,
 * so a durable-storage implementation can be swapped in later without
 * touching {@code IcadClearanceProcessor}.
 *
 * <p><b>{@link InMemoryPendingClearanceStore} loses this state on restart</b> —
 * see ADR-0015 for why that's a currently-accepted risk, not a solved problem.
 */
public interface PendingClearanceStore {

    void add(PendingClearance clearance);

    Collection<PendingClearance> all();

    void remove(String icadReference);
}
