package com.uba.mbp.memobalance.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Dev/test stand-in for real Blob storage (ADR-0003) — writes to a local
 * directory instead of a cloud object store. Swap this implementation, not
 * its callers, once this platform provisions real Blob storage.
 */
@Component
public class LocalFilesystemDocumentStorage implements DocumentStorage {

    private final Path baseDir;

    public LocalFilesystemDocumentStorage(@Value("${memo-balance.document-storage.base-dir}") String baseDir) {
        this.baseDir = Path.of(baseDir);
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String store(byte[] content, String fileName) {
        String storageKey = UUID.randomUUID() + "-" + sanitize(fileName);
        try {
            Files.write(resolveWithinBaseDir(storageKey), content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return storageKey;
    }

    @Override
    public byte[] retrieve(String storageKey) {
        try {
            return Files.readAllBytes(resolveWithinBaseDir(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Strips any path separator/parent-directory characters — the key is a filename, never a path. */
    private static String sanitize(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** Defends against a storageKey that's somehow escaped baseDir (e.g. via ".."). */
    private Path resolveWithinBaseDir(String storageKey) {
        Path resolved = baseDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("Invalid storage key: " + storageKey);
        }
        return resolved;
    }
}
