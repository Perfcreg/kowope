package com.uba.mbp.integration.icad.client;

/** {@code detail} is null while PENDING. */
public record IcadFetchResult(String reference, IcadClearanceStatus status, String detail) {

    public boolean isResolved() {
        return status.isResolved();
    }
}
