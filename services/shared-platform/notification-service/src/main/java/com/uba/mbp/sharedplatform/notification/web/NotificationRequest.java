package com.uba.mbp.sharedplatform.notification.web;

/** The synchronous notification-request contract any context sends through (spec User Story 7). */
public record NotificationRequest(String recipientGroup, String subject, String body) {
}
