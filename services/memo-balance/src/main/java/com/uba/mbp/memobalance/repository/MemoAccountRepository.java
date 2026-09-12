package com.uba.mbp.memobalance.repository;

import com.uba.mbp.memobalance.domain.MemoAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemoAccountRepository extends JpaRepository<MemoAccount, UUID> {
    Optional<MemoAccount> findByAccountNumber(String accountNumber);
}
