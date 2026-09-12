package com.uba.mbp.integration.visionetl.notification;

/** The seam onto shared-platform's notification-service — see write-off-detection-service's identical seam. */
public interface NotificationClient {
    void alertOperations(String subject, String detail);
}
