package com.uba.mbp.integration.writeoffdetection.route;

import com.uba.mbp.integration.writeoffdetection.event.MemoDetectedEvent;
import com.uba.mbp.integration.writeoffdetection.notification.NotificationClient;
import com.uba.mbp.integration.writeoffdetection.scan.WriteOffScanner;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * ADR-0011: the EIP wiring. Business logic lives in {@link WriteOffScanner};
 * this route is Polling Consumer (timer) → Splitter → Message Translator
 * (JSON) → Kafka Producer, wrapped in a Dead Letter Channel that satisfies
 * the integration spec's retry requirement (User Story 11) and alerts
 * operations once redeliveries are exhausted (User Story 12).
 */
@Component
public class WriteOffDetectionRoute extends RouteBuilder {

    private final WriteOffScanner scanner;
    private final NotificationClient notificationClient;
    private final ObjectMapper objectMapper;

    public WriteOffDetectionRoute(WriteOffScanner scanner, NotificationClient notificationClient, ObjectMapper objectMapper) {
        this.scanner = scanner;
        this.notificationClient = notificationClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure() {
        onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(2000)
                .backOffMultiplier(2.0)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .handled(true)
                .process(exchange -> {
                    Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                    notificationClient.alertOperations(
                            "write-off-detection-service scan failed after retries",
                            cause == null ? "unknown error" : String.valueOf(cause.getMessage()));
                });

        from("timer:writeOffScan?period={{write-off.scan.interval-ms:30000}}")
                .routeId("write-off-detection-scan")
                .bean(scanner, "scan")
                .split(body())
                    .to("direct:publishMemoDetected")
                .end();

        from("direct:publishMemoDetected")
                .routeId("publish-memo-detected")
                .process(exchange -> {
                    MemoDetectedEvent event = exchange.getIn().getBody(MemoDetectedEvent.class);
                    exchange.getIn().setHeader(KafkaConstants.KEY, event.accountNumber());
                    exchange.getIn().setBody(objectMapper.writeValueAsString(event));
                })
                .to("kafka:{{memo-detected.topic:mbp.integration.memo-detected}}"
                        + "?brokers={{kafka.bootstrap-servers:localhost:9092}}");
    }
}
