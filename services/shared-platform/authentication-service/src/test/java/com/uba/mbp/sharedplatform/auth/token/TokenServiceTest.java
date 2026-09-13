package com.uba.mbp.sharedplatform.auth.token;

import com.uba.mbp.sharedplatform.auth.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves a token issued by {@link TokenService} really verifies via
 * signature — not just that the claims look right. The JWKS-over-HTTP path
 * (what downstream services actually use) is covered separately by a
 * {@code @SpringBootTest} hitting the live {@code /.well-known/jwks.json}
 * endpoint; this test decodes with the same public key directly.
 */
class TokenServiceTest {

    private final SigningKeys signingKeys = new SigningKeys();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-13T10:00:00Z"), ZoneOffset.UTC);
    private final TokenService tokenService = new TokenService(signingKeys, clock);

    private NimbusJwtDecoder realDecoder() throws Exception {
        RSAPublicKey publicKey = (RSAPublicKey) signingKeys.signingKey().toRSAPublicKey();
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    @Test
    void issuesAnAccessTokenThatVerifiesAndCarriesTheRolesClaim() throws Exception {
        Set<Role> roles = EnumSet.of(Role.CREDIT_ADMIN, Role.RECOVERY_TEAM);
        String token = tokenService.issueAccessToken("credit-admin-1", roles);

        Jwt decoded = realDecoder().decode(token);

        assertThat(decoded.getSubject()).isEqualTo("credit-admin-1");
        assertThat(decoded.getClaimAsStringList("roles"))
                .containsExactlyInAnyOrder("CREDIT_ADMIN", "RECOVERY_TEAM");
        assertThat(decoded.getClaimAsString("session_class")).isEqualTo("standard");
        assertThat(decoded.getClaimAsStringList("amr")).isEqualTo(List.of("pwd", "otp"));
        assertThat(decoded.getExpiresAt()).isEqualTo(clock.instant().plus(TokenService.ACCESS_TOKEN_TTL));
    }

    @Test
    void issuesAStepUpTokenMarkedSensitive() throws Exception {
        String token = tokenService.issueStepUpToken("csm-1", EnumSet.of(Role.CSM));

        Jwt decoded = realDecoder().decode(token);

        assertThat(decoded.getClaimAsString("session_class")).isEqualTo("sensitive");
        assertThat(decoded.getExpiresAt()).isEqualTo(clock.instant().plus(TokenService.STEP_UP_TOKEN_TTL));
    }

    @Test
    void aTokenSignedByADifferentKeyIsRejected() throws Exception {
        SigningKeys otherKeys = new SigningKeys();
        TokenService otherTokenService = new TokenService(otherKeys, clock);
        String tokenFromOtherKey = otherTokenService.issueAccessToken("someone", EnumSet.of(Role.CSM));

        assertThatThrownBy(() -> realDecoder().decode(tokenFromOtherKey))
                .isInstanceOf(JwtException.class);
    }
}
