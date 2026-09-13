package com.uba.mbp.sharedplatform.auth.token;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/** Cryptographically strong opaque bearer tokens (refresh tokens, pending-login handles). */
@Component
public class OpaqueTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
