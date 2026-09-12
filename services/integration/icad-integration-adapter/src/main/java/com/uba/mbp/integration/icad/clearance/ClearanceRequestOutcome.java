package com.uba.mbp.integration.icad.clearance;

/**
 * The synchronous reply to {@code POST /icad/clearance-requests}. Real ICAD
 * clearance takes 24-48 hours (RFP §3.8) — this can only ever be PENDING; the
 * actual outcome arrives later as an {@code IcadClearanceOutcomeEvent}.
 */
public record ClearanceRequestOutcome(String accountNumber, String icadReference, String status) {
}
