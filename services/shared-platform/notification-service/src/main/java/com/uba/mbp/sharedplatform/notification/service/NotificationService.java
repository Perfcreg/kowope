package com.uba.mbp.sharedplatform.notification.service;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.sharedplatform.notification.config.NotificationRecipientsProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The one place a notification actually gets sent (RFP §3.4, spec User
 * Stories 7/9/11) — both {@code NotificationController} (the synchronous
 * REST seam any context calls) and the Kafka listeners (async triggers off
 * real domain events) go through this single method, so recipient
 * resolution, delivery, and auditing happen exactly once, the same way
 * regardless of entry point.
 */
@Service
public class NotificationService {

    private static final String SOURCE = "notification-service";

    private final JavaMailSender mailSender;
    private final NotificationRecipientsProperties recipientsProperties;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final String fromAddress;

    public NotificationService(JavaMailSender mailSender, NotificationRecipientsProperties recipientsProperties,
                                AuditLogger auditLogger, Clock clock,
                                @Value("${notification.from-address}") String fromAddress) {
        this.mailSender = mailSender;
        this.recipientsProperties = recipientsProperties;
        this.auditLogger = auditLogger;
        this.clock = clock;
        this.fromAddress = fromAddress;
    }

    /**
     * Resolves {@code recipientGroup} against the configured group table (a
     * real HR/staff-directory lookup doesn't exist anywhere in this repo yet
     * — see ADR-0019), sends via real SMTP, and audits the outcome either
     * way. An unresolved group is a hard failure, never a silent no-op.
     */
    public NotificationResult send(String recipientGroup, String subject, String body) {
        if (isBlank(recipientGroup) || isBlank(subject) || isBlank(body)) {
            String group = isBlank(recipientGroup) ? "unknown" : recipientGroup;
            auditLogger.record(new AuditEvent(
                    clock.instant(), "system", "NOTIFICATION_FAILED", "RecipientGroup", group,
                    SOURCE, "reason=recipientGroup/subject/body must all be non-blank"));
            throw new IllegalArgumentException("recipientGroup, subject, and body are all required");
        }

        List<String> recipients = recipientsProperties.getGroups().get(recipientGroup);
        if (recipients == null || recipients.isEmpty()) {
            auditLogger.record(new AuditEvent(
                    clock.instant(), "system", "NOTIFICATION_FAILED", "RecipientGroup", recipientGroup,
                    SOURCE, "reason=no recipients configured for group subject=" + subject));
            throw new UnknownRecipientGroupException(recipientGroup);
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipients.toArray(new String[0]));
        message.setSubject(subject);
        message.setText(body);

        Instant sentAt;
        try {
            mailSender.send(message);
            sentAt = clock.instant();
        } catch (MailException e) {
            auditLogger.record(new AuditEvent(
                    clock.instant(), "system", "NOTIFICATION_FAILED", "RecipientGroup", recipientGroup,
                    SOURCE, "subject=" + subject + " error=" + e.getMessage()));
            throw new NotificationDeliveryException("Failed to send notification to group: " + recipientGroup, e);
        }

        auditLogger.record(new AuditEvent(
                sentAt, "system", "NOTIFICATION_SENT", "RecipientGroup", recipientGroup,
                SOURCE, "subject=" + subject + " recipients=" + recipients.size()));

        return new NotificationResult(recipientGroup, recipients, sentAt);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
