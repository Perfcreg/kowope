package com.uba.mbp.sharedplatform.auth.token;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.uba.mbp.sharedplatform.auth.domain.Role;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Issues RS256-signed access tokens carrying the {@code roles} claim every
 * downstream {@code JwtRoleConverter} already expects (see
 * services/integration/CONTEXT.md and memo-balance's SecurityConfig). Signed
 * with {@link SigningKeys}; verified downstream via the JWKS endpoint, not a
 * shared secret (ADR-0017).
 */
@Component
public class TokenService {

    public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
    /** ADR-0017: the "shorter timeout for sensitive data/transactions" token (RFP §4.3). */
    public static final Duration STEP_UP_TOKEN_TTL = Duration.ofMinutes(5);

    private final SigningKeys signingKeys;
    private final Clock clock;

    public TokenService(SigningKeys signingKeys, Clock clock) {
        this.signingKeys = signingKeys;
        this.clock = clock;
    }

    public String issueAccessToken(String username, Set<Role> roles) {
        return issue(username, roles, ACCESS_TOKEN_TTL, "standard");
    }

    public String issueStepUpToken(String username, Set<Role> roles) {
        return issue(username, roles, STEP_UP_TOKEN_TTL, "sensitive");
    }

    private String issue(String username, Set<Role> roles, Duration ttl, String sessionClass) {
        Instant now = clock.instant();
        List<String> roleNames = roles.stream().map(Role::name).collect(Collectors.toList());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(username)
                .claim("roles", roleNames)
                // OIDC-standard Authentication Methods Reference: MFA is mandatory
                // (RFP §4.3), so every token issued here always carries both factors.
                .claim("amr", List.of("pwd", "otp"))
                // ADR-0017: the claim convention icad-integration-adapter's
                // SecurityConfig javadoc flagged as not existing yet — lets a
                // downstream service require "sensitive" for a high-risk action.
                // Enforcing it is each context's own future work; issuing it here
                // is what unblocks that.
                .claim("session_class", sessionClass)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(signingKeys.signingKey().getKeyID())
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claims);
        try {
            signedJWT.sign(new RSASSASigner(signingKeys.signingKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign the access token", e);
        }
        return signedJWT.serialize();
    }
}
