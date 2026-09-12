package com.uba.mbp.memobalance.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Inbound event from {@code integration}'s Vision ETL Connector (see Spec:
 * integration) — memo-balance defines this contract's consumer-side shape
 * since that connector doesn't exist yet.
 */
public record VisionBalanceSyncedEvent(String accountNumber, BigDecimal balance, Instant syncedAt) {
}
