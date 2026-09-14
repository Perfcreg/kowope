package com.uba.mbp.sharedplatform.notification.messaging;

import com.uba.mbp.sharedplatform.notification.event.MemoLiquidatedEvent;
import com.uba.mbp.sharedplatform.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * RFP §3.4 "Memo balance updates following liquidation" — notifies
 * RECOVERY_TEAM (RBAC table: "Full privileges — verification, liquidation
 * tracking, reporting" is literally this). A send failure (unconfigured
 * group, SMTP down) is caught and never rethrown — one bad/unreachable
 * notification must not block the partition for every liquidation behind
 * it, the same principle memo-balance's own MemoDetectedListener follows.
 */
@Component
public class MemoLiquidatedListener {

    private static final Logger log = LoggerFactory.getLogger(MemoLiquidatedListener.class);
    private static final String RECIPIENT_GROUP = "RECOVERY_TEAM";

    private final NotificationService notificationService;

    public MemoLiquidatedListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = NotificationTopics.MEMO_LIQUIDATED,
            groupId = "notification-service",
            containerFactory = "memoLiquidatedContainerFactory")
    public void onMemoLiquidated(MemoLiquidatedEvent event) {
        try {
            notificationService.send(RECIPIENT_GROUP,
                    "Memo balance liquidated: " + event.accountNumber(),
                    "Account " + event.accountNumber() + " reached a zero memo balance at " + event.liquidatedAt() + ".");
        } catch (RuntimeException e) {
            log.warn("Failed to notify {} of liquidation for account {}: {}", RECIPIENT_GROUP, event.accountNumber(), e.getMessage());
        }
    }
}
