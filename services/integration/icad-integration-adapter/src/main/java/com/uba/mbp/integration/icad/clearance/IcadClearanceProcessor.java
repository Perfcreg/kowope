package com.uba.mbp.integration.icad.clearance;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.icad.client.IcadClient;
import com.uba.mbp.integration.icad.client.IcadFetchResult;
import com.uba.mbp.integration.icad.client.IcadPushResult;
import com.uba.mbp.integration.icad.event.IcadClearanceOutcomeEvent;
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
 */
@Component
public class IcadClearanceProcessor {

    public static final String SOURCE = "icad-integration-adapter";

    private final IcadClient icadClient;
    private final PendingClearanceStore pendingStore;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public IcadClearanceProcessor(IcadClient icadClient, PendingClearanceStore pendingStore,
                                   AuditLogger auditLogger, Clock clock) {
        this.icadClient = icadClient;
        this.pendingStore = pendingStore;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    public ClearanceRequestOutcome requestClearance(String actor, ClearanceRequest request) {
        IcadPushResult pushResult = icadClient.pushAccount(request);
        pendingStore.add(new PendingClearance(pushResult.reference(), request.accountNumber(), request.customerId()));

        auditLogger.record(new AuditEvent(
                clock.instant(), actor, "ICAD_CLEARANCE_REQUESTED", "MemoAccount", request.accountNumber(),
                SOURCE, "icadReference=" + pushResult.reference()));

        return new ClearanceRequestOutcome(request.accountNumber(), pushResult.reference(), pushResult.status());
    }

    public List<IcadClearanceOutcomeEvent> pollForOutcomes() {
        List<IcadClearanceOutcomeEvent> resolved = new ArrayList<>();
        for (PendingClearance pending : pendingStore.all()) {
            IcadFetchResult fetchResult = icadClient.fetchAccount(pending.icadReference());
            if (!fetchResult.isResolved()) {
                continue;
            }

            resolved.add(new IcadClearanceOutcomeEvent(
                    pending.accountNumber(), pending.customerId(), pending.icadReference(),
                    fetchResult.status(), fetchResult.detail(), clock.instant(), SOURCE));

            auditLogger.record(new AuditEvent(
                    clock.instant(), "system", "ICAD_CLEARANCE_RESOLVED", "MemoAccount", pending.accountNumber(),
                    SOURCE, "status=" + fetchResult.status()));

            pendingStore.remove(pending.icadReference());
        }
        return resolved;
    }
}
