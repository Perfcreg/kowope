package com.uba.mbp.sharedplatform.auth.service;

/** Thrown when a role change would leave zero {@code ADMIN} users in the whole system (ADR-0018). */
public class LastAdminException extends RuntimeException {
    public LastAdminException(String message) {
        super(message);
    }
}
