package com.uba.mbp.memobalance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for a document attached to a Memo account (RFP §3.11 — non-indebtedness
 * letters, verification records, memo file updates). The file itself lives in Blob
 * storage (ADR-0003); only the pointer (storageKey) is kept here.
 */
@Entity
@Table(name = "memo_document")
public class MemoDocument {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "memo_account_id", nullable = false)
    private UUID memoAccountId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected MemoDocument() {
        // JPA
    }

    public static MemoDocument uploaded(
            UUID memoAccountId, String fileName, String contentType, String storageKey,
            String uploadedBy, Instant uploadedAt) {
        MemoDocument document = new MemoDocument();
        document.memoAccountId = memoAccountId;
        document.fileName = fileName;
        document.contentType = contentType;
        document.storageKey = storageKey;
        document.uploadedBy = uploadedBy;
        document.uploadedAt = uploadedAt;
        return document;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMemoAccountId() {
        return memoAccountId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
