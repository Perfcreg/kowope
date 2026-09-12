package com.uba.mbp.integration.icad.route;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.icad.clearance.ClearanceRequest;
import com.uba.mbp.integration.icad.clearance.IcadClearanceProcessor;
import com.uba.mbp.integration.icad.clearance.PendingClearanceStore;
import com.uba.mbp.integration.icad.event.IcadClearanceOutcomeEvent;
import com.uba.mbp.integration.icad.notification.NotificationClient;
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
 * ADR-0011: the EIP wiring, same shape as the other three adapters —
 * business logic lives in {@link IcadClearanceProcessor}. Three flows share
 * one Dead Letter Channel shape each ({@link #deadLetterChannel}): the
 * synchronous push (User Story 6), the Polling Consumer that checks pending
 * clearances (User Story 7), and publishing a resolved outcome to Kafka.
 *
 * <p>A resolved clearance is only removed from {@link PendingClearanceStore}
 * after this route confirms Kafka accepted it (see the publish route below)
 * — {@code IcadClearanceProcessor.pollForOutcomes()} deliberately leaves that
 * removal to here, so a publish failure leaves the item pending for the next
 * poll cycle instead of silently losing the outcome (ADR-0015).
 */
@Component
public class IcadClearanceRoute extends RouteBuilder {

    private final IcadClearanceProcessor processor;
    private final PendingClearanceStore pendingStore;
    private final NotificationClient notificationClient;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public IcadClearanceRoute(IcadClearanceProcessor processor, PendingClearanceStore pendingStore,
                               NotificationClient notificationClient, AuditLogger auditLogger, Clock clock,
                               ObjectMapper objectMapper) {
        this.processor = processor;
        this.pendingStore = pendingStore;
        this.notificationClient = notificationClient;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configure() {
        // The synchronous push is still worth 3 retries even though it's on a
        // controller-facing path (unlike ExcelImportRoute's caller-input
        // validation, which correctly skips retries): a pushAccount failure
        // here is a transient network/ICAD-outage failure, not a caller
        // error — the same category of failure Fineract's HTTP calls
        // elsewhere in this context already retry.
        RouteDefinition requestClearance = from("direct:requestClearance").routeId("icad-request-clearance");
        deadLetterChannel(requestClearance, false, "icad-integration-adapter pushAccount failed after retries", null);
        requestClearance
                .process(exchange -> {
                    ClearanceRequest request = exchange.getIn().getBody(ClearanceRequest.class);
                    exchange.setProperty("clearanceRequest", request);
                })
                .process(exchange -> {
                    String actor = exchange.getIn().getHeader("actor", String.class);
                    ClearanceRequest request = exchange.getIn().getBody(ClearanceRequest.class);
                    exchange.getIn().setBody(processor.requestClearance(actor, request));
                });

        // Per-pending-item resilience (a transient fetch failure for one
        // clearance shouldn't block evaluating the rest) lives inside
        // IcadClearanceProcessor.pollForOutcomes() itself; this route-level
        // channel is the safety net for the bean invocation failing outright.
        RouteDefinition clearancePoll = from("timer:icadClearancePoll?period={{icad.poll.interval-ms:30000}}")
                .routeId("icad-clearance-poll");
        deadLetterChannel(clearancePoll, true, "icad-integration-adapter poll cycle failed after retries", null);
        clearancePoll
                .bean(processor, "pollForOutcomes")
                .split(body())
                    .to("direct:publishIcadClearanceOutcome")
                .end();

        // A permanently failed publish still isn't lost — it lands on a
        // dead-letter topic instead of only being logged, closing the gap
        // ADR-0015 flags for this specific failure mode (as opposed to the
        // restart-while-pending gap, which ADR-0015 leaves open).
        RouteDefinition publishOutcome = from("direct:publishIcadClearanceOutcome")
                .routeId("publish-icad-clearance-outcome");
        deadLetterChannel(publishOutcome, true, "icad-integration-adapter publish failed after retries",
                "kafka:{{icad-clearance-outcome.topic:mbp.integration.icad-clearance-outcome}}.dlq"
                        + "?brokers={{kafka.bootstrap-servers:localhost:9092}}");
        publishOutcome
                .process(exchange -> {
                    IcadClearanceOutcomeEvent event = exchange.getIn().getBody(IcadClearanceOutcomeEvent.class);
                    exchange.setProperty("icadReference", event.icadReference());
                    exchange.getIn().setHeader(KafkaConstants.KEY, event.accountNumber());
                    exchange.getIn().setBody(objectMapper.writeValueAsString(event));
                })
                .to("kafka:{{icad-clearance-outcome.topic:mbp.integration.icad-clearance-outcome}}"
                        + "?brokers={{kafka.bootstrap-servers:localhost:9092}}")
                .process(exchange -> pendingStore.remove(exchange.getProperty("icadReference", String.class)));
    }

    /**
     * Standards: the three flows above previously copy-pasted this same
     * 10-line block, differing only in {@code handled} and the alert
     * subject/action. Extracted once here instead. When {@code dlqUri} is
     * given, the exhausted-retry exchange is also routed there — this must
     * happen INSIDE the exception handler's own chain (before its
     * {@code .end()}), not appended after this method returns, or it would
     * fire for every exchange instead of only failed ones.
     */
    private void deadLetterChannel(RouteDefinition route, boolean handled, String alertSubject, String dlqUri) {
        OnExceptionDefinition onException = route.onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(2000)
                .backOffMultiplier(2.0)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .handled(handled)
                .process(exchange -> {
                    Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
                    String detail = cause == null ? "unknown error" : String.valueOf(cause.getMessage());
                    notificationClient.alertOperations(alertSubject, detail);

                    ClearanceRequest failedRequest = exchange.getProperty("clearanceRequest", ClearanceRequest.class);
                    if (failedRequest != null) {
                        auditLogger.record(new AuditEvent(
                                clock.instant(), "system", "ICAD_CLEARANCE_PUSH_FAILED", "MemoAccount",
                                failedRequest.accountNumber(), "icad-integration-adapter", detail));
                    }
                });
        if (dlqUri != null) {
            onException.to(dlqUri);
        }
        onException.end();
    }
}
