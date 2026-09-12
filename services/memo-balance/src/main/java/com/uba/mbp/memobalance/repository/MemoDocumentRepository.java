package com.uba.mbp.memobalance.repository;

import com.uba.mbp.memobalance.domain.MemoDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemoDocumentRepository extends JpaRepository<MemoDocument, UUID> {
    Optional<MemoDocument> findByIdAndMemoAccountId(UUID id, UUID memoAccountId);

    List<MemoDocument> findByMemoAccountIdOrderByUploadedAtDesc(UUID memoAccountId);
}
