package com.uba.mbp.sharedplatform.notification.messaging;

import com.uba.mbp.sharedplatform.notification.event.IcadClearanceOutcomeEvent;
import com.uba.mbp.sharedplatform.notification.event.IcadClearanceStatus;
import com.uba.mbp.sharedplatform.notification.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * RFP §3.4/§4.6 "issue notifications to stakeholders if discrepancies arise
 * during ICAD clearance processes" + spec User Story 10 ("a Credit Admin
 * user... receive a notification when a loan-status discrepancy is
 * escalated"). Only DISCREPANCY/FAILED outcomes are notification-worthy —
 * CLEARED is the successful, silent path; UNKNOWN (ADR-0014's "unrecognized
 * ICAD value" case) is surfaced too since it needs a human look, same as a
 * real discrepancy.
 */
@Component
public class IcadClearanceOutcomeListener {

    private static final Logger log = LoggerFactory.getLogger(IcadClearanceOutcomeListener.class);
    private static final String RECIPIENT_GROUP = "CREDIT_ADMIN";
    private static final Set<IcadClearanceStatus> NOTIFICATION_WORTHY =
            Set.of(IcadClearanceStatus.DISCREPANCY, IcadClearanceStatus.FAILED, IcadClearanceStatus.UNKNOWN);

    private final NotificationService notificationService;

    public IcadClearanceOutcomeListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = NotificationTopics.ICAD_CLEARANCE_OUTCOME,
            groupId = "notification-service",
            containerFactory = "icadClearanceOutcomeContainerFactory")
    public void onIcadClearanceOutcome(IcadClearanceOutcomeEvent event) {
        if (!NOTIFICATION_WORTHY.contains(event.status())) {
            return;
        }
        try {
            notificationService.send(RECIPIENT_GROUP,
                    "ICAD clearance " + event.status() + ": " + event.accountNumber(),
                    "Account " + event.accountNumber() + " (customer " + event.customerId() + ", ICAD reference "
                            + event.icadReference() + ") resolved as " + event.status() + ". Detail: " + event.detail());
        } catch (RuntimeException e) {
            log.warn("Failed to notify {} of ICAD outcome for account {}: {}", RECIPIENT_GROUP, event.accountNumber(), e.getMessage());
        }
    }
}
