package com.uba.mbp.integration.icad.clearance;

/** What the poller needs to know about a clearance still awaiting an ICAD outcome. */
public record PendingClearance(String icadReference, String accountNumber, String customerId) {
}
