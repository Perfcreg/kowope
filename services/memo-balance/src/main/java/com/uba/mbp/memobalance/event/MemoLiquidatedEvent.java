package com.uba.mbp.memobalance.event;

import java.time.Instant;

/**
 * Ticket 05: published the instant a Memo account's balance reaches exactly
 * zero. Consumed by {@code account-verification} to transition the account
 * from Owing to Paid-Off (ADR-0006), and by {@code clearance-orchestration}
 * to start the ICAD clearance workflow.
 */
public record MemoLiquidatedEvent(String accountNumber, Instant liquidatedAt) {
}
