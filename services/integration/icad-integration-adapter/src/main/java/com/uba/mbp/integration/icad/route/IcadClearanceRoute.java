package com.uba.mbp.integration.icad.route;

import com.uba.mbp.integration.icad.clearance.ClearanceRequest;
import com.uba.mbp.integration.icad.clearance.IcadClearanceProcessor;
import com.uba.mbp.integration.icad.event.IcadClearanceOutcomeEvent;
import com.uba.mbp.integration.icad.notification.NotificationClient;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * ADR-0011: the EIP wiring, same shape as the other three adapters —
 * business logic lives in {@link IcadClearanceProcessor}. Two independent
 * flows share one Dead Letter Channel pattern each: the synchronous push
 * (User Story 6, called from the controller) and the Polling Consumer that
 * checks pending clearances and publishes outcomes (User Story 7).
 */
@Component
public class IcadClearanceRoute extends RouteBuilder {

    private final IcadClearanceProcessor processor;
    private final NotificationClient notificationClient;
    private final ObjectMapper objectMapper;

    public IcadClearanceRoute(IcadClearanceProcessor processor, NotificationClient notificationClient,
                               ObjectMapper objectMapper) {
        this.processor = processor;
        this.notificationClient = notificationClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure() {
        from("direct:requestClearance")
                .routeId("icad-request-clearance")
                .onException(Exception.class)
                        .maximumRedeliveries(3)
                        .redeliveryDelay(2000)
                        .backOffMultiplier(2.0)
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .handled(false)
                        .process(exchange -> {
                            Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                            notificationClient.alertOperations(
                                    "icad-integration-adapter pushAccount failed after retries",
                                    cause == null ? "unknown error" : String.valueOf(cause.getMessage()));
                        })
                .end()
                .process(exchange -> {
                    String actor = exchange.getIn().getHeader("actor", String.class);
                    ClearanceRequest request = exchange.getIn().getBody(ClearanceRequest.class);
                    exchange.getIn().setBody(processor.requestClearance(actor, request));
                });

        from("timer:icadClearancePoll?period={{icad.poll.interval-ms:30000}}")
                .routeId("icad-clearance-poll")
                .bean(processor, "pollForOutcomes")
                .split(body())
                    .to("direct:publishIcadClearanceOutcome")
                .end();

        from("direct:publishIcadClearanceOutcome")
                .routeId("publish-icad-clearance-outcome")
                .onException(Exception.class)
                        .maximumRedeliveries(3)
                        .redeliveryDelay(2000)
                        .backOffMultiplier(2.0)
                        .retryAttemptedLogLevel(LoggingLevel.WARN)
                        .handled(true)
                        .process(exchange -> {
                            Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                            notificationClient.alertOperations(
                                    "icad-integration-adapter publish failed after retries",
                                    cause == null ? "unknown error" : String.valueOf(cause.getMessage()));
                        })
                .end()
                .process(exchange -> {
                    IcadClearanceOutcomeEvent event = exchange.getIn().getBody(IcadClearanceOutcomeEvent.class);
                    exchange.getIn().setHeader(KafkaConstants.KEY, event.accountNumber());
                    exchange.getIn().setBody(objectMapper.writeValueAsString(event));
                })
                .to("kafka:{{icad-clearance-outcome.topic:mbp.integration.icad-clearance-outcome}}"
                        + "?brokers={{kafka.bootstrap-servers:localhost:9092}}");
    }
}
