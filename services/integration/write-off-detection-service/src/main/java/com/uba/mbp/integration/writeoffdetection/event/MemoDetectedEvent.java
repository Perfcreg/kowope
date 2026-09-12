package com.uba.mbp.integration.writeoffdetection.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The producer side of the contract memo-balance already consumes — field names
 * and types must match services/memo-balance/CONTEXT.md's "Consumed events"
 * section exactly. Not a shared library type on purpose: each side owns its
 * copy of the contract, per ADR-0001's event-driven (not shared-code) coupling.
 */
public record MemoDetectedEvent(
        String accountNumber,
        String customerId,
        String branchSol,
        String currency,
        String postingReference,
        String narration,
        BigDecimal balance,
        Instant transferDate,
        String source,
        String country) {
}
