package com.uba.mbp.integration.excelimport.validate;

import com.uba.mbp.integration.excelimport.event.MemoDetectedEvent;

/** {@code event} is null when {@code outcome.accepted()} is false. */
public record ValidatedRow(RowOutcome outcome, MemoDetectedEvent event) {
}
