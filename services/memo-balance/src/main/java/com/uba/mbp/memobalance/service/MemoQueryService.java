package com.uba.mbp.memobalance.service;

import com.uba.mbp.memobalance.domain.BalanceAdjustment;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.repository.BalanceAdjustmentRepository;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import com.uba.mbp.memobalance.web.MemoHistoryEntry;
import com.uba.mbp.memobalance.web.MemoAccountResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Ticket 03: read-only queries behind the RBAC-scoped REST API. */
@Service
public class MemoQueryService {

    private final MemoAccountRepository repository;
    private final BalanceAdjustmentRepository balanceAdjustmentRepository;

    public MemoQueryService(MemoAccountRepository repository, BalanceAdjustmentRepository balanceAdjustmentRepository) {
        this.repository = repository;
        this.balanceAdjustmentRepository = balanceAdjustmentRepository;
    }

    public Optional<MemoAccountResponse> findByAccountNumber(String accountNumber) {
        return repository.findByAccountNumber(accountNumber)
                .map(this::toResponse);
    }

    private MemoAccountResponse toResponse(MemoAccount account) {
        // Ticket 04: real adjustment history now backs this; empty for a freshly
        // detected account with no adjustments yet.
        List<MemoHistoryEntry> history = balanceAdjustmentRepository
                .findByMemoAccountIdOrderByOccurredAtAsc(account.getId())
                .stream()
                .map(this::toHistoryEntry)
                .toList();
        return MemoAccountResponse.from(account, history);
    }

    private MemoHistoryEntry toHistoryEntry(BalanceAdjustment adjustment) {
        return new MemoHistoryEntry(
                adjustment.getAdjustmentType().name(),
                adjustment.getPreviousBalance(),
                adjustment.getNewBalance(),
                adjustment.getOccurredAt());
    }
}
