package com.uba.mbp.integration.visionetl.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The producer side of the contract memo-balance already consumes — field
 * names and types must match services/memo-balance/CONTEXT.md's "Consumed
 * events" section exactly.
 */
public record VisionBalanceSyncedEvent(String accountNumber, BigDecimal balance, Instant syncedAt) {
}
