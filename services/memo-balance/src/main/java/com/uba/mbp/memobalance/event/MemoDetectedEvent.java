package com.uba.mbp.memobalance.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Inbound event from the {@code integration} context (Write-Off Detection Service,
 * Vision ETL Connector, or Excel Import Service) — see Spec: integration.
 * Consumed from the {@code mbp.integration.memo-detected} topic (ADR-0001).
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
        String source) {
}
