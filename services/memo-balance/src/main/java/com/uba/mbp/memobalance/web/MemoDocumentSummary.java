package com.uba.mbp.memobalance.web;

import com.uba.mbp.memobalance.domain.MemoDocument;

import java.time.Instant;
import java.util.UUID;

/** A row in the document-list endpoint — enough to identify and request a download. */
public record MemoDocumentSummary(
        UUID documentId, String fileName, String contentType, String uploadedBy, Instant uploadedAt) {

    public static MemoDocumentSummary from(MemoDocument document) {
        return new MemoDocumentSummary(
                document.getId(), document.getFileName(), document.getContentType(),
                document.getUploadedBy(), document.getUploadedAt());
    }
}
