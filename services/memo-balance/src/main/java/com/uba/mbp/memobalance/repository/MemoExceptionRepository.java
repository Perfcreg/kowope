package com.uba.mbp.memobalance.repository;

import com.uba.mbp.memobalance.domain.MemoException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemoExceptionRepository extends JpaRepository<MemoException, UUID> {
    List<MemoException> findByMemoAccountId(UUID memoAccountId);
}
