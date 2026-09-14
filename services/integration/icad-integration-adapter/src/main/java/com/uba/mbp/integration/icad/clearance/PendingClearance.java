package com.uba.mbp.integration.icad.clearance;

import java.time.Instant;

/**
 * What the poller needs to know about a clearance still awaiting an ICAD
 * outcome. {@code pushedAt} is when this clearance was first pushed to ICAD —
 * the escalation check (ADR-0020) measures age from this, not from any
 * individual poll attempt.
 */
public record PendingClearance(String icadReference, String accountNumber, String customerId, Instant pushedAt) {
}
