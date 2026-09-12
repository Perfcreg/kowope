package com.uba.mbp.integration.icad.notification;

/**
 * The seam onto shared-platform's notification-service (integration spec User
 * Story 12), which doesn't exist yet. {@link LoggingNotificationClient} stands
 * in for it — swap the implementation, not this interface or its callers,
 * once that context is real.
 */
public interface NotificationClient {
    void alertOperations(String subject, String detail);
}
