package com.uba.mbp.memobalance.service;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.memobalance.domain.AdjustmentType;
import com.uba.mbp.memobalance.domain.BalanceAdjustment;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.event.MemoBalanceAdjustedEvent;
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
 * Delegating to Vision's own balance algorithm (RFP §3.1) and reconciling any
 * discrepancy is ticket 06's job once the integration context exists — this
 * service does the arithmetic itself for now.
 */
@Service
public class MemoBalanceAdjustmentService {

    private final MemoAccountRepository memoAccountRepository;
    private final BalanceAdjustmentRepository balanceAdjustmentRepository;
    private final AuditLogger auditLogger;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Clock clock;

    public MemoBalanceAdjustmentService(
            MemoAccountRepository memoAccountRepository,
            BalanceAdjustmentRepository balanceAdjustmentRepository,
            AuditLogger auditLogger,
            KafkaTemplate<String, Object> kafkaTemplate,
            Clock clock) {
        this.memoAccountRepository = memoAccountRepository;
        this.balanceAdjustmentRepository = balanceAdjustmentRepository;
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
        if (amount.compareTo(previousBalance) > 0) {
            throw new InvalidAdjustmentException(
                    "Adjustment amount " + amount + " exceeds current balance " + previousBalance);
        }

        BigDecimal newBalance = previousBalance.subtract(amount);
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

        return account;
    }
}
