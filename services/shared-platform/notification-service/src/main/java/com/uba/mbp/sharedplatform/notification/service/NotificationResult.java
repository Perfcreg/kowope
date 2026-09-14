package com.uba.mbp.sharedplatform.notification.service;

import java.time.Instant;
import java.util.List;

/** What actually happened when a notification was sent — the REST response body and the Kafka-path's return value alike. */
public record NotificationResult(String recipientGroup, List<String> recipients, Instant sentAt) {
}
