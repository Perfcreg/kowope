package com.uba.mbp.integration.excelimport.route;

import com.uba.mbp.integration.excelimport.ExcelImportProcessor;
import com.uba.mbp.integration.excelimport.event.MemoDetectedEvent;
import com.uba.mbp.integration.excelimport.notification.NotificationClient;
import com.uba.mbp.integration.excelimport.validate.ImportResult;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;

/**
 * ADR-0011: the EIP wiring. Business logic (parsing, validation, audit) lives
 * in {@link ExcelImportProcessor}; this route is a Content Enricher (stashes
 * the outcome as an exchange property while the body becomes the accepted
 * events) → Splitter → Message Translator (JSON) → Kafka Producer, restoring
 * the outcome afterwards so the controller can reply with it synchronously.
 *
 * <p>Only the Kafka publish step carries a Dead Letter Channel: a malformed
 * upload (bad headers, unparseable rows) is a caller error the controller
 * should reject immediately, not something 14 seconds of redelivery will fix.
 * A transient Kafka outage publishing an already-validated row is exactly
 * what User Story 11's retry requirement is for.
 */
@Component
public class ExcelImportRoute extends RouteBuilder {

    private final ExcelImportProcessor processor;
    private final NotificationClient notificationClient;
    private final ObjectMapper objectMapper;

    public ExcelImportRoute(ExcelImportProcessor processor, NotificationClient notificationClient,
                             ObjectMapper objectMapper) {
        this.processor = processor;
        this.notificationClient = notificationClient;
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
                    exchange.setProperty("importOutcome", result.outcome());
                    exchange.getIn().setBody(result.acceptedEvents());
                })
                .split(body())
                    .to("direct:publishMemoDetected")
                .end()
                .setBody(exchangeProperty("importOutcome"));

        from("direct:publishMemoDetected")
                .routeId("publish-memo-detected")
                .onException(Exception.class)
                        .maximumRedeliveries(3)
                        .redeliveryDelay(2000)
                        .backOffMultiplier(2.0)
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .handled(true)
                        .process(exchange -> {
                            Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                            notificationClient.alertOperations(
                                    "excel-import-service publish failed after retries",
                                    cause == null ? "unknown error" : String.valueOf(cause.getMessage()));
                        })
                .end()
                .process(exchange -> {
                    MemoDetectedEvent event = exchange.getIn().getBody(MemoDetectedEvent.class);
                    exchange.getIn().setHeader(KafkaConstants.KEY, event.accountNumber());
                    exchange.getIn().setBody(objectMapper.writeValueAsString(event));
                })
                .to("kafka:{{memo-detected.topic:mbp.integration.memo-detected}}"
                        + "?brokers={{kafka.bootstrap-servers:localhost:9092}}");
    }
}
