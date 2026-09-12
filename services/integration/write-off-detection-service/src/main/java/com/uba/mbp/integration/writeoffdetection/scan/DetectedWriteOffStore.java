package com.uba.mbp.integration.writeoffdetection.scan;

/**
 * The adapter's own dedup state (integration spec Implementation Decisions:
 * "each adapter's own retry/dedup state") — without this, the timer's
 * from-scratch rescan would re-publish and re-audit every already-detected
 * write-off on every cycle forever, which is a repeating batch, not the
 * continuous *new*-movement feed User Story 3 asks for.
 *
 * <p>An interface, not just {@link InMemoryDetectedWriteOffStore}'s shape, so
 * a durable implementation can be swapped in later without touching
 * {@link WriteOffScanner} — same seam pattern as the ICAD adapter's
 * {@code PendingClearanceStore} (ADR-0015). {@link InMemoryDetectedWriteOffStore}
 * loses its state on restart, which means a restart can cause one round of
 * re-detection for whatever was already published before the restart — an
 * accepted, flagged limitation, not a silent one.
 */
public interface DetectedWriteOffStore {

    boolean alreadyDetected(long savingsAccountId, long transactionId);

    void markDetected(long savingsAccountId, long transactionId);
}
