package com.uba.mbp.integration.writeoffdetection.scan;

import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.writeoffdetection.config.FineractProperties;
import com.uba.mbp.integration.writeoffdetection.event.MemoDetectedEvent;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractClient;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractClientSummary;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractSavingsAccount;
import com.uba.mbp.integration.writeoffdetection.fineract.FineractSavingsTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The RFP §3.13(bis) detection rule, tested at its own seam — no Camel, no
 * HTTP, no Kafka. WriteOffDetectionRoute only orchestrates calling this.
 */
class WriteOffScannerTest {

    private FineractClient fineractClient;
    private WriteOffScanner scanner;

    @BeforeEach
    void setUp() {
        fineractClient = mock(FineractClient.class);
        FineractProperties properties = new FineractProperties();
        AuditLogger auditLogger = mock(AuditLogger.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);
        scanner = new WriteOffScanner(fineractClient, properties, auditLogger, clock);
    }

    private void givenClientWithTransaction(String note, BigDecimal amount) {
        when(fineractClient.listActiveClients()).thenReturn(
                List.of(new FineractClientSummary(4L, "Emeka Nwosu", "Lagos Island Branch")));
        when(fineractClient.listSavingsAccountIds(4L)).thenReturn(List.of(10L));
        when(fineractClient.getSavingsAccountWithTransactions(10L)).thenReturn(
                new FineractSavingsAccount(10L, "000000004", "NGN",
                        List.of(new FineractSavingsTransaction(98L, note, amount, LocalDate.of(2026, 1, 10)))));
    }

    @Test
    void detectsAnExactPhraseMatch() {
        givenClientWithTransaction("Account written off per approval", new BigDecimal("15000.00"));

        List<MemoDetectedEvent> detected = scanner.scan();

        assertEquals(1, detected.size());
        MemoDetectedEvent event = detected.get(0);
        assertEquals("000000004", event.accountNumber());
        assertEquals("4", event.customerId());
        assertEquals("Lagos Island Branch", event.branchSol());
        assertEquals("NGN", event.currency());
        assertEquals("FINERACT-TXN-98", event.postingReference());
        assertEquals("Account written off per approval", event.narration());
        assertEquals(0, new BigDecimal("15000.00").compareTo(event.balance()));
        assertEquals("write-off-detection-service", event.source());
        assertEquals("NG", event.country());
    }

    @Test
    void matchesCaseInsensitivelyAndAcrossHyphenatedVariants() {
        givenClientWithTransaction("WRITTEN-OFF by credit admin", new BigDecimal("500.00"));

        assertEquals(1, scanner.scan().size());
    }

    @Test
    void matchesTheWriteOffVariantWithoutTheTenSuffix() {
        // RFP §3.13(bis): configurable variants explicitly include "write off", not just "written off".
        givenClientWithTransaction("write off approved by credit admin", new BigDecimal("500.00"));

        assertEquals(1, scanner.scan().size());
    }

    @Test
    void ignoresTransactionsThatDoNotMentionWriteOff() {
        givenClientWithTransaction("Salary deposit", new BigDecimal("500.00"));

        assertTrue(scanner.scan().isEmpty());
    }

    @Test
    void ignoresTransactionsWithNoNoteAtAll() {
        when(fineractClient.listActiveClients()).thenReturn(
                List.of(new FineractClientSummary(4L, "Emeka Nwosu", "Lagos Island Branch")));
        when(fineractClient.listSavingsAccountIds(4L)).thenReturn(List.of(10L));
        when(fineractClient.getSavingsAccountWithTransactions(10L)).thenReturn(
                new FineractSavingsAccount(10L, "000000004", "NGN",
                        List.of(new FineractSavingsTransaction(98L, null, new BigDecimal("500.00"), LocalDate.of(2026, 1, 10)))));

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
                new FineractSavingsAccount(10L, "000000004", "NGN",
                        List.of(new FineractSavingsTransaction(1L, "written off", BigDecimal.TEN, LocalDate.of(2026, 1, 1)))));
        when(fineractClient.getSavingsAccountWithTransactions(20L)).thenReturn(
                new FineractSavingsAccount(20L, "000000005", "NGN",
                        List.of(new FineractSavingsTransaction(2L, "write off approved", BigDecimal.ONE, LocalDate.of(2026, 1, 2)))));

        List<MemoDetectedEvent> detected = scanner.scan();

        assertEquals(2, detected.size());
    }
}
