package com.uba.mbp.integration.excelimport.route;

import com.uba.mbp.integration.excelimport.event.MemoDetectedEvent;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Publishes each accepted {@link MemoDetectedEvent} through the Camel-EIP
 * route {@code direct:publishMemoDetected} (Message Translator, Kafka
 * Producer, Dead Letter Channel — ADR-0011) and reports which ones
 * permanently failed after retries.
 *
 * <p>Enterprise-review finding: the HTTP response a Maxim team user sees
 * (User Story 10) was built from validation results alone, before publishing
 * was even attempted — a row whose Kafka publish failed after exhausted
 * retries was still reported "accepted", telling the user their data reached
 * the rest of the system when it hadn't. This class exists so the caller can
 * reconcile the reported outcome against what Kafka actually accepted.
 */
@Component
public class MemoDetectedPublisher {

    /** Set by {@code ExcelImportRoute}'s onException handler once redeliveries are exhausted. */
    public static final String PUBLISH_FAILED_HEADER = "excelImportPublishFailed";

    private final ProducerTemplate producerTemplate;

    public MemoDetectedPublisher(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    public Set<String> publishAndReturnFailedAccountNumbers(List<MemoDetectedEvent> events) {
        Set<String> failedAccountNumbers = new LinkedHashSet<>();
        for (MemoDetectedEvent event : events) {
            Exchange result = producerTemplate.send(
                    "direct:publishMemoDetected", exchange -> exchange.getIn().setBody(event));
            if (Boolean.TRUE.equals(result.getIn().getHeader(PUBLISH_FAILED_HEADER, Boolean.class))) {
                failedAccountNumbers.add(event.accountNumber());
            }
        }
        return failedAccountNumbers;
    }
}
