package com.uba.mbp.integration.icad.clearance;

import java.time.Instant;

/**
 * What `clearance-orchestration` hands this adapter to start clearing a
 * customer's name from ICAD after liquidation (RFP §3.8, integration spec
 * User Story 6). {@code bvn} (Bank Verification Number) is how NIBSS's real
 * ICAD indexes a customer across banks — optional here since not every
 * account in the seeded/local data carries one.
 */
public record ClearanceRequest(
        String accountNumber,
        String customerId,
        String customerName,
        String bvn,
        Instant clearedDate) {
}
