package com.uba.mbp.audit;

import java.time.Instant;

/**
 * The system-wide audit log contract (RFP §3.5, ADR-0005). Every service
 * emits these instead of inventing its own log shape, so ELK can index
 * audit activity consistently across bounded contexts.
 */
public record AuditEvent(
        Instant timestamp,
        String actor,
        String action,
        String affectedRecordType,
        String affectedRecordId,
        String sourceService,
        String detail) {
}
