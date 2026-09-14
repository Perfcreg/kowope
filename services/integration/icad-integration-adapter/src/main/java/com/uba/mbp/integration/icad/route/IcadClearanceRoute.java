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
 * one Dead Letter Channel shape each: the synchronous push (User Story 6),
 * the Polling Consumer that checks pending clearances (User Story 7), and
 * publishing a resolved outcome to Kafka.
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
        // elsewhere in this context already retry. handled(false): the
        // exception must still reach the controller's ProducerTemplate call
        // after exhausted retries, so it can reply with a real 502 instead of
        // a stale/empty body.
        RouteDefinition requestClearance = from("direct:requestClearance").routeId("icad-request-clearance");
        configureSynchronousRetryWithAlertAndAudit(requestClearance, "icad-integration-adapter pushAccount failed after retries");
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
        // No synchronous caller is waiting on a timer route, so the failure
        // is simply absorbed (handled) after alerting.
        RouteDefinition clearancePoll = from("timer:icadClearancePoll?period={{icad.poll.interval-ms:30000}}")
                .routeId("icad-clearance-poll");
        configureBackgroundRetryWithAlert(clearancePoll, "icad-integration-adapter poll cycle failed after retries");
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
        configureBackgroundRetryWithAlertAndDeadLetter(publishOutcome, "icad-integration-adapter publish failed after retries",
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
     * Three distinctly-named configurations instead of one method with a
     * {@code boolean handled} / nullable-{@code dlqUri}-as-mode-switch flag
     * (Standards finding): the push route's {@code handled(false)} is not a
     * style choice, it's what lets the exception still reach the
     * synchronous controller call after retries exhaust — collapsing it into
     * a shared flag with the two fire-and-forget routes would be genuinely
     * wrong, not just less readable. Deliberately not named
     * {@code deadLetterChannel}, which would shadow {@code RouteBuilder}'s
     * own inherited method of that name — a naming collision an earlier
     * draft of this exact fix, on a sibling branch, had to correct.
     */
    private void configureSynchronousRetryWithAlertAndAudit(RouteDefinition route, String alertSubject) {
        retryPolicy(route)
                .handled(false)
                .process(exchange -> {
                    notificationClient.alertOperations(alertSubject, failureDetail(exchange));
                    auditPushFailureIfKnown(exchange);
                })
                .end();
    }

    private void configureBackgroundRetryWithAlert(RouteDefinition route, String alertSubject) {
        retryPolicy(route)
                .handled(true)
                .process(exchange -> notificationClient.alertOperations(alertSubject, failureDetail(exchange)))
                .end();
    }

    private void configureBackgroundRetryWithAlertAndDeadLetter(RouteDefinition route, String alertSubject, String dlqUri) {
        OnExceptionDefinition onException = retryPolicy(route)
                .handled(true)
                .process(exchange -> notificationClient.alertOperations(alertSubject, failureDetail(exchange)));
        onException.to(dlqUri);
        onException.end();
    }

    private OnExceptionDefinition retryPolicy(RouteDefinition route) {
        return route.onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(2000)
                .backOffMultiplier(2.0)
                .retryAttemptedLogLevel(LoggingLevel.WARN);
    }

    private void auditPushFailureIfKnown(Exchange exchange) {
        ClearanceRequest failedRequest = exchange.getProperty("clearanceRequest", ClearanceRequest.class);
        if (failedRequest != null) {
            // System-wide-audit fix (2026-09-15): the real submitting user is
            // knowable (the controller sets it as the "actor" header, still
            // present on the same Exchange through retries) but was previously
            // discarded in favor of a hardcoded "system" — breaking
            // traceability for a compliance-sensitive push failure.
            String actor = exchange.getIn().getHeader("actor", String.class);
            auditLogger.record(new AuditEvent(
                    clock.instant(), actor != null ? actor : "system", "ICAD_CLEARANCE_PUSH_FAILED", "MemoAccount",
                    failedRequest.accountNumber(), "icad-integration-adapter", failureDetail(exchange)));
        }
    }

    private String failureDetail(Exchange exchange) {
        Throwable cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        return cause == null ? "unknown error" : String.valueOf(cause.getMessage());
    }
}
