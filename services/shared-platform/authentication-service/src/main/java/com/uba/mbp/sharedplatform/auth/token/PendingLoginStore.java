package com.uba.mbp.sharedplatform.auth.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * The handle between {@code POST /auth/login} (password verified) and
 * {@code POST /auth/mfa/verify} (TOTP verified) — MFA is mandatory (RFP
 * §4.3), so a password check alone never yields a usable token. Single-use:
 * consuming it deletes it, so a leaked handle can't be replayed after the
 * real login completes.
 */
@Component
public class PendingLoginStore {

    private static final String KEY_PREFIX = "auth:pending-login:";

    private final StringRedisTemplate redis;
    private final OpaqueTokenGenerator tokenGenerator;
    private final Duration ttl;

    public PendingLoginStore(
            StringRedisTemplate redis,
            OpaqueTokenGenerator tokenGenerator,
            @Value("${auth.pending-login.ttl:PT5M}") Duration ttl) {
        this.redis = redis;
        this.tokenGenerator = tokenGenerator;
        this.ttl = ttl;
    }

    public String issue(String username) {
        String handle = tokenGenerator.generate();
        redis.opsForValue().set(KEY_PREFIX + handle, username, ttl);
        return handle;
    }

    /** Consumes the handle: returns the username once, then deletes it (single-use). */
    public Optional<String> consume(String handle) {
        String key = KEY_PREFIX + handle;
        String username = redis.opsForValue().get(key);
        if (username == null) {
            return Optional.empty();
        }
        redis.delete(key);
        return Optional.of(username);
    }
}
