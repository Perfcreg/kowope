package com.uba.mbp.integration.excelimport.validate;

/**
 * Per-row upload result surfaced back to the Maxim team user (integration spec
 * User Story 10). {@code reason} is null for an accepted row.
 */
public record RowOutcome(int rowNumber, String accountNumber, boolean accepted, String reason) {
}
