package com.uba.mbp.sharedplatform.auth.token;

import com.uba.mbp.sharedplatform.auth.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class PendingLoginStoreTest extends AbstractIntegrationTest {

    @Autowired
    private PendingLoginStore pendingLoginStore;

    @Test
    void consumeReturnsTheUsernameOnce() {
        String handle = pendingLoginStore.issue("csm.dev");

        assertThat(pendingLoginStore.consume(handle)).contains("csm.dev");
    }

    @Test
    void consumeIsSingleUse() {
        String handle = pendingLoginStore.issue("csm.dev");
        pendingLoginStore.consume(handle);

        assertThat(pendingLoginStore.consume(handle)).isEmpty();
    }

    @Test
    void unknownHandleResolvesToEmpty() {
        assertThat(pendingLoginStore.consume("not-a-real-handle")).isEmpty();
    }
}
