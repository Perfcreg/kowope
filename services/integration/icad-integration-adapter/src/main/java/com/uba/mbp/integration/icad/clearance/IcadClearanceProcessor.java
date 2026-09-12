package com.uba.mbp.integration.icad.clearance;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.icad.client.IcadClient;
import com.uba.mbp.integration.icad.client.IcadFetchResult;
import com.uba.mbp.integration.icad.client.IcadPushResult;
import com.uba.mbp.integration.icad.event.IcadClearanceOutcomeEvent;
import com.uba.mbp.integration.icad.notification.NotificationClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

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
 */
@Component
public class IcadClearanceProcessor {

    private static final String SOURCE = "icad-integration-adapter";

    private final IcadClient icadClient;
    private final PendingClearanceStore pendingStore;
    private final AuditLogger auditLogger;
    private final NotificationClient notificationClient;
    private final Clock clock;

    public IcadClearanceProcessor(IcadClient icadClient, PendingClearanceStore pendingStore,
                                   AuditLogger auditLogger, NotificationClient notificationClient, Clock clock) {
        this.icadClient = icadClient;
        this.pendingStore = pendingStore;
        this.auditLogger = auditLogger;
        this.notificationClient = notificationClient;
        this.clock = clock;
    }

    public ClearanceRequestAccepted requestClearance(String actor, ClearanceRequest request) {
        IcadPushResult pushResult = icadClient.pushAccount(request);
        pendingStore.add(new PendingClearance(pushResult.reference(), request.accountNumber(), request.customerId()));

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
                continue;
            }

            resolved.add(new IcadClearanceOutcomeEvent(
                    pending.accountNumber(), pending.customerId(), pending.icadReference(),
                    fetchResult.status(), fetchResult.detail(), clock.instant(), SOURCE));

            auditLogger.record(new AuditEvent(
                    clock.instant(), "system", "ICAD_CLEARANCE_RESOLVED", "MemoAccount", pending.accountNumber(),
                    SOURCE, "icadReference=" + pending.icadReference() + " status=" + fetchResult.status()));
        }
        return resolved;
    }
}
