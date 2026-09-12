package com.uba.mbp.integration.writeoffdetection.route;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.writeoffdetection.event.MemoDetectedEvent;
import com.uba.mbp.integration.writeoffdetection.notification.NotificationClient;
import com.uba.mbp.integration.writeoffdetection.scan.WriteOffScanner;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * ADR-0011: the EIP wiring. Business logic lives in {@link WriteOffScanner};
 * this route is Polling Consumer (timer) → Splitter → Message Translator
 * (JSON) → Kafka Producer, wrapped in a Dead Letter Channel that satisfies
 * the integration spec's retry requirement (User Story 11) and alerts
 * operations once redeliveries are exhausted (User Story 12).
 */
@Component
public class WriteOffDetectionRoute extends RouteBuilder {

    private static final String MEMO_DETECTED_TOPIC = "{{memo-detected.topic:mbp.integration.memo-detected}}";
    private static final String KAFKA_BROKERS = "{{kafka.bootstrap-servers:localhost:9092}}";

    private final WriteOffScanner scanner;
    private final NotificationClient notificationClient;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public WriteOffDetectionRoute(WriteOffScanner scanner, NotificationClient notificationClient,
                                   AuditLogger auditLogger, Clock clock, ObjectMapper objectMapper) {
        this.scanner = scanner;
        this.notificationClient = notificationClient;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure() {
        RouteDefinition scanRoute = from("timer:writeOffScan?period={{write-off.scan.interval-ms:30000}}")
                .routeId("write-off-detection-scan");
        configureRetryAlertAndAudit(scanRoute, "write-off-detection-service scan failed after retries");
        scanRoute
                .bean(scanner, "scan")
                .split(body())
                    .to("direct:publishMemoDetected")
                .end();

        // A permanently failed publish still isn't lost — it lands on a
        // dead-letter topic instead of only being logged, rather than the
        // alert+log-only handling the publish route previously had.
        RouteDefinition publishRoute = from("direct:publishMemoDetected")
                .routeId("publish-memo-detected");
        configureRetryAlertAndDeadLetter(publishRoute, "write-off-detection-service publish failed after retries",
                "kafka:" + MEMO_DETECTED_TOPIC + ".dlq?brokers=" + KAFKA_BROKERS);
        publishRoute
                .process(exchange -> {
                    MemoDetectedEvent event = exchange.getIn().getBody(MemoDetectedEvent.class);
                    exchange.getIn().setHeader(KafkaConstants.KEY, event.accountNumber());
                    exchange.getIn().setBody(objectMapper.writeValueAsString(event));
                })
                .to("kafka:" + MEMO_DETECTED_TOPIC + "?brokers=" + KAFKA_BROKERS);
    }

    /** The scan route's bulk failure is worth its own audit trail entry (User Story 13); it has no per-event DLQ. */
    private void configureRetryAlertAndAudit(RouteDefinition route, String alertSubject) {
        OnExceptionDefinition onException = retryThenAlert(route, alertSubject);
        onException.process(exchange -> auditLogger.record(new AuditEvent(
                clock.instant(), "system", "WRITE_OFF_SCAN_FAILED", "WriteOffScan",
                "cycle-" + clock.instant(), "write-off-detection-service", failureDetail(exchange))));
        onException.end();
    }

    /** The publish route's failure is per-event; a dead-letter topic replaces its own audit entry. */
    private void configureRetryAlertAndDeadLetter(RouteDefinition route, String alertSubject, String dlqUri) {
        OnExceptionDefinition onException = retryThenAlert(route, alertSubject);
        onException.to(dlqUri);
        onException.end();
    }

    private OnExceptionDefinition retryThenAlert(RouteDefinition route, String alertSubject) {
        return route.onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(2000)
                .backOffMultiplier(2.0)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .handled(true)
                .process(exchange -> notificationClient.alertOperations(alertSubject, failureDetail(exchange)));
    }

    private String failureDetail(Exchange exchange) {
        Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        return cause == null ? "unknown error" : String.valueOf(cause.getMessage());
    }
}
