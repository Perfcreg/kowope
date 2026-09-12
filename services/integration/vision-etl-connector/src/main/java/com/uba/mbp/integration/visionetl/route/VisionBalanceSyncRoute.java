package com.uba.mbp.integration.visionetl.route;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.visionetl.event.VisionBalanceSyncedEvent;
import com.uba.mbp.integration.visionetl.notification.NotificationClient;
import com.uba.mbp.integration.visionetl.scan.VisionBalanceScanner;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/** ADR-0011: Polling Consumer -> Splitter -> Message Translator -> Kafka Producer, with a Dead Letter Channel. */
@Component
public class VisionBalanceSyncRoute extends RouteBuilder {

    private static final String VISION_BALANCE_SYNCED_TOPIC = "{{vision-balance-synced.topic:mbp.integration.vision-balance-synced}}";
    private static final String KAFKA_BROKERS = "{{kafka.bootstrap-servers:localhost:9092}}";

    private final VisionBalanceScanner scanner;
    private final NotificationClient notificationClient;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public VisionBalanceSyncRoute(VisionBalanceScanner scanner, NotificationClient notificationClient,
                                   AuditLogger auditLogger, Clock clock, ObjectMapper objectMapper) {
        this.scanner = scanner;
        this.notificationClient = notificationClient;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure() {
        RouteDefinition syncRoute = from("timer:visionBalanceSync?period={{vision.sync.interval-ms:60000}}")
                .routeId("vision-balance-sync");
        configureRetryAlertAndAudit(syncRoute, "vision-etl-connector sync failed after retries");
        syncRoute
                .bean(scanner, "scan")
                .split(body())
                    .to("direct:publishVisionBalanceSynced")
                .end();

        // A permanently failed publish still isn't lost — it lands on a
        // dead-letter topic instead of only being logged.
        RouteDefinition publishRoute = from("direct:publishVisionBalanceSynced")
                .routeId("publish-vision-balance-synced");
        configureRetryAlertAndDeadLetter(publishRoute, "vision-etl-connector publish failed after retries",
                "kafka:" + VISION_BALANCE_SYNCED_TOPIC + ".dlq?brokers=" + KAFKA_BROKERS);
        publishRoute
                .process(exchange -> {
                    VisionBalanceSyncedEvent event = exchange.getIn().getBody(VisionBalanceSyncedEvent.class);
                    exchange.getIn().setHeader(KafkaConstants.KEY, event.accountNumber());
                    exchange.getIn().setBody(objectMapper.writeValueAsString(event));
                })
                .to("kafka:" + VISION_BALANCE_SYNCED_TOPIC + "?brokers=" + KAFKA_BROKERS);
    }

    /** The sync route's bulk failure is worth its own audit trail entry (User Story 13); it has no per-event DLQ. */
    private void configureRetryAlertAndAudit(RouteDefinition route, String alertSubject) {
        OnExceptionDefinition onException = retryThenAlert(route, alertSubject);
        onException.process(exchange -> auditLogger.record(new AuditEvent(
                clock.instant(), "system", "VISION_BALANCE_SYNC_FAILED", "VisionBalanceSync",
                "cycle-" + clock.instant(), "vision-etl-connector", failureDetail(exchange))));
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
