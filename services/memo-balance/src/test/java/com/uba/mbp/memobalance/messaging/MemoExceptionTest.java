package com.uba.mbp.memobalance.messaging;

import com.uba.mbp.memobalance.AbstractIntegrationTest;
import com.uba.mbp.memobalance.domain.AdjustmentType;
import com.uba.mbp.memobalance.domain.ExceptionType;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.domain.MemoStatus;
import com.uba.mbp.memobalance.event.VisionBalanceSyncedEvent;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import com.uba.mbp.memobalance.repository.MemoExceptionRepository;
import com.uba.mbp.memobalance.service.MemoBalanceAdjustmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 06: detect and flag balance/payment exceptions (unallocated payments,
 * Vision discrepancies). Seams under test: the adjustment service's own
 * behaviour and the Kafka consumer boundary for VisionBalanceSynced.
 */
class MemoExceptionTest extends AbstractIntegrationTest {

    @Autowired
    private MemoAccountRepository accountRepository;

    @Autowired
    private MemoExceptionRepository exceptionRepository;

    @Autowired
    private MemoBalanceAdjustmentService adjustmentService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private MemoAccount seedAccount(String accountNumber, BigDecimal balance) {
        MemoAccount account = MemoAccount.newlyDetected(
                accountNumber, "CUST-1", "SOL-001", "NGN", "TXN-REF-1",
                "written off", balance, Instant.parse("2026-01-01T00:00:00Z"), Instant.now());
        return accountRepository.save(account);
    }

    @Test
    void aPaymentExceedingTheBalanceLiquidatesTheAccountAndFlagsTheExcessAsUnallocated() {
        String accountNumber = "ACC-" + System.nanoTime();
        seedAccount(accountNumber, new BigDecimal("100.00"));

        adjustmentService.adjust(accountNumber, AdjustmentType.PARTIAL_PAYMENT, new BigDecimal("150.00"), "user-1");

        MemoAccount updated = accountRepository.findByAccountNumber(accountNumber).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(updated.getBalance()));
        assertEquals(MemoStatus.LIQUIDATED, updated.getStatus());

        var exceptions = exceptionRepository.findByMemoAccountId(updated.getId());
        assertEquals(1, exceptions.size());
        assertEquals(ExceptionType.UNALLOCATED_PAYMENT, exceptions.get(0).getType());
        assertTrue(exceptions.get(0).getDetail().contains("50.00"));
    }

    @Test
    void aVisionBalanceDisagreeingWithTheCalculatedBalanceRaisesADiscrepancy() {
        String accountNumber = "ACC-" + System.nanoTime();
        MemoAccount account = seedAccount(accountNumber, new BigDecimal("500.00"));

        kafkaTemplate.send(MemoTopics.VISION_BALANCE_SYNCED, accountNumber,
                new VisionBalanceSyncedEvent(accountNumber, new BigDecimal("450.00"), Instant.now()));

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            var exceptions = exceptionRepository.findByMemoAccountId(account.getId());
            assertEquals(1, exceptions.size());
            assertEquals(ExceptionType.BALANCE_DISCREPANCY, exceptions.get(0).getType());
        });
    }

    @Test
    void aVisionBalanceMatchingTheCalculatedBalanceRaisesNoDiscrepancy() {
        String accountNumber = "ACC-" + System.nanoTime();
        MemoAccount account = seedAccount(accountNumber, new BigDecimal("500.00"));

        kafkaTemplate.send(MemoTopics.VISION_BALANCE_SYNCED, accountNumber,
                new VisionBalanceSyncedEvent(accountNumber, new BigDecimal("500.00"), Instant.now()));

        // No positive event to await on absence of; give the consumer a moment then assert nothing landed.
        await().pollDelay(3, TimeUnit.SECONDS).atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
                assertEquals(0, exceptionRepository.findByMemoAccountId(account.getId()).size()));
    }
}
