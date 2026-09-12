package com.uba.mbp.integration.visionetl.scan;

import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.visionetl.event.VisionBalanceSyncedEvent;
import com.uba.mbp.integration.visionetl.fineract.FineractAccountBalance;
import com.uba.mbp.integration.visionetl.fineract.FineractClient;
import com.uba.mbp.integration.visionetl.notification.NotificationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisionBalanceScannerTest {

    private FineractClient fineractClient;
    private NotificationClient notificationClient;
    private VisionBalanceScanner scanner;

    @BeforeEach
    void setUp() {
        fineractClient = mock(FineractClient.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        notificationClient = mock(NotificationClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);
        scanner = new VisionBalanceScanner(fineractClient, auditLogger, notificationClient, clock);
    }

    @Test
    void publishesOneEventPerAccountAcrossEveryActiveClient() {
        when(fineractClient.listActiveClientIds()).thenReturn(List.of(4L, 5L));
        when(fineractClient.listSavingsAccountBalances(4L)).thenReturn(
                List.of(new FineractAccountBalance("000000004", new BigDecimal("6125000"))));
        when(fineractClient.listSavingsAccountBalances(5L)).thenReturn(
                List.of(new FineractAccountBalance("000000005", new BigDecimal("300000"))));

        List<VisionBalanceSyncedEvent> synced = scanner.scan();

        assertEquals(2, synced.size());
        assertTrue(synced.stream().anyMatch(e ->
                e.accountNumber().equals("000000004") && e.balance().compareTo(new BigDecimal("6125000")) == 0));
        assertTrue(synced.stream().anyMatch(e ->
                e.accountNumber().equals("000000005") && e.balance().compareTo(new BigDecimal("300000")) == 0));
        assertEquals(Instant.parse("2026-01-15T00:00:00Z"), synced.get(0).syncedAt());
    }

    @Test
    void producesNothingWhenNoActiveClientsExist() {
        when(fineractClient.listActiveClientIds()).thenReturn(List.of());

        assertTrue(scanner.scan().isEmpty());
    }

    @Test
    void producesNothingForAClientWithNoSavingsAccounts() {
        when(fineractClient.listActiveClientIds()).thenReturn(List.of(4L));
        when(fineractClient.listSavingsAccountBalances(4L)).thenReturn(List.of());

        assertTrue(scanner.scan().isEmpty());
    }

    @Test
    void skipsAndAlertsOnAnAccountWithNoAccountNumberInsteadOfPublishingAnEmptyKey() {
        when(fineractClient.listActiveClientIds()).thenReturn(List.of(4L));
        when(fineractClient.listSavingsAccountBalances(4L)).thenReturn(
                List.of(new FineractAccountBalance("", new BigDecimal("6125000"))));

        List<VisionBalanceSyncedEvent> synced = scanner.scan();

        assertTrue(synced.isEmpty());
        verify(notificationClient, times(1)).alertOperations(any(), any());
    }
}
