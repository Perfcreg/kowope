package com.uba.mbp.sharedplatform.auth.service;

/** Any failure in the login/MFA/refresh/step-up flow — mapped to 401 by {@code GlobalExceptionHandler}. */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
