package com.uba.mbp.integration.visionetl.route;

import com.uba.mbp.integration.visionetl.event.VisionBalanceSyncedEvent;
import com.uba.mbp.integration.visionetl.notification.NotificationClient;
import com.uba.mbp.integration.visionetl.scan.VisionBalanceScanner;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** ADR-0011: Polling Consumer -> Splitter -> Message Translator -> Kafka Producer, with a Dead Letter Channel. */
@Component
public class VisionBalanceSyncRoute extends RouteBuilder {

    private final VisionBalanceScanner scanner;
    private final NotificationClient notificationClient;
    private final ObjectMapper objectMapper;

    public VisionBalanceSyncRoute(VisionBalanceScanner scanner, NotificationClient notificationClient, ObjectMapper objectMapper) {
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
                            "vision-etl-connector sync failed after retries",
                            cause == null ? "unknown error" : String.valueOf(cause.getMessage()));
                });

        from("timer:visionBalanceSync?period={{vision.sync.interval-ms:60000}}")
                .routeId("vision-balance-sync")
                .bean(scanner, "scan")
                .split(body())
                    .to("direct:publishVisionBalanceSynced")
                .end();

        from("direct:publishVisionBalanceSynced")
                .routeId("publish-vision-balance-synced")
                .process(exchange -> {
                    VisionBalanceSyncedEvent event = exchange.getIn().getBody(VisionBalanceSyncedEvent.class);
                    exchange.getIn().setHeader(KafkaConstants.KEY, event.accountNumber());
                    exchange.getIn().setBody(objectMapper.writeValueAsString(event));
                })
                .to("kafka:{{vision-balance-synced.topic:mbp.integration.vision-balance-synced}}"
                        + "?brokers={{kafka.bootstrap-servers:localhost:9092}}");
    }
}
