package com.uba.mbp.integration.writeoffdetection.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Dev/test stand-in (ADR pending shared-platform): logs at ERROR instead of paging anyone. */
@Component
public class LoggingNotificationClient implements NotificationClient {

    private static final Logger log = LoggerFactory.getLogger("OPERATIONS_ALERT");

    @Override
    public void alertOperations(String subject, String detail) {
        log.error("[{}] {}", subject, detail);
    }
}
