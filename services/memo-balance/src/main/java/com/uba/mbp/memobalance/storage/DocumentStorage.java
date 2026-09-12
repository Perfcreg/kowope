package com.uba.mbp.memobalance.storage;

/**
 * The Blob storage seam (ADR-0003) documents are actually stored behind.
 * {@link LocalFilesystemDocumentStorage} stands in for a real cloud Blob
 * store until this platform has one provisioned — nothing above this
 * interface (MemoDocumentService, the controller) needs to change when it does.
 */
public interface DocumentStorage {

    /** Stores the bytes and returns an opaque key that {@link #retrieve} can use to fetch them again. */
    String store(byte[] content, String fileName);

    byte[] retrieve(String storageKey);
}
