package com.uba.mbp.integration.icad.clearance;

import com.uba.mbp.integration.icad.client.IcadClearanceStatus;

/**
 * The synchronous reply to {@code POST /icad/clearance-requests}. Real ICAD
 * clearance takes 24-48 hours (RFP §3.8) — {@code status} can only ever be
 * PENDING here; the actual outcome arrives later as an
 * {@code IcadClearanceOutcomeEvent}. Named distinctly from that event
 * (previously both were called "...Outcome") since this is an acknowledgement
 * of receipt, not a clearance result.
 */
public record ClearanceRequestAccepted(String accountNumber, String icadReference, IcadClearanceStatus status) {
}
