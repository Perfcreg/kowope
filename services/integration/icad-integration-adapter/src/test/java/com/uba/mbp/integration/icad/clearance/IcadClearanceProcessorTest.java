package com.uba.mbp.integration.icad.clearance;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.integration.icad.client.IcadClearanceStatus;
import com.uba.mbp.integration.icad.client.IcadClient;
import com.uba.mbp.integration.icad.client.IcadFetchResult;
import com.uba.mbp.integration.icad.client.IcadPushResult;
import com.uba.mbp.integration.icad.config.IcadProperties;
import com.uba.mbp.integration.icad.config.NotificationServiceProperties;
import com.uba.mbp.integration.icad.event.IcadClearanceOutcomeEvent;
import com.uba.mbp.integration.icad.notification.HttpNotificationClient;
import com.uba.mbp.integration.icad.notification.NotificationClient;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IcadClearanceProcessorTest {

    private static final Instant NOW = Instant.parse("2026-01-15T00:00:00Z");

    private IcadClient icadClient;
    private PendingClearanceStore pendingStore;
    private AuditLogger auditLogger;
    private NotificationClient notificationClient;
    private IcadProperties properties;
    private IcadClearanceProcessor processor;

    @BeforeEach
    void setUp() {
        icadClient = mock(IcadClient.class);
        pendingStore = new InMemoryPendingClearanceStore();
        auditLogger = mock(AuditLogger.class);
        notificationClient = mock(NotificationClient.class);
        properties = new IcadProperties();
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        processor = new IcadClearanceProcessor(icadClient, pendingStore, auditLogger, notificationClient, clock, properties);
    }

    @Test
    void requestingClearancePushesToIcadAndTracksItAsPending() {
        ClearanceRequest request = new ClearanceRequest(
                "ACC-001", "CUST-1", "Jane Doe", "12345678901", Instant.parse("2026-01-10T00:00:00Z"));
        when(icadClient.pushAccount(request)).thenReturn(new IcadPushResult("REF-001", IcadClearanceStatus.PENDING));

        ClearanceRequestAccepted outcome = processor.requestClearance("credit-admin-1", request);

        assertEquals("ACC-001", outcome.accountNumber());
        assertEquals("REF-001", outcome.icadReference());
        assertEquals(IcadClearanceStatus.PENDING, outcome.status());
        assertEquals(1, pendingStore.all().size());
        verify(auditLogger, times(1)).record(any());
    }

    @Test
    void pollingSkipsAStillPendingClearance() {
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1", NOW));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", IcadClearanceStatus.PENDING, null));

        List<IcadClearanceOutcomeEvent> resolved = processor.pollForOutcomes();

        assertTrue(resolved.isEmpty());
        assertEquals(1, pendingStore.all().size());
    }

    @Test
    void pollingReturnsAResolvedClearanceButLeavesItPendingUntilTheRouteConfirmsPublish() {
        // ADR-0015: removal is the route's responsibility, only after a
        // confirmed Kafka publish — pollForOutcomes must not remove it itself,
        // or a publish failure would silently lose the outcome.
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1", NOW));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", IcadClearanceStatus.CLEARED, null));

        List<IcadClearanceOutcomeEvent> resolved = processor.pollForOutcomes();

        assertEquals(1, resolved.size());
        assertEquals("ACC-001", resolved.get(0).accountNumber());
        assertEquals(IcadClearanceStatus.CLEARED, resolved.get(0).status());
        assertEquals("icad-integration-adapter", resolved.get(0).source());
        assertEquals(1, pendingStore.all().size());
    }

    @Test
    void pollingSurfacesTheRawDiscrepancyDetailFromIcad() {
        pendingStore.add(new PendingClearance("REF-002", "ACC-002", "CUST-2", NOW));
        when(icadClient.fetchAccount("REF-002")).thenReturn(new IcadFetchResult(
                "REF-002", IcadClearanceStatus.DISCREPANCY, "Name mismatch: BVN record shows a different customer"));

        List<IcadClearanceOutcomeEvent> resolved = processor.pollForOutcomes();

        assertEquals(IcadClearanceStatus.DISCREPANCY, resolved.get(0).status());
        assertEquals("Name mismatch: BVN record shows a different customer", resolved.get(0).detail());
    }

    @Test
    void pollingAcrossMultiplePendingClearancesOnlyResolvesTheOnesIcadHasAnsweredFor() {
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1", NOW));
        pendingStore.add(new PendingClearance("REF-002", "ACC-002", "CUST-2", NOW));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", IcadClearanceStatus.CLEARED, null));
        when(icadClient.fetchAccount("REF-002")).thenReturn(new IcadFetchResult("REF-002", IcadClearanceStatus.PENDING, null));

        List<IcadClearanceOutcomeEvent> resolved = processor.pollForOutcomes();

        assertEquals(1, resolved.size());
        assertEquals("ACC-001", resolved.get(0).accountNumber());
    }

    @Test
    void aFetchFailureForOnePendingClearanceAlertsAndSkipsItWithoutBlockingTheRest() {
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1", NOW));
        pendingStore.add(new PendingClearance("REF-002", "ACC-002", "CUST-2", NOW));
        when(icadClient.fetchAccount("REF-001")).thenThrow(new RuntimeException("ICAD timeout"));
        when(icadClient.fetchAccount("REF-002")).thenReturn(new IcadFetchResult("REF-002", IcadClearanceStatus.CLEARED, null));

        List<IcadClearanceOutcomeEvent> resolved = processor.pollForOutcomes();

        assertEquals(1, resolved.size());
        assertEquals("ACC-002", resolved.get(0).accountNumber());
        assertEquals(2, pendingStore.all().size());
        verify(notificationClient, times(1)).alertOperations(any(), any());

        // System-wide-audit fix (2026-09-15): a fetch failure is now audited,
        // not just ops-alerted — this class's own javadoc already claimed
        // "auditing every call" before this was actually true.
        verify(auditLogger, times(1)).record(argThat(event -> "ICAD_CLEARANCE_FETCH_FAILED".equals(event.action())
                && "ACC-001".equals(event.affectedRecordId())));
    }

    @Test
    void anUnresolvedFetchNeverAudits() {
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1", NOW));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", IcadClearanceStatus.PENDING, null));

        processor.pollForOutcomes();

        verify(auditLogger, never()).record(any());
    }

    @Test
    void aStillPendingClearanceOlderThanTheSlaEscalatesToCreditAdmin() {
        properties.setEscalationSla(Duration.ofHours(48));
        Instant pushedAt = NOW.minus(Duration.ofHours(49));
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1", pushedAt));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", IcadClearanceStatus.PENDING, null));

        processor.pollForOutcomes();

        verify(notificationClient, times(1)).escalate(eq("CREDIT_ADMIN"), any(), any());
        verify(auditLogger, times(1)).record(argThat(event -> "ICAD_CLEARANCE_ESCALATED".equals(event.action())
                && "ACC-001".equals(event.affectedRecordId())));
    }

    @Test
    void aStillPendingClearanceWithinTheSlaDoesNotEscalate() {
        properties.setEscalationSla(Duration.ofHours(48));
        Instant pushedAt = NOW.minus(Duration.ofHours(47));
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1", pushedAt));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", IcadClearanceStatus.PENDING, null));

        processor.pollForOutcomes();

        verify(notificationClient, never()).escalate(any(), any(), any());
        verify(auditLogger, never()).record(any());
    }

    @Test
    void anAlreadyEscalatedClearanceIsNotEscalatedAgainOnASubsequentPoll() {
        properties.setEscalationSla(Duration.ofHours(48));
        Instant pushedAt = NOW.minus(Duration.ofHours(49));
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1", pushedAt));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", IcadClearanceStatus.PENDING, null));

        processor.pollForOutcomes();
        processor.pollForOutcomes();

        verify(notificationClient, times(1)).escalate(eq("CREDIT_ADMIN"), any(), any());
    }

    @Test
    void aResolvedClearanceThatWasPreviouslyEscalatedCanEscalateAgainIfRePushedAndOverdue() {
        properties.setEscalationSla(Duration.ofHours(48));
        Instant pushedAt = NOW.minus(Duration.ofHours(49));
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1", pushedAt));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", IcadClearanceStatus.PENDING, null));
        processor.pollForOutcomes();
        verify(notificationClient, times(1)).escalate(eq("CREDIT_ADMIN"), any(), any());

        // Resolves — escalation tracking for this reference is cleared.
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", IcadClearanceStatus.CLEARED, null));
        processor.pollForOutcomes();

        // The same reference is re-pushed (e.g. a retry workflow) and is immediately overdue again.
        pendingStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1", pushedAt));
        when(icadClient.fetchAccount("REF-001")).thenReturn(new IcadFetchResult("REF-001", IcadClearanceStatus.PENDING, null));
        processor.pollForOutcomes();

        verify(notificationClient, times(2)).escalate(eq("CREDIT_ADMIN"), any(), any());
    }

    @Test
    void anUnreachableNotificationServiceDuringEscalationStillRecordsTheAuditEvent() throws Exception {
        // Security-axis re-verification: HttpNotificationClient.escalate() must
        // swallow the failure (ADR-0019), but that must not silently suppress
        // the ICAD_CLEARANCE_ESCALATED audit record too — real HttpNotificationClient
        // against an unreachable notification-service, not a mock, proves this
        // end-to-end rather than assuming it from the two halves separately.
        WireMockServer wireMock = new WireMockServer(0);
        wireMock.start();
        int deadPort = wireMock.port();
        wireMock.stop();

        CamelContext camelContext = new DefaultCamelContext();
        camelContext.start();
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        NotificationServiceProperties notificationProperties = new NotificationServiceProperties();
        notificationProperties.setBaseUrl("http://localhost:" + deadPort);
        NotificationClient realNotificationClient =
                new HttpNotificationClient(producerTemplate, JsonMapper.builder().build(), notificationProperties);

        AuditLogger realTestAuditLogger = mock(AuditLogger.class);
        PendingClearanceStore realTestStore = new InMemoryPendingClearanceStore();
        IcadProperties realTestProperties = new IcadProperties();
        realTestProperties.setEscalationSla(Duration.ofHours(48));
        Instant pushedAt = NOW.minus(Duration.ofHours(49));
        realTestStore.add(new PendingClearance("REF-001", "ACC-001", "CUST-1", pushedAt));

        IcadClient realTestIcadClient = mock(IcadClient.class);
        when(realTestIcadClient.fetchAccount("REF-001"))
                .thenReturn(new IcadFetchResult("REF-001", IcadClearanceStatus.PENDING, null));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        IcadClearanceProcessor realTestProcessor = new IcadClearanceProcessor(
                realTestIcadClient, realTestStore, realTestAuditLogger, realNotificationClient, clock, realTestProperties);

        try {
            assertDoesNotThrow(realTestProcessor::pollForOutcomes);

            verify(realTestAuditLogger, times(1)).record(argThat(event ->
                    "ICAD_CLEARANCE_ESCALATED".equals(event.action()) && "ACC-001".equals(event.affectedRecordId())));
        } finally {
            camelContext.stop();
        }
    }
}
