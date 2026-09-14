package com.uba.mbp.integration.icad.notification;

import com.uba.mbp.integration.icad.config.NotificationServiceProperties;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * The real seam onto shared-platform's notification-service (ADR-0019),
 * replacing {@code LoggingNotificationClient} now that it exists — same
 * Camel {@link ProducerTemplate} pattern this adapter already uses for ICAD
 * (see {@code IcadClient}), per ADR-0011. A failed call here falls back to
 * the same local ERROR log the old stand-in always did, never rethrows: an
 * alert that itself fails to send must not become a new failure on top of
 * whatever it was reporting.
 */
@Component
public class HttpNotificationClient implements NotificationClient {

    // Two distinct log categories, not one shared "OPERATIONS_ALERT" logger
    // (Standards finding, 2026-09-14): an ops-page and a business-stakeholder
    // escalation failure are different signals a log-filter needs to tell
    // apart without parsing message text.
    private static final Logger opsLog = LoggerFactory.getLogger("OPERATIONS_ALERT");
    private static final Logger escalationLog = LoggerFactory.getLogger("ESCALATION_ALERT");
    private static final String RECIPIENT_GROUP = "OPERATIONS";

    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HttpNotificationClient(ProducerTemplate producerTemplate, ObjectMapper objectMapper,
                                   NotificationServiceProperties properties) {
        this.producerTemplate = producerTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = properties.getBaseUrl();
    }

    @Override
    public void alertOperations(String subject, String detail) {
        post(RECIPIENT_GROUP, subject, detail, opsLog);
    }

    @Override
    public void escalate(String recipientGroup, String subject, String detail) {
        post(recipientGroup, subject, detail, escalationLog);
    }

    private void post(String recipientGroup, String subject, String detail, Logger fallbackLog) {
        try {
            String requestJson = objectMapper.writeValueAsString(
                    new NotificationRequestBody(recipientGroup, subject, detail));
            Map<String, Object> headers = Map.of(
                    Exchange.HTTP_METHOD, "POST",
                    Exchange.CONTENT_TYPE, "application/json");
            producerTemplate.requestBodyAndHeaders(baseUrl + "/notifications", requestJson, headers, String.class);
        } catch (RuntimeException e) {
            fallbackLog.error("[{}] {} (notification-service unreachable: {})", subject, detail, e.getMessage());
        }
    }

    private record NotificationRequestBody(String recipientGroup, String subject, String body) {
    }
}
