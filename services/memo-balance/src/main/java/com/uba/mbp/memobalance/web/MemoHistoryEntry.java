package com.uba.mbp.memobalance.web;

import java.math.BigDecimal;
import java.time.Instant;

/** One entry in a Memo account's adjustment/liquidation history (Tickets 04/05). */
public record MemoHistoryEntry(
        String type,
        BigDecimal previousBalance,
        BigDecimal newBalance,
        Instant occurredAt) {
}
