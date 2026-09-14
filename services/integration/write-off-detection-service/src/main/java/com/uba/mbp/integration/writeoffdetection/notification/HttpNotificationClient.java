package com.uba.mbp.integration.writeoffdetection.notification;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * The real seam onto shared-platform's notification-service (ADR-0019),
 * replacing {@code LoggingNotificationClient} now that it exists — same
 * Camel {@link ProducerTemplate} pattern this adapter already uses for
 * Fineract (see {@code FineractHttpClient}), per ADR-0011. A failed call
 * here falls back to the same local ERROR log the old stand-in always did,
 * never rethrows: an alert that itself fails to send must not become a new
 * failure on top of whatever it was reporting.
 */
@Component
public class HttpNotificationClient implements NotificationClient {

    private static final Logger log = LoggerFactory.getLogger("OPERATIONS_ALERT");
    private static final String RECIPIENT_GROUP = "OPERATIONS";

    private final ProducerTemplate producerTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HttpNotificationClient(ProducerTemplate producerTemplate, ObjectMapper objectMapper,
                                   @Value("${notification.service.base-url}") String baseUrl) {
        this.producerTemplate = producerTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public void alertOperations(String subject, String detail) {
        try {
            String requestJson = objectMapper.writeValueAsString(
                    new NotificationRequestBody(RECIPIENT_GROUP, subject, detail));
            Map<String, Object> headers = Map.of(
                    Exchange.HTTP_METHOD, "POST",
                    Exchange.CONTENT_TYPE, "application/json");
            producerTemplate.requestBodyAndHeaders(baseUrl + "/notifications", requestJson, headers, String.class);
        } catch (RuntimeException e) {
            log.error("[{}] {} (notification-service unreachable: {})", subject, detail, e.getMessage());
        }
    }

    private record NotificationRequestBody(String recipientGroup, String subject, String body) {
    }
}
