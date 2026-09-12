package com.uba.mbp.integration.writeoffdetection.scan;

import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.writeoffdetection.config.WriteOffDetectionProperties;
import com.uba.mbp.integration.writeoffdetection.event.MemoDetectedEvent;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractClient;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractClientSummary;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractSavingsAccount;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractSavingsTransaction;
import com.uba.mbp.integration.writeoffdetection.notification.NotificationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The RFP §3.13(bis) detection rule, tested at its own seam — no Camel, no
 * HTTP, no Kafka. WriteOffDetectionRoute only orchestrates calling this.
 */
class WriteOffScannerTest {

    private FineractClient fineractClient;
    private DetectedWriteOffStore detectedStore;
    private NotificationClient notificationClient;
    private WriteOffScanner scanner;

    @BeforeEach
    void setUp() {
        fineractClient = mock(FineractClient.class);
        WriteOffDetectionProperties properties = new WriteOffDetectionProperties();
        detectedStore = new InMemoryDetectedWriteOffStore();
        AuditLogger auditLogger = mock(AuditLogger.class);
        notificationClient = mock(NotificationClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);
        scanner = new WriteOffScanner(fineractClient, properties, detectedStore, auditLogger, notificationClient, clock);
    }

    private void givenClientWithTransaction(long transactionId, String note, BigDecimal accountBalance, LocalDate date) {
        when(fineractClient.listActiveClients()).thenReturn(
                List.of(new FineractClientSummary(4L, "Emeka Nwosu", "Abuja Branch")));
        when(fineractClient.listSavingsAccountIds(4L)).thenReturn(List.of(10L));
        when(fineractClient.getSavingsAccountWithTransactions(10L)).thenReturn(
                new FineractSavingsAccount(10L, "000000004", "NGN", accountBalance,
                        List.of(new FineractSavingsTransaction(transactionId, note, new BigDecimal("999999.99"), date))));
    }

    @Test
    void detectsAnExactPhraseMatchAndPublishesTheAccountBalanceNotTheTransactionAmount() {
        givenClientWithTransaction(98L, "Account written off per approval", new BigDecimal("15000.00"), LocalDate.of(2026, 1, 10));

        List<MemoDetectedEvent> detected = scanner.scan();

        assertEquals(1, detected.size());
        MemoDetectedEvent event = detected.get(0);
        assertEquals("000000004", event.accountNumber());
        assertEquals("4", event.customerId());
        assertEquals("Abuja Branch", event.branchSol());
        assertEquals("NGN", event.currency());
        assertEquals("98", event.postingReference());
        assertEquals("Account written off per approval", event.narration());
        // The account's real balance (15000.00), not the transaction's own amount (999999.99).
        assertEquals(0, new BigDecimal("15000.00").compareTo(event.balance()));
        assertEquals("write-off-detection-service", event.source());
        // Country is deliberately null — reference-data-config (RFP §3.14) owns that, not this adapter.
        assertNull(event.country());
    }

    @Test
    void matchesCaseInsensitivelyAndAcrossHyphenatedVariants() {
        givenClientWithTransaction(1L, "WRITTEN-OFF by credit admin", new BigDecimal("500.00"), LocalDate.of(2026, 1, 10));

        assertEquals(1, scanner.scan().size());
    }

    @Test
    void matchesTheWriteOffVariantWithoutTheTenSuffix() {
        // RFP §3.13(bis): configurable variants explicitly include "write off", not just "written off".
        givenClientWithTransaction(1L, "write off approved by credit admin", new BigDecimal("500.00"), LocalDate.of(2026, 1, 10));

        assertEquals(1, scanner.scan().size());
    }

    @Test
    void ignoresTransactionsThatDoNotMentionWriteOff() {
        givenClientWithTransaction(1L, "Salary deposit", new BigDecimal("500.00"), LocalDate.of(2026, 1, 10));

        assertTrue(scanner.scan().isEmpty());
    }

    @Test
    void ignoresTransactionsWithNoNoteAtAll() {
        givenClientWithTransaction(1L, null, new BigDecimal("500.00"), LocalDate.of(2026, 1, 10));

        assertTrue(scanner.scan().isEmpty());
    }

    @Test
    void scansEveryAccountAcrossEveryActiveClient() {
        when(fineractClient.listActiveClients()).thenReturn(List.of(
                new FineractClientSummary(4L, "Emeka Nwosu", "Lagos Island Branch"),
                new FineractClientSummary(5L, "Blessing Okafor", "Abuja Branch")));
        when(fineractClient.listSavingsAccountIds(4L)).thenReturn(List.of(10L));
        when(fineractClient.listSavingsAccountIds(5L)).thenReturn(List.of(20L));
        when(fineractClient.getSavingsAccountWithTransactions(10L)).thenReturn(
                new FineractSavingsAccount(10L, "000000004", "NGN", BigDecimal.TEN,
                        List.of(new FineractSavingsTransaction(1L, "written off", BigDecimal.TEN, LocalDate.of(2026, 1, 1)))));
        when(fineractClient.getSavingsAccountWithTransactions(20L)).thenReturn(
                new FineractSavingsAccount(20L, "000000005", "NGN", BigDecimal.ONE,
                        List.of(new FineractSavingsTransaction(2L, "write off approved", BigDecimal.ONE, LocalDate.of(2026, 1, 2)))));

        List<MemoDetectedEvent> detected = scanner.scan();

        assertEquals(2, detected.size());
    }

    @Test
    void aTransactionAlreadyDetectedInAnEarlierScanIsNotRePublishedOrReaudited() {
        givenClientWithTransaction(98L, "Account written off per approval", new BigDecimal("15000.00"), LocalDate.of(2026, 1, 10));

        List<MemoDetectedEvent> first = scanner.scan();
        List<MemoDetectedEvent> second = scanner.scan();

        assertEquals(1, first.size());
        assertTrue(second.isEmpty(), "A previously-detected write-off must not be re-detected on the next scan cycle");
    }

    @Test
    void aTransactionWithNoTransferDateIsSkippedAndAlertedRatherThanFabricatingNow() {
        givenClientWithTransaction(98L, "Account written off per approval", new BigDecimal("15000.00"), null);

        List<MemoDetectedEvent> detected = scanner.scan();

        assertTrue(detected.isEmpty());
        verify(notificationClient, times(1)).alertOperations(any(), any());
    }
}
