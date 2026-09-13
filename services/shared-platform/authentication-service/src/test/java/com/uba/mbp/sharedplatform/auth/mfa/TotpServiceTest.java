package com.uba.mbp.sharedplatform.auth.mfa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the real RFC 6238 algorithm end to end — no mocking of the TOTP library. */
class TotpServiceTest {

    private final TotpService totpService = new TotpService();

    @Test
    void generatesAndAcceptsAValidCurrentCode() {
        String secret = totpService.generateSecret();
        String code = totpService.currentCode(secret);

        assertThat(totpService.isValidCode(secret, code)).isTrue();
    }

    @Test
    void rejectsAWrongCode() {
        String secret = totpService.generateSecret();
        String realCode = totpService.currentCode(secret);
        // Guaranteed different 6-digit code from the real one, still well-formed.
        String wrongCode = realCode.equals("000000") ? "111111" : "000000";

        assertThat(totpService.isValidCode(secret, wrongCode)).isFalse();
    }

    @Test
    void rejectsACodeGeneratedForADifferentSecret() {
        String secretA = totpService.generateSecret();
        String secretB = totpService.generateSecret();
        String codeForB = totpService.currentCode(secretB);

        assertThat(totpService.isValidCode(secretA, codeForB)).isFalse();
    }

    @Test
    void rejectsBlankOrNullCodes() {
        String secret = totpService.generateSecret();

        assertThat(totpService.isValidCode(secret, null)).isFalse();
        assertThat(totpService.isValidCode(secret, "")).isFalse();
        assertThat(totpService.isValidCode(secret, "   ")).isFalse();
    }

    @Test
    void devSecretsAreDeterministicAndUsable() {
        String secret = DevSecrets.forRole("CSM");
        String secretAgain = DevSecrets.forRole("CSM");
        assertThat(secret).isEqualTo(secretAgain);

        String code = totpService.currentCode(secret);
        assertThat(totpService.isValidCode(secret, code)).isTrue();
    }
}
