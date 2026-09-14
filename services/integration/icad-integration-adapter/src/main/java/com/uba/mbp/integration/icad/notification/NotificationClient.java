package com.uba.mbp.integration.icad.notification;

/**
 * The seam onto shared-platform's notification-service (integration spec User
 * Story 12; ADR-0019). {@code alertOperations} pages the internal ops group;
 * {@code escalate} (ADR-0020) notifies a business-stakeholder recipient group
 * directly — a distinct concern this adapter alone needs today (an overdue
 * ICAD clearance is a business-stakeholder concern, not an ops alert).
 */
public interface NotificationClient {
    void alertOperations(String subject, String detail);

    void escalate(String recipientGroup, String subject, String detail);
}
