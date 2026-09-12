package com.uba.mbp.integration.writeoffdetection.scan;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.writeoffdetection.config.WriteOffDetectionProperties;
import com.uba.mbp.integration.writeoffdetection.event.MemoDetectedEvent;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractClient;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractCustomerSummary;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractSavingsAccount;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractSavingsTransaction;
import com.uba.mbp.integration.writeoffdetection.notification.NotificationClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The business logic (RFP §3.13(bis)): scan every active client's savings
 * transactions for a narration matching the write-off phrase. Deliberately a
 * plain bean, not Camel code, so it's testable without a running route —
 * the Camel route (WriteOffDetectionRoute) only orchestrates calling this.
 *
 * <p>Fineract-generic per ADR-0012: this class matches on narration text and
 * publishes an account's real balance, both concepts a real Finacle
 * integration would also have — nothing here depends on a Fineract-specific
 * concept. Only {@link FineractClient}'s implementation is Fineract-shaped.
 */
@Component
public class WriteOffScanner {

    private static final String SOURCE = "write-off-detection-service";

    private final FineractClient fineractClient;
    private final DetectedWriteOffStore detectedStore;
    private final AuditLogger auditLogger;
    private final NotificationClient notificationClient;
    private final Clock clock;
    private final Pattern writeOffPattern;

    public WriteOffScanner(
            FineractClient fineractClient, WriteOffDetectionProperties properties, DetectedWriteOffStore detectedStore,
            AuditLogger auditLogger, NotificationClient notificationClient, Clock clock) {
        this.fineractClient = fineractClient;
        this.detectedStore = detectedStore;
        this.auditLogger = auditLogger;
        this.notificationClient = notificationClient;
        this.clock = clock;
        this.writeOffPattern = Pattern.compile(properties.getPattern(), Pattern.CASE_INSENSITIVE);
    }

    public List<MemoDetectedEvent> scan() {
        List<MemoDetectedEvent> detected = new ArrayList<>();

        for (FineractCustomerSummary customer : fineractClient.listActiveClients()) {
            for (Long savingsAccountId : fineractClient.listSavingsAccountIds(customer.id())) {
                FineractSavingsAccount account = fineractClient.getSavingsAccountWithTransactions(savingsAccountId);
                account.transactions().stream()
                        .filter(tx -> tx.note() != null && writeOffPattern.matcher(tx.note()).find())
                        .filter(tx -> !detectedStore.alreadyDetected(savingsAccountId, tx.id()))
                        .forEach(tx -> {
                            if (tx.date() == null) {
                                // Never fabricate a transfer date (RFP §3.13(bis) requires the real
                                // one) — surface the gap instead of silently guessing "now". Still
                                // mark it detected so a permanently date-less transaction alerts once,
                                // not on every scan cycle forever.
                                detectedStore.markDetected(savingsAccountId, tx.id());
                                notificationClient.alertOperations(
                                        "write-off-detection-service found a write-off with no transfer date",
                                        "savingsAccountId=" + savingsAccountId + " transactionId=" + tx.id());
                                return;
                            }
                            MemoDetectedEvent event = toEvent(customer, account, tx);
                            detected.add(event);
                            detectedStore.markDetected(savingsAccountId, tx.id());
                            audit(event);
                        });
            }
        }

        return detected;
    }

    private MemoDetectedEvent toEvent(FineractCustomerSummary customer, FineractSavingsAccount account, FineractSavingsTransaction tx) {
        return new MemoDetectedEvent(
                account.accountNo(),
                String.valueOf(customer.id()),
                // Fineract's closest concept to Finacle's Branch/SOL is the
                // customer's office — a name ("Abuja Branch"), not a numeric
                // SOL code, since that's the only real Fineract field available.
                customer.officeName(),
                account.currencyCode(),
                String.valueOf(tx.id()),
                tx.note(),
                account.accountBalance(),
                tx.date().atStartOfDay(ZoneOffset.UTC).toInstant(),
                SOURCE,
                // Country is deliberately null, not guessed: reference-data-config
                // (RFP §3.14) owns Country resolution, not this adapter — a wrong
                // guess (e.g. always "NG") would be worse than an honest gap for a
                // pan-African bank. memo-balance's consumed-event contract already
                // treats country as nullable.
                null);
    }

    private void audit(MemoDetectedEvent event) {
        auditLogger.record(new AuditEvent(
                clock.instant(), "system", "WRITE_OFF_DETECTED", "MemoDetectedEvent",
                event.accountNumber(), SOURCE,
                "postingReference=" + event.postingReference() + " matchedWriteOffPattern=true"));
    }
}
