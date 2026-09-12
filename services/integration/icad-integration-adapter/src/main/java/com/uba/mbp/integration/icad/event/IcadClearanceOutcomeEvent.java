package com.uba.mbp.integration.icad.event;

import com.uba.mbp.integration.icad.client.IcadClearanceStatus;

import java.time.Instant;

/**
 * Published once a pushed clearance request resolves on ICAD's side
 * (integration spec User Story 7). {@code status} is one of CLEARED,
 * DISCREPANCY, FAILED, or UNKNOWN (ADR-0014: an unrecognized value from
 * ICAD's unverified contract, surfaced rather than dropped); {@code detail}
 * carries ICAD's raw response text or discrepancy reason (User Story 14) —
 * clearance-orchestration escalates using this, not a summarized/lossy
 * version of it. {@code status} is this adapter's own mapped vocabulary
 * (IcadClearanceStatus), never ICAD's raw wire value — ADR-0014's
 * anti-corruption boundary lives in IcadClient, not here.
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
