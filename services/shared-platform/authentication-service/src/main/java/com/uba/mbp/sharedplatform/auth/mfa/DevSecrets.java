package com.uba.mbp.sharedplatform.auth.mfa;

import java.nio.charset.StandardCharsets;

/**
 * Deterministic, documented dev-only TOTP secrets — one per RFP role, derived
 * from a fixed seed string so the same secret is reproducible across restarts
 * without checking in a hand-typed Base32 literal (see ADR-0017). Documented
 * in services/shared-platform/CONTEXT.md for anyone testing login manually
 * with a real authenticator app. Never used outside {@code DevUserSeeder} and
 * tests — production user MFA secrets come from real enrollment
 * (dev.samstevens.totp's {@code DefaultSecretGenerator}), not this.
 */
public final class DevSecrets {

    private static final String SEED_PREFIX = "mbp-dev-seed-";

    private DevSecrets() {
    }

    public static String forRole(String roleName) {
        return Base32Codec.encode((SEED_PREFIX + roleName).getBytes(StandardCharsets.UTF_8));
    }
}
