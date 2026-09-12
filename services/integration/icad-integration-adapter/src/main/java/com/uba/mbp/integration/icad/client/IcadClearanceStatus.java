package com.uba.mbp.integration.icad.client;

/**
 * ADR-0014: the internal vocabulary this adapter's anti-corruption layer maps
 * ICAD's raw (modeled, unverified) status strings onto — this is what flows
 * into {@code IcadClearanceOutcomeEvent} and the REST reply, never ICAD's own
 * wire value directly. {@link #UNKNOWN} exists because the real contract is
 * unverified (ADR-0014): an unrecognized value from ICAD should surface as a
 * visible gap for operations to investigate, not throw and drop the item.
 */
public enum IcadClearanceStatus {
    PENDING,
    CLEARED,
    DISCREPANCY,
    FAILED,
    UNKNOWN;

    public static IcadClearanceStatus fromWireValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public boolean isResolved() {
        return this != PENDING;
    }
}
