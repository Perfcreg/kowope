package com.uba.mbp.integration.excelimport.validate;

import java.util.List;

/**
 * What {@code ExcelImportProcessor} hands to the Camel route: the accepted
 * rows still to publish (each tagged with its row number for correlation —
 * see {@link AcceptedRow}), and the outcome to hand back to the caller once
 * they're sent to Kafka.
 */
public record ImportResult(List<AcceptedRow> acceptedRows, ImportOutcome outcome) {
}
