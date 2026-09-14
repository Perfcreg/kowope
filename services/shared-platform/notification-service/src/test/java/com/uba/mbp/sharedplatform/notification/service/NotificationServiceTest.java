package com.uba.mbp.sharedplatform.notification.service;

import com.uba.mbp.audit.AuditEvent;
import com.uba.mbp.audit.AuditLogger;
import com.uba.mbp.sharedplatform.notification.config.NotificationRecipientsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T10:00:00Z");

    private JavaMailSender mailSender;
    private AuditLogger auditLogger;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        auditLogger = mock(AuditLogger.class);
        NotificationRecipientsProperties properties = new NotificationRecipientsProperties();
        properties.setGroups(Map.of("RECOVERY_TEAM", List.of("recovery-team@uba.local")));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new NotificationService(mailSender, properties, auditLogger, clock, "mbp-notifications@uba.local");
    }

    @Test
    void sendsToTheConfiguredGroupAndAuditsSuccess() {
        NotificationResult result = service.send("RECOVERY_TEAM", "subject", "body");

        assertThat(result.recipientGroup()).isEqualTo("RECOVERY_TEAM");
        assertThat(result.recipients()).containsExactly("recovery-team@uba.local");
        assertThat(result.sentAt()).isEqualTo(NOW);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("recovery-team@uba.local");
        assertThat(captor.getValue().getSubject()).isEqualTo("subject");

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("NOTIFICATION_SENT");
        assertThat(auditCaptor.getValue().affectedRecordId()).isEqualTo("RECOVERY_TEAM");
    }

    @Test
    void rejectsAnUnconfiguredRecipientGroupWithoutSendingButStillAuditsTheFailure() {
        assertThatThrownBy(() -> service.send("NOT_A_REAL_GROUP", "subject", "body"))
                .isInstanceOf(UnknownRecipientGroupException.class);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        // Enterprise-review fix: a rejected notification must still leave an
        // audit trail entry — this was previously a silent, unaudited failure.
        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("NOTIFICATION_FAILED");
        assertThat(auditCaptor.getValue().affectedRecordId()).isEqualTo("NOT_A_REAL_GROUP");
    }

    @Test
    void aBlankSubjectIsRejectedBeforeResolvingRecipientsButStillAuditsTheFailure() {
        assertThatThrownBy(() -> service.send("RECOVERY_TEAM", " ", "body"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("NOTIFICATION_FAILED");
    }

    @Test
    void aMailSendFailureIsAuditedAndWrappedRatherThanSwallowed() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.send("RECOVERY_TEAM", "subject", "body"))
                .isInstanceOf(NotificationDeliveryException.class);

        ArgumentCaptor<AuditEvent> auditCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogger).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().action()).isEqualTo("NOTIFICATION_FAILED");
    }
}
