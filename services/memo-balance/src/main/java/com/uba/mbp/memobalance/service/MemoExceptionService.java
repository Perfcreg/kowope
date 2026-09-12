package com.uba.mbp.memobalance.service;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.memobalance.domain.ExceptionType;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.domain.MemoException;
import com.uba.mbp.memobalance.exception.MemoAccountNotFoundException;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import com.uba.mbp.memobalance.repository.MemoExceptionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

/**
 * Ticket 06: flags balance/payment exceptions for Transaction Services.
 * Unallocated payments are raised inline by {@link MemoBalanceAdjustmentService};
 * balance discrepancies are raised here from Vision sync events.
 */
@Service
public class MemoExceptionService {

    private final MemoAccountRepository memoAccountRepository;
    private final MemoExceptionRepository repository;
    private final AuditLogger auditLogger;
    private final Clock clock;

    public MemoExceptionService(
            MemoAccountRepository memoAccountRepository,
            MemoExceptionRepository repository,
            AuditLogger auditLogger,
            Clock clock) {
        this.memoAccountRepository = memoAccountRepository;
        this.repository = repository;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    /** Ticket 06 follow-up: exceptions were persisted but had no way to be seen — this is that surface. */
    public List<MemoException> list(String accountNumber) {
        var account = memoAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new MemoAccountNotFoundException(accountNumber));
        return repository.findByMemoAccountId(account.getId());
    }

    public void raiseUnallocatedPayment(MemoAccount account, BigDecimal unallocatedAmount, String actor) {
        raise(account, ExceptionType.UNALLOCATED_PAYMENT,
                "Payment exceeded the outstanding balance by " + unallocatedAmount, actor);
    }

    public void checkForVisionDiscrepancy(MemoAccount account, BigDecimal visionBalance) {
        if (account.getBalance().compareTo(visionBalance) != 0) {
            // Event-driven, not human-initiated — "system" is the correct actor here.
            raise(account, ExceptionType.BALANCE_DISCREPANCY,
                    "memo-balance calculated " + account.getBalance() + " but Vision reported " + visionBalance,
                    "system");
        }
    }

    private void raise(MemoAccount account, ExceptionType type, String detail, String actor) {
        var now = clock.instant();
        repository.save(MemoException.raise(account.getId(), type, detail, now));
        auditLogger.record(new AuditEvent(
                now, actor, "MEMO_EXCEPTION_RAISED", "MemoAccount", account.getAccountNumber(),
                "memo-balance", type + ": " + detail));
    }
}
