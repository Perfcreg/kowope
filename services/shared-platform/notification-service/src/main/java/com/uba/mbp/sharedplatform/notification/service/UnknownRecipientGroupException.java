package com.uba.mbp.sharedplatform.notification.service;

/** No {@code notification.recipients.groups.<name>} entry is configured for the requested group. */
public class UnknownRecipientGroupException extends RuntimeException {
    public UnknownRecipientGroupException(String recipientGroup) {
        super("No recipients configured for group: " + recipientGroup);
    }
}
