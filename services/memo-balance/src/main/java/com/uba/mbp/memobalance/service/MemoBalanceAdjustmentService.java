package com.uba.mbp.memobalance.service;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.memobalance.domain.AdjustmentType;
import com.uba.mbp.memobalance.domain.BalanceAdjustment;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.event.MemoBalanceAdjustedEvent;
import com.uba.mbp.memobalance.event.MemoLiquidatedEvent;
import com.uba.mbp.memobalance.exception.InvalidAdjustmentException;
import com.uba.mbp.memobalance.exception.MemoAccountNotFoundException;
import com.uba.mbp.memobalance.messaging.MemoTopics;
import com.uba.mbp.memobalance.repository.BalanceAdjustmentRepository;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;

/**
 * Ticket 04: recalculates a Memo account's balance for a partial payment or an
 * approved write-off, records the adjustment, and publishes {@link MemoBalanceAdjustedEvent}.
 * Delegating to Vision's own balance algorithm (RFP §3.1) is still deferred —
 * this service does the arithmetic itself — but reconciling a Vision discrepancy
 * and flagging an unallocated payment (ticket 06) both live here now.
 */
@Service
public class MemoBalanceAdjustmentService {

    private final MemoAccountRepository memoAccountRepository;
    private final BalanceAdjustmentRepository balanceAdjustmentRepository;
    private final MemoExceptionService exceptionService;
    private final AuditLogger auditLogger;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Clock clock;

    public MemoBalanceAdjustmentService(
            MemoAccountRepository memoAccountRepository,
            BalanceAdjustmentRepository balanceAdjustmentRepository,
            MemoExceptionService exceptionService,
            AuditLogger auditLogger,
            KafkaTemplate<String, Object> kafkaTemplate,
            Clock clock) {
        this.memoAccountRepository = memoAccountRepository;
        this.balanceAdjustmentRepository = balanceAdjustmentRepository;
        this.exceptionService = exceptionService;
        this.auditLogger = auditLogger;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Transactional
    public MemoAccount adjust(String accountNumber, AdjustmentType type, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAdjustmentException("Adjustment amount must be positive: " + amount);
        }

        MemoAccount account = memoAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new MemoAccountNotFoundException(accountNumber));

        BigDecimal previousBalance = account.getBalance();

        // Ticket 06: a payment larger than the outstanding balance still liquidates
        // the account — the excess is flagged as unallocated, not rejected outright.
        BigDecimal newBalance;
        if (amount.compareTo(previousBalance) > 0) {
            BigDecimal unallocated = amount.subtract(previousBalance);
            newBalance = BigDecimal.ZERO.setScale(previousBalance.scale());
            exceptionService.raiseUnallocatedPayment(account, unallocated);
        } else {
            newBalance = previousBalance.subtract(amount);
        }

        var now = clock.instant();

        account.applyAdjustedBalance(newBalance, now);
        memoAccountRepository.save(account);

        balanceAdjustmentRepository.save(
                BalanceAdjustment.record(account.getId(), type, previousBalance, newBalance, now));

        kafkaTemplate.send(MemoTopics.MEMO_BALANCE_ADJUSTED, accountNumber,
                new MemoBalanceAdjustedEvent(accountNumber, type, previousBalance, newBalance, now));

        auditLogger.record(new AuditEvent(
                now, "system", "MEMO_BALANCE_ADJUSTED", "MemoAccount", accountNumber,
                "memo-balance", type + ": " + previousBalance + " -> " + newBalance));

        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            kafkaTemplate.send(MemoTopics.MEMO_LIQUIDATED, accountNumber,
                    new MemoLiquidatedEvent(accountNumber, now));
            auditLogger.record(new AuditEvent(
                    now, "system", "MEMO_LIQUIDATED", "MemoAccount", accountNumber,
                    "memo-balance", "Balance reached zero"));
        }

        return account;
    }
}
