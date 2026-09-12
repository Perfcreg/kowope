package com.uba.mbp.integration.icad.event;

import java.time.Instant;

/**
 * Published once a pushed clearance request resolves on ICAD's side
 * (integration spec User Story 7). {@code status} is one of CLEARED,
 * DISCREPANCY, or FAILED; {@code detail} carries ICAD's raw response text or
 * discrepancy reason (User Story 14) — clearance-orchestration escalates
 * using this, not a summarized/lossy version of it.
 */
public record IcadClearanceOutcomeEvent(
        String accountNumber,
        String customerId,
        String icadReference,
        String status,
        String detail,
        Instant resolvedAt,
        String source) {
}
