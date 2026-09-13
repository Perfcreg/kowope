package com.uba.mbp.sharedplatform.auth.token;

import com.uba.mbp.sharedplatform.auth.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Redis (Testcontainers), never mocked — this is exactly the RFP §4.3 session-timeout mechanism. */
class RefreshTokenStoreTest extends AbstractIntegrationTest {

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void issuedTokenResolvesToTheUsername() {
        String token = refreshTokenStore.issue("csm.dev");

        assertThat(refreshTokenStore.resolveAndSlide(token)).contains("csm.dev");
    }

    @Test
    void unknownTokenResolvesToEmpty() {
        assertThat(refreshTokenStore.resolveAndSlide("not-a-real-token")).isEmpty();
    }

    @Test
    void revokedTokenNoLongerResolves() {
        String token = refreshTokenStore.issue("csm.dev");
        refreshTokenStore.revoke(token);

        assertThat(refreshTokenStore.resolveAndSlide(token)).isEmpty();
    }

    @Test
    void resolveAndSlideResetsTheRedisTtlBackToTheFullInactivityWindow() {
        String token = refreshTokenStore.issue("csm.dev");
        String key = "auth:refresh:" + token;
        // Shrink the TTL far below the configured inactivity timeout (30 min default).
        redis.expire(key, Duration.ofSeconds(5));

        assertThat(refreshTokenStore.resolveAndSlide(token)).contains("csm.dev");

        // If resolveAndSlide only read the value without resetting the TTL, this
        // would still show ~5s left, not the full window.
        Long ttlAfterSlide = redis.getExpire(key);
        assertThat(ttlAfterSlide).isGreaterThan(60L);
    }
}
