package com.uba.mbp.integration.excelimport.route;

import com.uba.mbp.integration.excelimport.validate.AcceptedRow;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Publishes each accepted row's {@code MemoDetectedEvent} through the
 * Camel-EIP route {@code direct:publishMemoDetected} (Message Translator,
 * Kafka Producer, Dead Letter Channel — ADR-0011) and reports which ROW
 * NUMBERS permanently failed after retries.
 *
 * <p>Enterprise-review finding: the HTTP response a Maxim team user sees
 * (User Story 10) was built from validation results alone, before publishing
 * was even attempted — a row whose Kafka publish failed after exhausted
 * retries was still reported "accepted", telling the user their data reached
 * the rest of the system when it hadn't. This class exists so the caller can
 * reconcile the reported outcome against what Kafka actually accepted.
 *
 * <p>Re-verification finding: an earlier version of this correlated by
 * account number, not row number — a workbook with two rows for the same
 * account (e.g. two separate write-off transactions) would have flipped
 * BOTH rows to rejected if only one of them actually failed to publish.
 * Row number is the correct correlation key since it's unique per upload.
 */
@Component
public class MemoDetectedPublisher {

    /** Set by {@code ExcelImportRoute}'s onException handler once redeliveries are exhausted. */
    public static final String PUBLISH_FAILED_HEADER = "excelImportPublishFailed";

    private final ProducerTemplate producerTemplate;

    public MemoDetectedPublisher(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    public Set<Integer> publishAndReturnFailedRowNumbers(List<AcceptedRow> acceptedRows) {
        Set<Integer> failedRowNumbers = new LinkedHashSet<>();
        for (AcceptedRow acceptedRow : acceptedRows) {
            Exchange result = producerTemplate.send(
                    "direct:publishMemoDetected", exchange -> exchange.getIn().setBody(acceptedRow.event()));
            // The header is the primary signal (set deliberately by the
            // route's own onException handler); the exception check is a
            // defensive fallback in case that handler's own alert/audit call
            // throws before it gets to set the header — a failure must never
            // silently read as success just because the header wasn't reached.
            boolean failed = Boolean.TRUE.equals(result.getIn().getHeader(PUBLISH_FAILED_HEADER, Boolean.class))
                    || result.getException() != null;
            if (failed) {
                failedRowNumbers.add(acceptedRow.rowNumber());
            }
        }
        return failedRowNumbers;
    }
}
