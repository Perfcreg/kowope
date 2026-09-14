package com.uba.mbp.memobalance.service;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.event.MemoDetectedEvent;
import com.uba.mbp.memobalance.exception.InvalidMemoDetectedEventException;
import com.uba.mbp.memobalance.referencedata.CountryConfig;
import com.uba.mbp.memobalance.referencedata.CountryConfigLookup;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Ticket 01/02: consumes {@link MemoDetectedEvent}, creating a new Memo record or,
 * for a repeat detection of an already-tracked account, updating it in place
 * while preserving the earliest transfer date (RFP §3.13(bis)).
 */
@Service
public class MemoIngestionService {

    private final MemoAccountRepository repository;
    private final CountryConfigLookup countryConfigLookup;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public MemoIngestionService(
            MemoAccountRepository repository,
            CountryConfigLookup countryConfigLookup,
            AuditLogger auditLogger,
            Clock clock) {
        this.repository = repository;
        this.countryConfigLookup = countryConfigLookup;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional
    public MemoAccount ingest(MemoDetectedEvent event) {
        validate(event);
        Instant now = clock.instant();
        CountryConfig countryConfig = countryConfigLookup.lookup(event.country());

        return repository.findByAccountNumber(event.accountNumber())
                .map(existing -> {
                    existing.mergeRepeatDetection(
                            event.postingReference(),
                            event.narration(),
                            event.balance(),
                            event.transferDate(),
                            now);
                    applyCountryConfig(existing, event.country(), countryConfig);
                    MemoAccount saved = repository.save(existing);
                    audit("MEMO_DETECTED_UPDATED", event, saved);
                    return saved;
                })
                .orElseGet(() -> {
                    MemoAccount created = MemoAccount.newlyDetected(
                            event.accountNumber(),
                            event.customerId(),
                            event.branchSol(),
                            event.currency(),
                            event.postingReference(),
                            event.narration(),
                            event.balance(),
                            event.transferDate(),
                            now);
                    applyCountryConfig(created, event.country(), countryConfig);
                    MemoAccount saved = repository.save(created);
                    audit("MEMO_DETECTED_CREATED", event, saved);
                    return saved;
                });
    }

    private void applyCountryConfig(MemoAccount account, String country, CountryConfig config) {
        account.applyCountryConfig(country, config.baseCurrency(), config.glWriteOffCode(), config.glRecoveryCode());
    }

    private void validate(MemoDetectedEvent event) {
        // System-wide-audit fix (2026-09-15): transferDate/balance weren't
        // checked here — only country is documented as nullable-and-tolerated
        // (see CountryConfigLookup). A null transferDate on a repeat
        // detection previously reached MemoAccount.mergeRepeatDetection's
        // transferDate.isBefore(...) call and threw an uncaught NPE instead
        // of degrading the same way an already-handled malformed event does.
        if (isBlank(event.accountNumber()) || isBlank(event.currency()) || isBlank(event.narration())
                || event.transferDate() == null || event.balance() == null) {
            auditLogger.record(new AuditEvent(
                    clock.instant(),
                    "system",
                    "MEMO_DETECTED_REJECTED",
                    "MemoDetectedEvent",
                    event.accountNumber(),
                    "memo-balance",
                    "Missing a required field (accountNumber, currency, narration, transferDate, or balance): " + event));
            throw new InvalidMemoDetectedEventException(
                    "MemoDetected event is missing a required field "
                            + "(accountNumber, currency, narration, transferDate, or balance)");
        }
    }

    private void audit(String action, MemoDetectedEvent event, MemoAccount account) {
        auditLogger.record(new AuditEvent(
                clock.instant(),
                "system",
                action,
                "MemoAccount",
                account.getAccountNumber(),
                "memo-balance",
                "source=" + event.source() + ", balance=" + account.getBalance()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
