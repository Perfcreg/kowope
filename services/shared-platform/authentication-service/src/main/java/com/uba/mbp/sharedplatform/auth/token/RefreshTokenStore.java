package com.uba.mbp.sharedplatform.auth.token;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * The Redis-backed refresh-token session (ADR-0003) that realizes RFP §4.3's
 * "session timeout for inactive users": each successful {@code /auth/refresh}
 * slides the TTL forward, so a session only expires after a real gap in
 * activity, not on a fixed wall-clock schedule.
 */
@Component
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redis;
    private final OpaqueTokenGenerator tokenGenerator;
    private final Duration inactivityTimeout;

    public RefreshTokenStore(
            StringRedisTemplate redis,
            OpaqueTokenGenerator tokenGenerator,
            @Value("${auth.session.inactivity-timeout:PT30M}") Duration inactivityTimeout) {
        this.redis = redis;
        this.tokenGenerator = tokenGenerator;
        this.inactivityTimeout = inactivityTimeout;
    }

    /** Issues a new refresh token for {@code username}, starting the sliding-window session. */
    public String issue(String username) {
        String token = tokenGenerator.generate();
        redis.opsForValue().set(KEY_PREFIX + token, username, inactivityTimeout);
        return token;
    }

    /**
     * Looks up the username for a refresh token and slides its TTL forward.
     * Empty if the token doesn't exist or already expired (RFP §4.3 timeout).
     */
    public Optional<String> resolveAndSlide(String token) {
        String key = KEY_PREFIX + token;
        String username = redis.opsForValue().get(key);
        if (username == null) {
            return Optional.empty();
        }
        redis.expire(key, inactivityTimeout);
        return Optional.of(username);
    }

    /** Read-only lookup, used by {@code /auth/step-up} — does not slide the TTL. */
    public Optional<String> resolve(String token) {
        return Optional.ofNullable(redis.opsForValue().get(KEY_PREFIX + token));
    }

    public void revoke(String token) {
        redis.delete(KEY_PREFIX + token);
    }
}
