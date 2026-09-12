package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.domain.MemoStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Ticket 03: the RBAC-scoped read view of a Memo account. {@code history} is
 * populated once Tickets 04/05 record balance adjustments/liquidation.
 * {@code country}/{@code baseCurrency}/{@code glWriteOffCode}/{@code glRecoveryCode}
 * (Ticket 08) are null until a CountryConfigLookup has resolved them.
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
        String country,
        String baseCurrency,
        String glWriteOffCode,
        String glRecoveryCode,
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
                account.getCountry(),
                account.getBaseCurrency(),
                account.getGlWriteOffCode(),
                account.getGlRecoveryCode(),
                history);
    }
}
