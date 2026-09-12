package com.uba.mbp.integration.visionetl.scan;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.visionetl.event.VisionBalanceSyncedEvent;
import com.uba.mbp.integration.visionetl.fineract.FineractAccountBalance;
import com.uba.mbp.integration.visionetl.fineract.FineractClient;
import com.uba.mbp.integration.visionetl.notification.NotificationClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration spec User Stories 4-5: pull the authoritative balance figure
 * per account so memo-balance can reconcile against it. A plain bean, not
 * Camel code — see write-off-detection-service's WriteOffScanner for why.
 *
 * <p>Every active account is republished every cycle, not just changed ones
 * — User Story 5 asks for "an event per account per sync... a defined
 * trigger rather than polling Vision itself", which means each sync cycle
 * IS the trigger, not a delta. Unlike write-off detection's one-time
 * narration match, this adapter has no dedup store, deliberately.
 */
@Component
public class VisionBalanceScanner {

    private static final String SOURCE = "vision-etl-connector";

    private final FineractClient fineractClient;
    private final AuditLogger auditLogger;
    private final NotificationClient notificationClient;
    private final Clock clock;

    public VisionBalanceScanner(FineractClient fineractClient, AuditLogger auditLogger,
                                 NotificationClient notificationClient, Clock clock) {
        this.fineractClient = fineractClient;
        this.auditLogger = auditLogger;
        this.notificationClient = notificationClient;
        this.clock = clock;
    }

    public List<VisionBalanceSyncedEvent> scan() {
        List<VisionBalanceSyncedEvent> synced = new ArrayList<>();
        Instant now = clock.instant();

        for (Long clientId : fineractClient.listActiveClientIds()) {
            for (FineractAccountBalance account : fineractClient.listSavingsAccountBalances(clientId)) {
                if (account.accountNo() == null || account.accountNo().isBlank()) {
                    // Never publish an event with no real account identifier — it would
                    // collapse onto an empty Kafka key and reach memo-balance unidentifiable.
                    notificationClient.alertOperations(
                            "vision-etl-connector found a savings account with no account number",
                            "clientId=" + clientId);
                    continue;
                }
                VisionBalanceSyncedEvent event = toEvent(account, now);
                synced.add(event);
                audit(event);
            }
        }

        return synced;
    }

    private VisionBalanceSyncedEvent toEvent(FineractAccountBalance account, Instant now) {
        return new VisionBalanceSyncedEvent(account.accountNo(), account.accountBalance(), now);
    }

    private void audit(VisionBalanceSyncedEvent event) {
        // No balance figure in the audit detail — the account number already
        // identifies the record; the figure itself doesn't need to travel
        // into ELK (ADR-0005), which has no at-rest encryption established.
        auditLogger.record(new AuditEvent(
                event.syncedAt(), "system", "VISION_BALANCE_SYNCED", "VisionBalanceSyncedEvent",
                event.accountNumber(), SOURCE, "balanceSynced=true"));
    }
}
