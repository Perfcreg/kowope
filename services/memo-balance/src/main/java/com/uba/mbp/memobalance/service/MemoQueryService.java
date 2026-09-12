package com.uba.mbp.memobalance.service;

import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import com.uba.mbp.memobalance.web.MemoAccountResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Ticket 03: read-only queries behind the RBAC-scoped REST API. */
@Service
public class MemoQueryService {

    private final MemoAccountRepository repository;

    public MemoQueryService(MemoAccountRepository repository) {
        this.repository = repository;
    }

    public Optional<MemoAccountResponse> findByAccountNumber(String accountNumber) {
        return repository.findByAccountNumber(accountNumber)
                .map(this::toResponse);
    }

    private MemoAccountResponse toResponse(MemoAccount account) {
        // History is empty until Tickets 04/05 add a persisted adjustment trail.
        return MemoAccountResponse.from(account, List.of());
    }
}
