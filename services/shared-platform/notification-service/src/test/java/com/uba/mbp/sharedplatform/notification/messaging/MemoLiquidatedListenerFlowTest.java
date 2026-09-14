package com.uba.mbp.sharedplatform.notification.messaging;

import com.icegreen.greenmail.util.GreenMailUtil;
import com.uba.mbp.sharedplatform.notification.AbstractIntegrationTest;
import com.uba.mbp.sharedplatform.notification.event.MemoLiquidatedEvent;
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
 * The real trigger for RFP §3.4's "memo balance updates following
 * liquidation" — a genuine `MemoLiquidatedEvent` on Kafka, not a fabricated
 * test-only event shape (see the plan's ADR-0019 rationale).
 */
class MemoLiquidatedListenerFlowTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, Object> testKafkaTemplate;

    @BeforeEach
    void resetInbox() {
        GREEN_MAIL.reset();
    }

    @Test
    void aLiquidationNotifiesRecoveryTeamByRealEmail() {
        String accountNumber = "ACC-" + System.nanoTime();
        MemoLiquidatedEvent event = new MemoLiquidatedEvent(accountNumber, Instant.parse("2026-09-14T09:00:00Z"));

        testKafkaTemplate.send(NotificationTopics.MEMO_LIQUIDATED, accountNumber, event);

        await().atMost(15, SECONDS).untilAsserted(() -> {
            MimeMessage[] received = GREEN_MAIL.getReceivedMessages();
            assertThat(received).hasSize(1);
            assertThat(received[0].getSubject()).contains(accountNumber);
            assertThat(GreenMailUtil.getBody(received[0])).contains(accountNumber);
            assertThat(received[0].getAllRecipients()[0].toString()).isEqualTo("recovery-team@uba.local");
        });
    }
}
