package com.uba.mbp.memobalance.service;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.memobalance.domain.MemoDocument;
import com.uba.mbp.memobalance.exception.MemoAccountNotFoundException;
import com.uba.mbp.memobalance.exception.MemoDocumentNotFoundException;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import com.uba.mbp.memobalance.repository.MemoDocumentRepository;
import com.uba.mbp.memobalance.storage.DocumentStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Ticket 07: upload and retrieve documents attached to a Memo account (RFP §3.11). */
@Service
public class MemoDocumentService {

    private final MemoAccountRepository memoAccountRepository;
    private final MemoDocumentRepository memoDocumentRepository;
    private final DocumentStorage documentStorage;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public MemoDocumentService(
            MemoAccountRepository memoAccountRepository,
            MemoDocumentRepository memoDocumentRepository,
            DocumentStorage documentStorage,
            AuditLogger auditLogger,
            Clock clock) {
        this.memoAccountRepository = memoAccountRepository;
        this.memoDocumentRepository = memoDocumentRepository;
        this.documentStorage = documentStorage;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional
    public UUID upload(String accountNumber, String fileName, String contentType, byte[] content, String uploadedBy) {
        var account = memoAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new MemoAccountNotFoundException(accountNumber));

        String storageKey = documentStorage.store(content, fileName);
        var now = clock.instant();
        var document = memoDocumentRepository.save(
                MemoDocument.uploaded(account.getId(), fileName, contentType, storageKey, uploadedBy, now));

        auditLogger.record(new AuditEvent(
                now, uploadedBy, "MEMO_DOCUMENT_UPLOADED", "MemoAccount", accountNumber,
                "memo-balance", "Uploaded " + fileName));

        return document.getId();
    }

    public record DownloadedDocument(String fileName, String contentType, byte[] content) {
    }

    public DownloadedDocument retrieve(String accountNumber, UUID documentId) {
        var account = memoAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new MemoAccountNotFoundException(accountNumber));

        var document = memoDocumentRepository.findByIdAndMemoAccountId(documentId, account.getId())
                .orElseThrow(() -> new MemoDocumentNotFoundException(documentId));

        byte[] content = documentStorage.retrieve(document.getStorageKey());
        return new DownloadedDocument(document.getFileName(), document.getContentType(), content);
    }
}
