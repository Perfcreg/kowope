package com.uba.mbp.sharedplatform.notification.service;

/** Wraps a real SMTP send failure — surfaced to a synchronous caller as 502, never swallowed silently. */
public class NotificationDeliveryException extends RuntimeException {
    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
