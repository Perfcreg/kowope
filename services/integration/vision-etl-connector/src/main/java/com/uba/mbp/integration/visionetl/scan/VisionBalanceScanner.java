package com.uba.mbp.integration.visionetl.scan;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.visionetl.event.VisionBalanceSyncedEvent;
import com.uba.mbp.integration.visionetl.fineract.FineractClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Integration spec User Stories 4-5: pull the authoritative balance figure
 * per account so memo-balance can reconcile against it. A plain bean, not
 * Camel code — see write-off-detection-service's WriteOffScanner for why.
 */
@Component
public class VisionBalanceScanner {

    private static final String SOURCE = "vision-etl-connector";

    private final FineractClient fineractClient;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public VisionBalanceScanner(FineractClient fineractClient, AuditLogger auditLogger, Clock clock) {
        this.fineractClient = fineractClient;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    public List<VisionBalanceSyncedEvent> scan() {
        List<VisionBalanceSyncedEvent> synced = new ArrayList<>();
        var now = clock.instant();

        for (Long clientId : fineractClient.listActiveClientIds()) {
            fineractClient.listSavingsAccountBalances(clientId).forEach(account -> {
                VisionBalanceSyncedEvent event = new VisionBalanceSyncedEvent(account.accountNo(), account.accountBalance(), now);
                synced.add(event);
                auditLogger.record(new AuditEvent(
                        now, "system", "VISION_BALANCE_SYNCED", "VisionBalanceSyncedEvent",
                        event.accountNumber(), SOURCE, "balance=" + event.balance()));
            });
        }

        return synced;
    }
}
