package com.uba.mbp.integration.icad.client;

/** {@code status} is one of PENDING, CLEARED, DISCREPANCY, FAILED; {@code detail} is null while PENDING. */
public record IcadFetchResult(String reference, String status, String detail) {

    public boolean isResolved() {
        return !"PENDING".equals(status);
    }
}
