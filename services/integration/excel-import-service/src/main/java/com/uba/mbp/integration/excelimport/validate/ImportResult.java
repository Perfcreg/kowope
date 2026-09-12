package com.uba.mbp.integration.excelimport.validate;

import com.uba.mbp.integration.excelimport.event.MemoDetectedEvent;

import java.util.List;

/**
 * What {@code ExcelImportProcessor} hands to the Camel route: the events still
 * to publish, and the outcome to hand back to the caller once they're split
 * off and sent to Kafka.
 */
public record ImportResult(List<MemoDetectedEvent> acceptedEvents, ImportOutcome outcome) {
}
