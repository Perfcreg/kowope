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

/**
 * A Memo account: the system of record for a customer account that has been
 * written off to memo (RFP §3.13(bis)). See docs/specs' memo-balance spec.
 */
@Entity
@Table(name = "memo_account")
public class MemoAccount {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "branch_sol")
    private String branchSol;

    @Column(nullable = false)
    private String currency;

    @Column(name = "posting_reference")
    private String postingReference;

    @Column(nullable = false, columnDefinition = "text")
    private String narration;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "transfer_date", nullable = false)
    private Instant transferDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemoAccount() {
        // JPA
    }

    public static MemoAccount newlyDetected(
            String accountNumber,
            String customerId,
            String branchSol,
            String currency,
            String postingReference,
            String narration,
            BigDecimal balance,
            Instant transferDate,
            Instant now) {
        MemoAccount account = new MemoAccount();
        account.accountNumber = accountNumber;
        account.customerId = customerId;
        account.branchSol = branchSol;
        account.currency = currency;
        account.postingReference = postingReference;
        account.narration = narration;
        account.balance = balance;
        account.transferDate = transferDate;
        account.status = MemoStatus.IMPORTED_PENDING_REVIEW;
        account.createdAt = now;
        account.updatedAt = now;
        return account;
    }

    /**
     * Merges a repeat detection into this existing record (RFP §3.13(bis) de-duplication):
     * updates balance/narration/reference, but the earliest transfer date always wins.
     */
    public void mergeRepeatDetection(
            String postingReference,
            String narration,
            BigDecimal balance,
            Instant transferDate,
            Instant now) {
        this.postingReference = postingReference;
        this.narration = narration;
        this.balance = balance;
        if (transferDate.isBefore(this.transferDate)) {
            this.transferDate = transferDate;
        }
        this.updatedAt = now;
    }

    /** Ticket 04/05: applies a recalculated balance, moving to LIQUIDATED if it reaches zero. */
    public void applyAdjustedBalance(BigDecimal newBalance, Instant now) {
        this.balance = newBalance;
        this.updatedAt = now;
        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            this.status = MemoStatus.LIQUIDATED;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getBranchSol() {
        return branchSol;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPostingReference() {
        return postingReference;
    }

    public String getNarration() {
        return narration;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getTransferDate() {
        return transferDate;
    }

    public MemoStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
