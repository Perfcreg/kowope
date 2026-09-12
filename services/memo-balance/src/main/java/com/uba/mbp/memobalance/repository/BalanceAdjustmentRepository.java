package com.uba.mbp.memobalance.repository;

import com.uba.mbp.memobalance.domain.BalanceAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BalanceAdjustmentRepository extends JpaRepository<BalanceAdjustment, UUID> {
    List<BalanceAdjustment> findByMemoAccountIdOrderByOccurredAtAsc(UUID memoAccountId);
}
