package com.uba.mbp.sharedplatform.auth.mfa;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.stereotype.Component;

/**
 * RFC 6238 TOTP via a real, verified implementation (dev.samstevens.totp),
 * not hand-rolled HMAC code — same rationale as this repo using Apache POI
 * for xlsx and Camel for EIPs instead of reinventing them. Every account has
 * MFA enabled (RFP §4.3 — mandatory for all users, no opt-out), so this is
 * the only second factor authentication-service supports.
 */
@Component
public class TotpService {

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final DefaultCodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    public TotpService() {
        // One step (30s) of clock drift tolerance either side, standard practice.
        codeVerifier.setAllowedTimePeriodDiscrepancy(1);
    }

    /** A fresh Base32 secret for a new user's MFA enrollment. */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    public boolean isValidCode(String secret, String code) {
        return code != null && !code.isBlank() && codeVerifier.isValidCode(secret, code);
    }

    /** The code currently valid for {@code secret} — used by tests and the dev-user seeder, never by production login logic. */
    public String currentCode(String secret) {
        try {
            long counter = timeProvider.getTime() / 30;
            return codeGenerator.generate(secret, counter);
        } catch (CodeGenerationException e) {
            throw new IllegalStateException("Failed to generate a TOTP code for the given secret", e);
        }
    }
}
