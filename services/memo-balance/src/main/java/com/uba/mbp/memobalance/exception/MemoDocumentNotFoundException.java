package com.uba.mbp.memobalance.exception;

import java.util.UUID;

public class MemoDocumentNotFoundException extends RuntimeException {
    public MemoDocumentNotFoundException(UUID documentId) {
        super("No document found with id: " + documentId);
    }
}
