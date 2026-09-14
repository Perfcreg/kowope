package com.uba.mbp.sharedplatform.notification.event;

/**
 * This service's own copy of {@code icad-integration-adapter}'s mapped
 * clearance-status vocabulary — all 5 values, including {@code PENDING}
 * (system-wide-audit fix, 2026-09-15: the producer's real enum has 5 values;
 * this copy previously had only 4, missing {@code PENDING}. Currently
 * harmless — {@code icad-integration-adapter} only ever publishes a
 * resolved outcome, never {@code PENDING} — but an incomplete mirror of the
 * authoritative type is a latent Kafka-deserialization crash risk if that
 * producer-side gating logic ever changes).
 */
public enum IcadClearanceStatus {
    PENDING,
    CLEARED,
    DISCREPANCY,
    FAILED,
    UNKNOWN
}
