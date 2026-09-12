package com.uba.mbp.integration.icad.clearance;

import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.icad.client.IcadClient;
import com.uba.mbp.integration.icad.client.IcadFetchResult;
import com.uba.mbp.integration.icad.client.IcadPushResult;
import com.uba.mbp.integration.icad.event.IcadClearanceOutcomeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IcadClearanceProcessorTest {

    private IcadClient icadClient;
    private PendingClearanceStore pendingStore;
    private AuditLogger auditLogger;
    private IcadClearanceProcessor processor;

    @BeforeEach
    void setUp() {
        icadClient = mock(IcadClient.class);
        pendingStore = new PendingClearanceStore();
        auditLogger = mock(AuditLogger.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);
        processor = new IcadClearanceProcessor(icadClient, pendingStore, auditLogger, clock);
    }

    @Test
    void requestingClearancePushesToIcadAndTracksItAsPending() {
        ClearanceRequest request = new ClearanceRequest(
                "ACC-001", "CUST-1", "Jane Doe", "12345678901", Instant.parse("2026-01-10T00:00:00Z"));
        when(icadClient.pushAccount(request)).thenReturn(new IcadPushResult("REF-001", "PENDING"));

        ClearanceRequestOutcome outcome = processor.requestClearance("credit-admin-1", request);

        assertEquals("ACC-001", outcome.accountNumber());
        assertEquals("REF-001", outcome.icadReference());
        assertEquals("PENDING", outcome.status());
        assertEquals(1, pendingStore.all().size());
        verify(auditLogger, times(1)).record(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void pollingSkipsAStillPendingClearance() {
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1"));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", "PENDING", null));

        List<IcadClearanceOutcomeEvent> resolved = processor.pollForOutcomes();

        assertTrue(resolved.isEmpty());
        assertEquals(1, pendingStore.all().size());
    }

    @Test
    void pollingPublishesAndRemovesAResolvedClearance() {
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1"));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", "CLEARED", null));

        List<IcadClearanceOutcomeEvent> resolved = processor.pollForOutcomes();

        assertEquals(1, resolved.size());
        assertEquals("ACC-001", resolved.get(0).accountNumber());
        assertEquals("CLEARED", resolved.get(0).status());
        assertEquals("icad-integration-adapter", resolved.get(0).source());
        assertTrue(pendingStore.all().isEmpty());
    }

    @Test
    void pollingSurfacesTheRawDiscrepancyDetailFromIcad() {
        pendingStore.add(new PendingClearance("REF-002", "ACC-002", "CUST-2"));
        when(icadClient.fetchAccount("REF-002"))
                .thenReturn(new IcadFetchResult("REF-002", "DISCREPANCY", "Name mismatch: BVN record shows a different customer"));

        List<IcadClearanceOutcomeEvent> resolved = processor.pollForOutcomes();

        assertEquals("DISCREPANCY", resolved.get(0).status());
        assertEquals("Name mismatch: BVN record shows a different customer", resolved.get(0).detail());
    }

    @Test
    void pollingAcrossMultiplePendingClearancesOnlyResolvesTheOnesIcadHasAnsweredFor() {
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1"));
        pendingStore.add(new PendingClearance("REF-002", "ACC-002", "CUST-2"));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", "CLEARED", null));
        when(icadClient.fetchAccount("REF-002")).thenReturn(new IcadFetchResult("REF-002", "PENDING", null));

        List<IcadClearanceOutcomeEvent> resolved = processor.pollForOutcomes();

        assertEquals(1, resolved.size());
        assertEquals("ACC-001", resolved.get(0).accountNumber());
        assertEquals(1, pendingStore.all().size());
    }
}
