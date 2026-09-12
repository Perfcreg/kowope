package com.uba.mbp.integration.writeoffdetection.scan;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.writeoffdetection.config.FineractProperties;
import com.uba.mbp.integration.writeoffdetection.event.MemoDetectedEvent;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractClient;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractClientSummary;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractSavingsAccount;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The business logic (RFP §3.13(bis)): scan every active client's savings
 * transactions for a narration matching the write-off phrase. Deliberately a
 * plain bean, not Camel code, so it's testable without a running route —
 * the Camel route (WriteOffDetectionRoute) only orchestrates calling this.
 */
@Component
public class WriteOffScanner {

    private static final String SOURCE = "write-off-detection-service";
    private static final String COUNTRY = "NG";

    private final FineractClient fineractClient;
    private final FineractProperties properties;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public WriteOffScanner(
            FineractClient fineractClient, FineractProperties properties, AuditLogger auditLogger, Clock clock) {
        this.fineractClient = fineractClient;
        this.properties = properties;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    public List<MemoDetectedEvent> scan() {
        Pattern writeOffPattern = Pattern.compile(properties.getWriteOffPattern(), Pattern.CASE_INSENSITIVE);
        List<MemoDetectedEvent> detected = new ArrayList<>();

        for (FineractClientSummary client : fineractClient.listActiveClients()) {
            for (Long savingsAccountId : fineractClient.listSavingsAccountIds(client.id())) {
                FineractSavingsAccount account = fineractClient.getSavingsAccountWithTransactions(savingsAccountId);
                account.transactions().stream()
                        .filter(tx -> tx.note() != null && writeOffPattern.matcher(tx.note()).find())
                        .forEach(tx -> {
                            MemoDetectedEvent event = new MemoDetectedEvent(
                                    account.accountNo(),
                                    String.valueOf(client.id()),
                                    client.officeName(),
                                    account.currencyCode(),
                                    "FINERACT-TXN-" + tx.id(),
                                    tx.note(),
                                    tx.amount(),
                                    tx.date() == null ? clock.instant() : tx.date().atStartOfDay(ZoneOffset.UTC).toInstant(),
                                    SOURCE,
                                    COUNTRY);
                            detected.add(event);
                            audit(event);
                        });
            }
        }

        return detected;
    }

    private void audit(MemoDetectedEvent event) {
        auditLogger.record(new AuditEvent(
                clock.instant(), "system", "WRITE_OFF_DETECTED", "MemoDetectedEvent",
                event.accountNumber(), SOURCE, "narration=\"" + event.narration() + "\""));
    }
}
