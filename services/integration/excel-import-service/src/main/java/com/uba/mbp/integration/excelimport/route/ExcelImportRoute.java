package com.uba.mbp.integration.excelimport.route;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.excelimport.ExcelImportProcessor;
import com.uba.mbp.integration.excelimport.event.MemoDetectedEvent;
import com.uba.mbp.integration.excelimport.notification.NotificationClient;
import com.uba.mbp.integration.excelimport.validate.ImportOutcome;
import com.uba.mbp.integration.excelimport.validate.ImportResult;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.Clock;
import java.util.Set;

/**
 * ADR-0011: the EIP wiring. Business logic (parsing, validation, audit) lives
 * in {@link ExcelImportProcessor}; the per-event Kafka publish (Message
 * Translator, Kafka Producer, Dead Letter Channel) is a Camel route called
 * once per accepted event by {@link MemoDetectedPublisher} — a plain Java
 * loop rather than a Camel Splitter, because the HTTP response (User Story
 * 10) needs to know, per row, whether ITS publish actually succeeded, and
 * correlating that back out of a Splitter's aggregation is far more fragile
 * than a synchronous loop over {@link MemoDetectedPublisher#producerTemplate}
 * sends.
 *
 * <p>Only the Kafka publish step carries a Dead Letter Channel: a malformed
 * upload (bad headers, unparseable rows) is a caller error the controller
 * should reject immediately, not something 14 seconds of redelivery will fix.
 * A transient Kafka outage publishing an already-validated row is exactly
 * what User Story 11's retry requirement is for — and a permanently failed
 * one now lands on a dead-letter topic and is reflected as rejected in the
 * outcome the caller sees, not silently reported as accepted.
 */
@Component
public class ExcelImportRoute extends RouteBuilder {

    private static final String MEMO_DETECTED_TOPIC = "{{memo-detected.topic:mbp.integration.memo-detected}}";
    private static final String KAFKA_BROKERS = "{{kafka.bootstrap-servers:localhost:9092}}";

    private final ExcelImportProcessor processor;
    private final MemoDetectedPublisher publisher;
    private final NotificationClient notificationClient;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ExcelImportRoute(ExcelImportProcessor processor, MemoDetectedPublisher publisher,
                             NotificationClient notificationClient, AuditLogger auditLogger, Clock clock,
                             ObjectMapper objectMapper) {
        this.processor = processor;
        this.publisher = publisher;
        this.notificationClient = notificationClient;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure() {
        from("direct:importExcelFile")
                .routeId("excel-import")
                .process(exchange -> {
                    String actor = exchange.getIn().getHeader("actor", String.class);
                    String fileName = exchange.getIn().getHeader("fileName", String.class);
                    InputStream content = exchange.getIn().getBody(InputStream.class);

                    ImportResult result = processor.process(actor, fileName, content);
                    Set<String> failedAccountNumbers =
                            publisher.publishAndReturnFailedAccountNumbers(result.acceptedEvents());
                    ImportOutcome finalOutcome =
                            processor.reconcileWithPublishFailures(result.outcome(), failedAccountNumbers);
                    processor.auditFinalOutcome(actor, finalOutcome);

                    exchange.getIn().setBody(finalOutcome);
                });

        from("direct:publishMemoDetected")
                .routeId("publish-memo-detected")
                .onException(Exception.class)
                        .maximumRedeliveries("{{excel-import.publish.max-redeliveries:3}}")
                        .redeliveryDelay("{{excel-import.publish.redelivery-delay-ms:2000}}")
                        .backOffMultiplier(2.0)
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .handled(true)
                        .process(exchange -> {
                            Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                            String detail = cause == null ? "unknown error" : String.valueOf(cause.getMessage());
                            String accountNumber = exchange.getIn().getHeader(KafkaConstants.KEY, String.class);
                            notificationClient.alertOperations("excel-import-service publish failed after retries", detail);
                            auditLogger.record(new AuditEvent(
                                    clock.instant(), "system", "MEMO_DETECTED_PUBLISH_FAILED", "MemoAccount",
                                    accountNumber, "excel-import-service", detail));
                            exchange.getIn().setHeader(MemoDetectedPublisher.PUBLISH_FAILED_HEADER, true);
                        })
                        .to("kafka:" + MEMO_DETECTED_TOPIC + ".dlq?brokers=" + KAFKA_BROKERS)
                .end()
                .process(exchange -> {
                    MemoDetectedEvent event = exchange.getIn().getBody(MemoDetectedEvent.class);
                    exchange.getIn().setHeader(KafkaConstants.KEY, event.accountNumber());
                    exchange.getIn().setBody(objectMapper.writeValueAsString(event));
                })
                .to("kafka:" + MEMO_DETECTED_TOPIC + "?brokers=" + KAFKA_BROKERS);
    }
}
