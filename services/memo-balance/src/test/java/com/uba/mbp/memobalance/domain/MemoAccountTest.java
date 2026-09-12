package com.uba.mbp.memobalance.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 04/05/06 domain rule, tested at its own seam — no Spring, no Kafka, no database.
 * Extracted per the Architecture Conformance review finding that this logic was
 * fused into the service layer and duplicated by a caller re-deriving liquidation.
 */
class MemoAccountTest {

    private MemoAccount accountWithBalance(String balance) {
        return MemoAccount.newlyDetected(
                "ACC-1", "CUST-1", "SOL-001", "NGN", "TXN-REF-1",
                "written off", new BigDecimal(balance), Instant.parse("2026-01-01T00:00:00Z"), Instant.now());
    }

    @Test
    void aPartialAdjustmentReducesTheBalanceWithNoUnallocatedAmount() {
        MemoAccount account = accountWithBalance("500.00");

        var result = account.adjust(new BigDecimal("200.00"), Instant.now());

        assertEquals(0, new BigDecimal("300.00").compareTo(result.newBalance()));
        assertFalse(result.hasUnallocatedAmount());
        assertNull(result.unallocatedAmount());
        assertEquals(MemoStatus.IMPORTED_PENDING_REVIEW, account.getStatus());
    }

    @Test
    void anExactAdjustmentZeroesTheBalanceAndLiquidatesWithNoUnallocatedAmount() {
        MemoAccount account = accountWithBalance("500.00");

        var result = account.adjust(new BigDecimal("500.00"), Instant.now());

        assertEquals(0, BigDecimal.ZERO.compareTo(result.newBalance()));
        assertFalse(result.hasUnallocatedAmount());
        assertEquals(MemoStatus.LIQUIDATED, account.getStatus());
    }

    @Test
    void anExcessiveAdjustmentCapsAtZeroLiquidatesAndReturnsTheUnallocatedExcess() {
        MemoAccount account = accountWithBalance("100.00");

        var result = account.adjust(new BigDecimal("150.00"), Instant.now());

        assertEquals(0, BigDecimal.ZERO.compareTo(result.newBalance()));
        assertTrue(result.hasUnallocatedAmount());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.unallocatedAmount()));
        assertEquals(MemoStatus.LIQUIDATED, account.getStatus());
    }
}
