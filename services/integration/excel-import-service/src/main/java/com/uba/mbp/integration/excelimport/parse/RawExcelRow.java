package com.uba.mbp.integration.excelimport.parse;

/**
 * One data row read straight off the sheet, as text — no validation or type
 * conversion yet. A blank cell comes through as {@code null}. Column mapping
 * is header-name-based (see {@link ExcelWorkbookParser}), not positional, so
 * the Maxim team's column order doesn't have to match this service's assumptions.
 */
public record RawExcelRow(
        int rowNumber,
        String accountNumber,
        String customerId,
        String branchSol,
        String currency,
        String postingReference,
        String narration,
        String balance,
        String transferDate,
        String country) {
}
