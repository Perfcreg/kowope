package com.uba.mbp.memobalance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Ticket 04: one balance change on a Memo account — the persisted half of {@code MemoBalanceAdjusted}. */
@Entity
@Table(name = "balance_adjustment")
public class BalanceAdjustment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "memo_account_id", nullable = false)
    private UUID memoAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false)
    private AdjustmentType adjustmentType;

    @Column(name = "previous_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal previousBalance;

    @Column(name = "new_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal newBalance;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected BalanceAdjustment() {
        // JPA
    }

    public static BalanceAdjustment record(
            UUID memoAccountId,
            AdjustmentType adjustmentType,
            BigDecimal previousBalance,
            BigDecimal newBalance,
            Instant occurredAt) {
        BalanceAdjustment adjustment = new BalanceAdjustment();
        adjustment.memoAccountId = memoAccountId;
        adjustment.adjustmentType = adjustmentType;
        adjustment.previousBalance = previousBalance;
        adjustment.newBalance = newBalance;
        adjustment.occurredAt = occurredAt;
        return adjustment;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemoAccountId() {
        return memoAccountId;
    }

    public AdjustmentType getAdjustmentType() {
        return adjustmentType;
    }

    public BigDecimal getPreviousBalance() {
        return previousBalance;
    }

    public BigDecimal getNewBalance() {
        return newBalance;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
