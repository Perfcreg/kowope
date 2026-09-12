package com.uba.mbp.memobalance.event;

import com.uba.mbp.memobalance.domain.AdjustmentType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ticket 04: published on every balance change, consumed by {@code reporting}
 * to compute period movement without recomputing it from raw transactions.
 */
public record MemoBalanceAdjustedEvent(
        String accountNumber,
        AdjustmentType adjustmentType,
        BigDecimal previousBalance,
        BigDecimal newBalance,
        Instant occurredAt) {
}
