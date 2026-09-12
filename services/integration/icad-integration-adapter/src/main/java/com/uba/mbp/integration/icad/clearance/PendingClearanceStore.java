package com.uba.mbp.integration.icad.clearance;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory retry/dedup state, per the integration spec's Implementation
 * Decisions: "No context-specific persistence beyond each adapter's own
 * retry/dedup state; the Memo record itself belongs to memo-balance." A
 * pushed-but-unresolved clearance lives here until the poller confirms it —
 * lost on restart, which just means the next poll cycle re-derives nothing
 * lost (ICAD itself remains the source of truth for `icadReference`, this is
 * only the adapter's own worklist of references to check).
 */
@Component
public class PendingClearanceStore {

    private final ConcurrentHashMap<String, PendingClearance> pending = new ConcurrentHashMap<>();

    public void add(PendingClearance clearance) {
        pending.put(clearance.icadReference(), clearance);
    }

    public Collection<PendingClearance> all() {
        return pending.values();
    }

    public void remove(String icadReference) {
        pending.remove(icadReference);
    }
}
