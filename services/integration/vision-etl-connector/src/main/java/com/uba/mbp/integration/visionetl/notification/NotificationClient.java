package com.uba.mbp.integration.visionetl.notification;

/**
 * The seam onto shared-platform's notification-service (integration spec
 * User Story 12), which doesn't exist yet — every adapter in this context
 * carries the same interface/logging-stand-in pair for it independently.
 */
public interface NotificationClient {
    void alertOperations(String subject, String detail);
}
