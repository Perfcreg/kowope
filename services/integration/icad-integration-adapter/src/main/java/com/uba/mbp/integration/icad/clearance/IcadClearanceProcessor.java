package com.uba.mbp.integration.icad.clearance;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.icad.client.IcadClient;
import com.uba.mbp.integration.icad.client.IcadFetchResult;
import com.uba.mbp.integration.icad.client.IcadPushResult;
import com.uba.mbp.integration.icad.config.IcadProperties;
import com.uba.mbp.integration.icad.event.IcadClearanceOutcomeEvent;
import com.uba.mbp.integration.icad.notification.NotificationClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plain orchestration bean, no Camel dependency — matches the other three
 * adapters' pattern of keeping business logic out of the route. Two
 * responsibilities, both about the same pending-clearance worklist: push a
 * new request onto ICAD (User Story 6), and poll ICAD for outcomes on
 * whatever's still pending (User Story 7), auditing every call (User Story 13).
 *
 * <p>{@link #pollForOutcomes()} does NOT remove a resolved item from
 * {@link PendingClearanceStore} — the Camel route does that, only after
 * confirming the outcome was actually published to Kafka (see
 * {@code IcadClearanceRoute}), so a publish failure leaves the item pending
 * for the next poll cycle instead of silently dropping the outcome.
 *
 * <p>ADR-0020: the same poll cycle also escalates a still-pending clearance
 * to CREDIT_ADMIN once it's older than {@link IcadProperties#getEscalationSla()}
 * (RFP §3.8/§4.6/§3.9) — escalated exactly once per reference via
 * {@link #escalatedReferences}, an in-memory-only set with the same
 * restart-loses-state caveat as {@link PendingClearanceStore} itself
 * (ADR-0015): a restart re-arms escalation for anything still overdue,
 * which re-escalates rather than silently never escalating again.
 */
@Component
public class IcadClearanceProcessor {

    private static final String SOURCE = "icad-integration-adapter";
    private static final String ESCALATION_RECIPIENT_GROUP = "CREDIT_ADMIN";

    private final IcadClient icadClient;
    private final PendingClearanceStore pendingStore;
    private final AuditLogger auditLogger;
    private final NotificationClient notificationClient;
    private final Clock clock;
    private final IcadProperties properties;
    private final Set<String> escalatedReferences = ConcurrentHashMap.newKeySet();

    public IcadClearanceProcessor(IcadClient icadClient, PendingClearanceStore pendingStore,
                                   AuditLogger auditLogger, NotificationClient notificationClient, Clock clock,
                                   IcadProperties properties) {
        this.icadClient = icadClient;
        this.pendingStore = pendingStore;
        this.auditLogger = auditLogger;
        this.notificationClient = notificationClient;
        this.clock = clock;
        this.properties = properties;
    }

    public ClearanceRequestAccepted requestClearance(String actor, ClearanceRequest request) {
        IcadPushResult pushResult = icadClient.pushAccount(request);
        pendingStore.add(new PendingClearance(
                pushResult.reference(), request.accountNumber(), request.customerId(), clock.instant()));

        auditLogger.record(new AuditEvent(
                clock.instant(), actor, "ICAD_CLEARANCE_REQUESTED", "MemoAccount", request.accountNumber(),
                SOURCE, "icadReference=" + pushResult.reference()));

        return new ClearanceRequestAccepted(request.accountNumber(), pushResult.reference(), pushResult.status());
    }

    /**
     * One pending clearance failing to fetch (a transient ICAD outage) must
     * not block evaluation of the rest of the worklist — each is isolated and
     * alerted independently (User Story 11/12) rather than aborting the whole
     * poll cycle.
     */
    public List<IcadClearanceOutcomeEvent> pollForOutcomes() {
        List<IcadClearanceOutcomeEvent> resolved = new ArrayList<>();
        for (PendingClearance pending : pendingStore.all()) {
            IcadFetchResult fetchResult;
            try {
                fetchResult = icadClient.fetchAccount(pending.icadReference());
            } catch (RuntimeException e) {
                notificationClient.alertOperations(
                        "icad-integration-adapter fetchAccount failed for " + pending.icadReference(),
                        String.valueOf(e.getMessage()));
                continue;
            }
            if (!fetchResult.isResolved()) {
                escalateIfOverdue(pending);
                continue;
            }

            escalatedReferences.remove(pending.icadReference());

            resolved.add(new IcadClearanceOutcomeEvent(
                    pending.accountNumber(), pending.customerId(), pending.icadReference(),
                    fetchResult.status(), fetchResult.detail(), clock.instant(), SOURCE));

            auditLogger.record(new AuditEvent(
                    clock.instant(), "system", "ICAD_CLEARANCE_RESOLVED", "MemoAccount", pending.accountNumber(),
                    SOURCE, "icadReference=" + pending.icadReference() + " status=" + fetchResult.status()));
        }
        return resolved;
    }

    /**
     * RFP §3.9: "issue alerts for overdue action plans and escalate
     * unresolved items to stakeholders." Escalates once per reference, not
     * once per poll cycle — {@link #escalatedReferences} is the guard.
     */
    private void escalateIfOverdue(PendingClearance pending) {
        Duration age = Duration.between(pending.pushedAt(), clock.instant());
        if (age.compareTo(properties.getEscalationSla()) < 0) {
            return;
        }
        if (!escalatedReferences.add(pending.icadReference())) {
            return;
        }

        notificationClient.escalate(ESCALATION_RECIPIENT_GROUP,
                "ICAD clearance overdue: " + pending.accountNumber(),
                "Account " + pending.accountNumber() + " (ICAD reference " + pending.icadReference()
                        + ") has been pending clearance for " + age + ", exceeding the configured SLA of "
                        + properties.getEscalationSla() + ".");

        auditLogger.record(new AuditEvent(
                clock.instant(), "system", "ICAD_CLEARANCE_ESCALATED", "MemoAccount", pending.accountNumber(),
                SOURCE, "icadReference=" + pending.icadReference() + " pendingFor=" + age
                        + " slaThreshold=" + properties.getEscalationSla()));
    }
}
