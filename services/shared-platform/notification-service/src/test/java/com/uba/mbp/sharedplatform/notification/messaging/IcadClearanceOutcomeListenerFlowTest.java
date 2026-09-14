package com.uba.mbp.sharedplatform.notification.messaging;

import com.uba.mbp.sharedplatform.notification.AbstractIntegrationTest;
import com.uba.mbp.sharedplatform.notification.event.IcadClearanceOutcomeEvent;
import com.uba.mbp.sharedplatform.notification.event.IcadClearanceStatus;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * RFP §3.4/§4.6 + spec User Story 10: a real ICAD clearance discrepancy
 * notifies CREDIT_ADMIN; a clean CLEARED outcome notifies no one.
 */
class IcadClearanceOutcomeListenerFlowTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> testKafkaTemplate;

    @BeforeEach
    void resetInbox() {
        GREEN_MAIL.reset();
    }

    @Test
    void aDiscrepancyNotifiesCreditAdminByRealEmail() {
        String accountNumber = "ACC-" + System.nanoTime();
        IcadClearanceOutcomeEvent event = new IcadClearanceOutcomeEvent(
                accountNumber, "CUST-1", "ICAD-REF-1", IcadClearanceStatus.DISCREPANCY,
                "Name mismatch", Instant.parse("2026-09-14T09:00:00Z"), "icad-integration-adapter");

        testKafkaTemplate.send(NotificationTopics.ICAD_CLEARANCE_OUTCOME, accountNumber, event);

        await().atMost(15, SECONDS).untilAsserted(() -> {
            MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
            assertThat(received).hasSize(1);
            assertThat(received[0].getSubject()).contains(accountNumber).contains("DISCREPANCY");
            assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("credit-admin@uba.local");
        });
    }

    @Test
    void aClearedOutcomeNeverSendsANotification() {
        String accountNumber = "ACC-" + System.nanoTime();
        IcadClearanceOutcomeEvent event = new IcadClearanceOutcomeEvent(
                accountNumber, "CUST-1", "ICAD-REF-2", IcadClearanceStatus.CLEARED,
                null, Instant.parse("2026-09-14T09:00:00Z"), "icad-integration-adapter");

        testKafkaTemplate.send(NotificationTopics.ICAD_CLEARANCE_OUTCOME, accountNumber, event);

        // Give the listener a moment to (not) process, then confirm nothing was sent.
        await().pollDelay(3, SECONDS).atMost(10, SECONDS)
                .untilAsserted(() -> assertThat(GREEN_MAIL.getReceivedMessages()).isEmpty());
    }
}
