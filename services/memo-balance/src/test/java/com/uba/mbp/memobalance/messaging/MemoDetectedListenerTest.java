package com.uba.mbp.memobalance.messaging;

import com.uba.mbp.memobalance.AbstractIntegrationTest;
import com.uba.mbp.memobalance.domain.MemoAccount;
import com.uba.mbp.memobalance.domain.MemoStatus;
import com.uba.mbp.memobalance.event.MemoDetectedEvent;
import com.uba.mbp.memobalance.repository.MemoAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Ticket 01: ingest a detected write-off and create a Memo record.
 * Ticket 02: de-duplicate repeat detections for an already-tracked account.
 * Seam under test: the Kafka consumer boundary (publish -> assert persisted state).
 */
class MemoDetectedListenerTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private MemoAccountRepository repository;

    @Test
    void createsANewMemoRecordFromAValidDetection() {
        String accountNumber = "ACC-" + System.nanoTime();
        MemoDetectedEvent event = new MemoDetectedEvent(
                accountNumber,
                "CUST-1",
                "SOL-001",
                "NGN",
                "TXN-REF-1",
                "written off per approval",
                new BigDecimal("15000.00"),
                Instant.parse("2026-01-10T09:00:00Z"),
                "write-off-detection-service", "NG");

        kafkaTemplate.send(MemoTopics.MEMO_DETECTED, accountNumber, event);

        await().atMost(15, SECONDS).untilAsserted(() -> {
            Optional<MemoAccount> saved = repository.findByAccountNumber(accountNumber);
            assertTrue(saved.isPresent());
            assertEquals(MemoStatus.IMPORTED_PENDING_REVIEW, saved.get().getStatus());
            assertEquals("CUST-1", saved.get().getCustomerId());
            assertEquals("SOL-001", saved.get().getBranchSol());
            assertEquals("NGN", saved.get().getCurrency());
            assertEquals("TXN-REF-1", saved.get().getPostingReference());
            assertEquals("written off per approval", saved.get().getNarration());
            assertEquals(0, new BigDecimal("15000.00").compareTo(saved.get().getBalance()));
            assertEquals(Instant.parse("2026-01-10T09:00:00Z"), saved.get().getTransferDate());
            assertEquals("NG", saved.get().getCountry());
            assertEquals("NGN", saved.get().getBaseCurrency());
            assertEquals("GL-WRITEOFF-NG", saved.get().getGlWriteOffCode());
            assertEquals("GL-RECOVERY-NG", saved.get().getGlRecoveryCode());
        });
    }

    @Test
    void rejectsADetectionMissingARequiredField() {
        String accountNumber = "ACC-" + System.nanoTime();
        MemoDetectedEvent invalidEvent = new MemoDetectedEvent(
                accountNumber,
                "CUST-1",
                "SOL-001",
                "", // missing currency
                "TXN-REF-1",
                "written off",
                new BigDecimal("100.00"),
                Instant.parse("2026-01-10T09:00:00Z"),
                "write-off-detection-service", "NG");

        kafkaTemplate.send(MemoTopics.MEMO_DETECTED, accountNumber, invalidEvent);

        // Give the listener a moment to process, then confirm nothing was persisted.
        await().pollDelay(3, SECONDS).atMost(10, SECONDS)
                .untilAsserted(() -> assertFalse(repository.findByAccountNumber(accountNumber).isPresent()));
    }

    @Test
    void updatesAnExistingRecordOnARepeatDetectionPreservingTheEarliestTransferDate() {
        String accountNumber = "ACC-" + System.nanoTime();
        Instant earlierTransferDate = Instant.parse("2026-01-01T00:00:00Z");
        Instant laterTransferDate = Instant.parse("2026-01-15T00:00:00Z");

        MemoDetectedEvent firstDetection = new MemoDetectedEvent(
                accountNumber, "CUST-1", "SOL-001", "NGN", "TXN-REF-1",
                "written off", new BigDecimal("15000.00"), earlierTransferDate,
                "write-off-detection-service", "NG");
        kafkaTemplate.send(MemoTopics.MEMO_DETECTED, accountNumber, firstDetection);

        await().atMost(15, SECONDS)
                .untilAsserted(() -> assertTrue(repository.findByAccountNumber(accountNumber).isPresent()));

        MemoDetectedEvent repeatDetection = new MemoDetectedEvent(
                accountNumber, "CUST-1", "SOL-001", "NGN", "TXN-REF-2",
                "written off (re-scan)", new BigDecimal("12000.00"), laterTransferDate,
                "write-off-detection-service", "NG");
        kafkaTemplate.send(MemoTopics.MEMO_DETECTED, accountNumber, repeatDetection);

        await().atMost(15, SECONDS).untilAsserted(() -> {
            Optional<MemoAccount> updated = repository.findByAccountNumber(accountNumber);
            assertTrue(updated.isPresent());
            assertEquals(0, new BigDecimal("12000.00").compareTo(updated.get().getBalance()));
            assertEquals("TXN-REF-2", updated.get().getPostingReference());
            assertEquals(earlierTransferDate, updated.get().getTransferDate());
        });

        assertEquals(1, repository.findAll().stream()
                .filter(a -> a.getAccountNumber().equals(accountNumber))
                .count());
    }
}
