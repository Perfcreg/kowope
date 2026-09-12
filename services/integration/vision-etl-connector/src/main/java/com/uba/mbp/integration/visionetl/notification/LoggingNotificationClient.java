package com.uba.mbp.integration.visionetl.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingNotificationClient implements NotificationClient {

    private static final Logger log = LoggerFactory.getLogger("OPERATIONS_ALERT");

    @Override
    public void alertOperations(String subject, String detail) {
        log.error("[{}] {}", subject, detail);
    }
}
