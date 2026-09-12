package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.domain.MemoStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ticket 03: the RBAC-scoped read view of a Memo account. {@code history} is
 * empty until Tickets 04/05 introduce balance adjustments/liquidation to populate it.
 */
public record MemoAccountResponse(
        String accountNumber,
        String customerId,
        String branchSol,
        String currency,
        MemoStatus status,
        BigDecimal balance,
        String narration,
        Instant transferDate,
        Instant createdAt,
        Instant updatedAt,
        List<MemoHistoryEntry> history) {

    public static MemoAccountResponse from(MemoAccount account, List<MemoHistoryEntry> history) {
        return new MemoAccountResponse(
                account.getAccountNumber(),
                account.getCustomerId(),
                account.getBranchSol(),
                account.getCurrency(),
                account.getStatus(),
                account.getBalance(),
                account.getNarration(),
                account.getTransferDate(),
                account.getCreatedAt(),
                account.getUpdatedAt(),
                history);
    }
}
