package com.uba.mbp.sharedplatform.notification.event;

import java.time.Instant;

/**
 * This service's own copy of {@code icad-integration-adapter}'s published
 * event — see services/integration/CONTEXT.md#published-events for the
 * authoritative shape. Only {@code status} and {@code accountNumber} are
 * used here (a {@code DISCREPANCY}/{@code FAILED} outcome notifies
 * CREDIT_ADMIN, per spec User Story 10); the rest of the fields are carried
 * for a faithful copy of the wire shape, not because they're all consumed.
 */
public record IcadClearanceOutcomeEvent(
        String accountNumber,
        String customerId,
        String icadReference,
        IcadClearanceStatus status,
        String detail,
        Instant resolvedAt,
        String source) {
}
